package io.github.surezzzzzz.sdk.auth.iam.server.service.authorization;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.OpenRoleOperation;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.openrole.response.OpenRoleBindingResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.authorization.IamOpenRoleBindingEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.authorization.IamRoleEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.trustedapplication.IamTrustedApplicationEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.exception.IamOpenRoleException;
import io.github.surezzzzzz.sdk.auth.iam.server.model.IamOpenRoleActor;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.authorization.IamOpenRoleBindingRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.manifest.IamApplicationPermissionManifestRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.trustedapplication.IamTrustedApplicationRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.user.IamUserRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.service.trustedapplication.IamTrustedApplicationMutationGuard;
import io.github.surezzzzzz.sdk.auth.iam.server.service.trustedapplication.TrustedApplicationBuiltInResolver;
import io.github.surezzzzzz.sdk.auth.iam.server.service.web.auth.IamUserDetails;
import io.github.surezzzzzz.sdk.auth.iam.server.support.IamOpenRoleProtocolHelper;
import io.github.surezzzzzz.sdk.auth.resource.core.model.VerifiedResourceContext;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.LockModeType;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 机器与管理台共享的锁、固定范围、版本及审计元数据，不依赖角色业务服务。
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleIamServerComponent
@RequiredArgsConstructor
public class IamOpenRoleMutationSupport {
    private static final ObjectMapper AUDIT_MAPPER = new ObjectMapper();
    private static final String SQL_APPLICATION_REFERENCE = "SELECT open_role_id FROM iam_open_role_binding "
            + "WHERE application_id = ? AND state = ? LIMIT 1 FOR UPDATE";
    private static final String SQL_ROOT_REFERENCE = "SELECT open_role_id FROM iam_open_role_binding "
            + "WHERE root_department_id = ? AND state = ? LIMIT 1 FOR UPDATE";
    private static final String SQL_ROOT_LOCK = "SELECT id FROM iam_department WHERE id = ? FOR UPDATE";
    private static final String SQL_APPLICATION_LOCK = "SELECT id FROM iam_trusted_application WHERE id = ? FOR UPDATE";
    private final IamOpenRoleBindingRepository bindingRepository;
    private final IamTrustedApplicationMutationGuard applicationMutationGuard;
    private final IamUserRepository userRepository;
    private final IamTrustedApplicationRepository applicationRepository;
    private final IamApplicationPermissionManifestRepository manifestRepository;
    private final TrustedApplicationBuiltInResolver builtInResolver;
    private final IamOpenDirectoryService directoryService;
    private final EntityManager entityManager;
    private final JdbcTemplate jdbcTemplate;

    /**
     * 若角色受委托，固定以应用、委托、角色顺序取得当前数据库写锁。
     * 旧普通角色返回 null，保留原路径语义。
     */
    public IamOpenRoleBindingEntity begin(Long roleId) {
        IamOpenRoleBindingEntity observed = bindingRepository.findByRoleId(roleId).orElse(null);
        if (observed == null) return null;
        applicationMutationGuard.requireMutable(observed.getApplicationId());
        IamTrustedApplicationEntity application = applicationRepository.findById(observed.getApplicationId())
                .orElseThrow(() -> new IamOpenRoleException(ErrorCode.OPEN_ROLE_CONFLICT));
        entityManager.refresh(application, LockModeType.PESSIMISTIC_WRITE);
        if (builtInResolver.isBuiltIn(application)) throw new IamOpenRoleException(ErrorCode.OPEN_ROLE_CONFLICT);
        // 所有治理写均先锁应用与清单，再锁委托及角色，和清单管理入口保持同序。
        manifestRepository.findByApplicationId(application.getId()).ifPresent(manifest ->
                entityManager.refresh(manifest, LockModeType.PESSIMISTIC_WRITE));
        IamOpenRoleBindingEntity current = bindingRepository.findForUpdate(observed.getOpenRoleId())
                .orElseThrow(() -> new IamOpenRoleException(ErrorCode.OPEN_ROLE_CONFLICT));
        entityManager.refresh(current, LockModeType.PESSIMISTIC_WRITE);
        requireActive(current);
        IamRoleEntity role = entityManager.find(IamRoleEntity.class, current.getRoleId(), LockModeType.PESSIMISTIC_WRITE);
        if (role == null) throw new IamOpenRoleException(ErrorCode.OPEN_ROLE_CONFLICT);
        entityManager.refresh(role, LockModeType.PESSIMISTIC_WRITE);
        if (!IamOpenRoleProtocolHelper.roleCode(current.getOpenRoleId()).equals(role.getCode())
                || !Integer.valueOf(SimpleIamServerConstant.STATUS_INACTIVE).equals(role.getBuiltIn())) {
            throw new IamOpenRoleException(ErrorCode.OPEN_ROLE_CONFLICT);
        }
        log.debug("委托角色共同写锁取得：openRoleId={}, revision={}", current.getOpenRoleId(), current.getRevision());
        return current;
    }

    /**
     * 管理入口在同一事务取得共同锁并验证版本；普通角色不新增条件要求。
     */
    public void requireAdminCondition(Long roleId, String ifMatch) {
        IamOpenRoleBindingEntity binding = begin(roleId);
        if (binding != null) IamOpenRoleProtocolHelper.requireIfMatch(ifMatch, binding);
    }

    /**
     * 管理命令的条件校验和既有业务写在同一事务，不在 Controller 提前验版本。
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public <T> T adminMutation(Long roleId, String ifMatch, Supplier<T> command) {
        requireAdminCondition(roleId, ifMatch);
        return command.get();
    }

    /**
     * 当前状态与版本异常一律停止，墓碑不能复活。
     */
    public void requireActive(IamOpenRoleBindingEntity binding) {
        if (!SimpleIamServerConstant.OPEN_ROLE_STATE_ACTIVE.equals(binding.getState())
                || binding.getRevision() == null || binding.getRevision() < SimpleIamServerConstant.OPEN_ROLE_INITIAL_REVISION) {
            throw new IamOpenRoleException(ErrorCode.OPEN_ROLE_CONFLICT);
        }
    }

    /**
     * 固定应用不允许旧规则路径绕过，任何 all=true 也不能更换。
     */
    public void requireApplication(IamOpenRoleBindingEntity binding, Long applicationId) {
        if (binding != null && !binding.getApplicationId().equals(applicationId)) {
            throw new IamOpenRoleException(ErrorCode.OPEN_ROLE_FORBIDDEN);
        }
    }

    /**
     * 先拒绝跨应用请求，再取得任何应用锁，避免错误请求制造反向锁顺序。
     */
    public void requireRoleApplication(Long roleId, Long applicationId) {
        requireApplication(bindingRepository.findByRoleId(roleId).orElse(null), applicationId);
    }

    /**
     * 受委托角色不能直接授给个人或绑定全局权限。
     */
    public void requireUnmanaged(Long roleId) {
        if (bindingRepository.findByRoleId(roleId).isPresent()) {
            throw new IamOpenRoleException(ErrorCode.OPEN_ROLE_FORBIDDEN);
        }
    }

    /**
     * 新增部门挂载必须锁住并验证当前完整祖先链。
     */
    public void requireDepartment(IamOpenRoleBindingEntity binding, Long departmentId) {
        if (binding != null) directoryService.lockActiveChain(binding.getRootDepartmentId(), departmentId);
    }

    /**
     * 实际变化才递增版本；在上限处失败而非回绕。
     */
    public void changed(IamOpenRoleBindingEntity binding) {
        if (binding == null) return;
        if (binding.getRevision() == Long.MAX_VALUE) throw new IamOpenRoleException(ErrorCode.OPEN_ROLE_CONFLICT);
        binding.setRevision(binding.getRevision() + 1L);
        binding.setUpdatedAt(Instant.now());
        bindingRepository.saveAndFlush(binding);
        log.debug("委托角色变更提交准备：openRoleId={}, revision={}, state={}", binding.getOpenRoleId(), binding.getRevision(), binding.getState());
    }

    /**
     * 在删除范围对象的同一事务内核对最新引用，避免旧一致性快照漏检。
     */
    public void requireApplicationUnreferenced(Long applicationId) {
        // 删除中的应用仍须允许查询既有清理任务；这里只锁引用，不附加“可修改”状态要求。
        jdbcTemplate.queryForList(SQL_APPLICATION_LOCK, Long.class, applicationId);
        rejectReference(SQL_APPLICATION_REFERENCE, applicationId);
    }

    /**
     * 锁根后核对最新引用，与创建时的根锁共同保护引用。
     */
    public void requireRootUnreferenced(Long departmentId) {
        jdbcTemplate.queryForList(SQL_ROOT_LOCK, Long.class, departmentId);
        rejectReference(SQL_ROOT_REFERENCE, departmentId);
    }

    /**
     * 管理台只读摘要；普通角色为 null。
     */
    public OpenRoleBindingResponse summary(Long roleId) {
        return bindingRepository.findByRoleId(roleId).map(this::toSummary)
                .orElse(null);
    }

    /**
     * 一次查询补齐角色目录和关系列表的只读摘要；空列表不执行 IN 查询。
     */
    public Map<Long, OpenRoleBindingResponse> summaries(List<Long> roleIds) {
        if (roleIds.isEmpty()) return Collections.emptyMap();
        return bindingRepository.findByRoleIdIn(roleIds).stream()
                .collect(Collectors.toMap(IamOpenRoleBindingEntity::getRoleId, this::toSummary));
    }

    private OpenRoleBindingResponse toSummary(IamOpenRoleBindingEntity binding) {
        return OpenRoleBindingResponse.builder()
                .openRoleId(binding.getOpenRoleId()).applicationId(binding.getApplicationId())
                .rootDepartmentId(binding.getRootDepartmentId()).revision(binding.getRevision()).state(binding.getState()).build();
    }

    /**
     * 管理详情在绑定读锁下读取角色和摘要，阻止版本与内容来自不同状态。
     */
    public void readLock(Long roleId) {
        bindingRepository.findByRoleId(roleId).ifPresent(binding -> {
            entityManager.refresh(binding, LockModeType.PESSIMISTIC_READ);
            requireActive(binding);
            IamRoleEntity role = entityManager.find(IamRoleEntity.class, roleId, LockModeType.PESSIMISTIC_READ);
            if (role == null) throw new IamOpenRoleException(ErrorCode.OPEN_ROLE_CONFLICT);
            entityManager.refresh(role, LockModeType.PESSIMISTIC_READ);
            if (!IamOpenRoleProtocolHelper.roleCode(binding.getOpenRoleId()).equals(role.getCode())
                    || !Integer.valueOf(SimpleIamServerConstant.STATUS_INACTIVE).equals(role.getBuiltIn())) {
                throw new IamOpenRoleException(ErrorCode.OPEN_ROLE_CONFLICT);
            }
        });
    }

    /**
     * 事务线程内快照实际操作者；管理用户取可信登录主体对应的公开 subjectId。
     */
    public IamOpenRoleActor actor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof VerifiedResourceContext) {
            return IamOpenRoleProtocolHelper.requireServiceActor();
        }
        String subjectId = null;
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof IamUserDetails) {
            Long userId = ((IamUserDetails) authentication.getPrincipal()).getUserId();
            subjectId = userRepository.findById(userId).map(user -> user.getSubjectId()).orElse(null);
        }
        return new IamOpenRoleActor(SimpleIamServerConstant.BUILT_IN_APPLICATION_IAM,
                io.github.surezzzzzz.sdk.auth.resource.core.constant.ResourceSubjectType.HUMAN.getCode(),
                subjectId, UUID.randomUUID().toString());
    }

    /**
     * 只有受委托角色需要新增操作者快照，不给旧普通角色调用附加机器身份要求。
     */
    public IamOpenRoleActor actorForRole(Long roleId) {
        return bindingRepository.findByRoleId(roleId).isPresent() ? actor() : null;
    }

    /**
     * 最小 JSON detail 不携带权限、个人展示资料或认证材料。
     */
    public String detail(IamOpenRoleBindingEntity binding, IamOpenRoleActor actor,
                         OpenRoleOperation operation, Long departmentId) {
        if (binding == null) return null;
        AuditDetail detail = AuditDetail.builder().operation(operation.getCode()).requestId(actor.getRequestId())
                .openRoleId(binding.getOpenRoleId()).applicationId(binding.getApplicationId())
                .rootDepartmentId(binding.getRootDepartmentId()).departmentId(departmentId)
                .revision(binding.getRevision()).actorSourceId(actor.getSourceId())
                .actorSubjectType(actor.getSubjectType()).actorSubjectId(actor.getSubjectId()).build();
        try {
            return AUDIT_MAPPER.writeValueAsString(detail);
        } catch (Exception exception) {
            throw new IamOpenRoleException(ErrorCode.OPEN_ROLE_CONFLICT);
        }
    }

    private void rejectReference(String sql, Long id) {
        List<String> references = jdbcTemplate.queryForList(sql, String.class, id, SimpleIamServerConstant.OPEN_ROLE_STATE_ACTIVE);
        if (!references.isEmpty()) throw new IamOpenRoleException(ErrorCode.OPEN_ROLE_CONFLICT);
    }

    /**
     * 复用既有事件自由文本位的结构化最小元数据。
     */
    @Getter
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private static class AuditDetail {
        /**
         * 实际变更类型。
         */
        private final String operation;
        /**
         * 事务内快照的请求关联标识。
         */
        private final String requestId;
        /**
         * 对外稳定角色标识。
         */
        private final String openRoleId;
        /**
         * 固定应用范围。
         */
        private final Long applicationId;
        /**
         * 固定组织根范围。
         */
        private final Long rootDepartmentId;
        /**
         * 部门关系变化的目标；其他操作省略。
         */
        private final Long departmentId;
        /**
         * 此次变更提交的版本。
         */
        private final long revision;
        /**
         * 实际操作者的可信来源。
         */
        private final String actorSourceId;
        /**
         * 实际操作者类型，不冒充角色所有者。
         */
        private final String actorSubjectType;
        /**
         * 实际操作者的公开主体标识。
         */
        private final String actorSubjectId;
    }
}
