package io.github.surezzzzzz.sdk.auth.iam.server.service.authorization;

import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.DataAccessOutcome;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataAccessPlan;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataConstraint;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrant;
import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.authorization.request.CreateRoleRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.authorization.response.RoleAuthorizationRuleResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.manifest.response.ApplicationPermissionManifestResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.openrole.request.CreateOpenRoleRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.openrole.request.PutOpenRoleRuleRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.openrole.response.*;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.authorization.IamOpenRoleBindingEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.authorization.IamRoleEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.department.IamDepartmentRoleEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.manifest.IamApplicationPermissionManifestEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.trustedapplication.IamTrustedApplicationEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.exception.IamOpenRoleException;
import io.github.surezzzzzz.sdk.auth.iam.server.model.IamOpenRoleActor;
import io.github.surezzzzzz.sdk.auth.iam.server.model.IamOpenRoleCreationResult;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.authorization.IamOpenRoleBindingRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.authorization.IamRoleAuthorizationRuleRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.department.IamDepartmentRoleRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.trustedapplication.IamTrustedApplicationRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.service.manifest.IamApplicationPermissionManifestService;
import io.github.surezzzzzz.sdk.auth.iam.server.service.trustedapplication.IamTrustedApplicationMutationGuard;
import io.github.surezzzzzz.sdk.auth.iam.server.service.trustedapplication.TrustedApplicationBuiltInResolver;
import io.github.surezzzzzz.sdk.auth.iam.server.support.IamOpenRoleDataAccessPlanHelper;
import io.github.surezzzzzz.sdk.auth.iam.server.support.IamOpenRoleProtocolHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import javax.persistence.EntityManager;
import javax.persistence.LockModeType;
import javax.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 受委托角色机器入口；商业事实属于调用方，不进入 IAM。
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleIamServerComponent
@RequiredArgsConstructor
public class IamOpenRoleService {
    private final IamOpenRoleBindingRepository bindingRepository;
    private final IamRoleAuthorizationRuleRepository ruleRepository;
    private final IamDepartmentRoleRepository departmentRoleRepository;
    private final IamTrustedApplicationRepository applicationRepository;
    private final TrustedApplicationBuiltInResolver builtInResolver;
    private final IamTrustedApplicationMutationGuard applicationMutationGuard;
    private final IamRoleService roleService;
    private final IamRoleAuthorizationRuleService ruleService;
    private final IamApplicationPermissionManifestService manifestService;
    private final IamOpenRoleMutationSupport mutationSupport;
    private final IamOpenDirectoryService directoryService;
    private final PlatformTransactionManager transactionManager;
    private final EntityManager entityManager;

    /**
     * 创建或回读；唯一键失败先回滚，再用独立事务核对胜出记录。
     */
    public IamOpenRoleCreationResult create(CreateOpenRoleRequest input, DataAccessPlan plan) {
        IamOpenRoleActor actor = IamOpenRoleProtocolHelper.requireServiceActor();
        CreateOpenRoleRequest request = IamOpenRoleProtocolHelper.normalize(input);
        IamOpenRoleBindingEntity scope = new IamOpenRoleBindingEntity();
        scope.setApplicationId(request.getApplicationId());
        scope.setRootDepartmentId(request.getRootDepartmentId());
        IamOpenRoleDataAccessPlanHelper.require(plan, IamOpenRoleDataAccessPlanHelper.dimensions(scope, false, null));
        String digest = IamOpenRoleProtocolHelper.digest(request);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        try {
            return transaction.execute(status -> {
                IamOpenRoleBindingEntity existing = findCreation(actor, request.getExternalId());
                if (existing != null) return replay(existing, actor, digest);
                applicationMutationGuard.requireMutable(request.getApplicationId());
                requireTargetApplication(request.getApplicationId(), true);
                manifestService.requireManifest(request.getApplicationId());
                directoryService.lockActiveRoot(request.getRootDepartmentId());
                String openRoleId = UUID.randomUUID().toString();
                IamOpenRoleBindingEntity binding = new IamOpenRoleBindingEntity();
                binding.setOpenRoleId(openRoleId);
                binding.setExternalId(request.getExternalId());
                binding.setOwnerSourceId(actor.getSourceId());
                binding.setOwnerSubjectType(actor.getSubjectType());
                binding.setOwnerSubjectId(actor.getSubjectId());
                binding.setApplicationId(request.getApplicationId());
                binding.setRootDepartmentId(request.getRootDepartmentId());
                binding.setCreationDigest(digest);
                binding.setRevision(SimpleIamServerConstant.OPEN_ROLE_INITIAL_REVISION);
                binding.setState(SimpleIamServerConstant.OPEN_ROLE_STATE_ACTIVE);
                binding.setCreatedAt(Instant.now());
                binding.setUpdatedAt(binding.getCreatedAt());
                CreateRoleRequest roleRequest = new CreateRoleRequest();
                roleRequest.setCode(IamOpenRoleProtocolHelper.roleCode(openRoleId));
                roleRequest.setName(request.getName());
                roleRequest.setDescription(request.getDescription());
                IamRoleEntity role = roleService.createRole(roleRequest, binding, actor);
                binding.setRoleId(role.getId());
                bindingRepository.saveAndFlush(binding);
                log.debug("委托角色创建提交准备：requestId={}, openRoleId={}, revision={}", actor.getRequestId(), openRoleId, binding.getRevision());
                return new IamOpenRoleCreationResult(toResponse(binding, role), true);
            });
        } catch (DataIntegrityViolationException conflict) {
            return transaction.execute(status -> {
                IamOpenRoleBindingEntity winner = findCreation(actor, request.getExternalId());
                if (winner == null) throw new IamOpenRoleException(ErrorCode.OPEN_ROLE_CONFLICT);
                return replay(winner, actor, digest);
            });
        }
    }

    /**
     * 主体及完整 DATA 条件进入列表查询和 count，不先取全库再裁剪。
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public OpenRolePageResponse<OpenRoleResponse> list(DataAccessPlan plan, Long applicationId,
                                                       Long rootDepartmentId, String externalId, int page, int size) {
        IamOpenRoleActor actor = IamOpenRoleProtocolHelper.requireServiceActor();
        validatePage(page, size);
        if (applicationId != null) IamOpenRoleProtocolHelper.positive(applicationId);
        if (rootDepartmentId != null) IamOpenRoleProtocolHelper.positive(rootDepartmentId);
        if (externalId != null) externalId = IamOpenRoleProtocolHelper.uuid(externalId);
        Page<IamOpenRoleBindingEntity> bindings = bindingRepository.findAll(
                IamOpenRoleDataAccessPlanHelper.roleSpecification(actor, plan, applicationId, rootDepartmentId, externalId),
                PageRequest.of(page - 1, size, Sort.by(SimpleIamServerConstant.DATA_RESOURCE_DIMENSION_OPEN_ROLE_ID)));
        List<OpenRoleResponse> results = new ArrayList<>();
        for (IamOpenRoleBindingEntity binding : bindings.getContent()) {
            results.add(responseWithReadLock(binding, actor));
        }
        return new OpenRolePageResponse<>(results, bindings.getTotalElements(), page, size);
    }

    /**
     * 在共享行锁下读取内容及同一版本；墓碑仅返回固定标识和范围。
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public OpenRoleResponse get(String openRoleId, DataAccessPlan plan) {
        IamOpenRoleActor actor = IamOpenRoleProtocolHelper.requireServiceActor();
        IamOpenRoleDataAccessPlanHelper.validate(plan, SimpleIamServerConstant.DATA_RESOURCE_DIMENSION_APPLICATION_ID,
                SimpleIamServerConstant.DATA_RESOURCE_DIMENSION_ROOT_DEPARTMENT_ID);
        IamOpenRoleBindingEntity binding = owned(openRoleId, actor);
        IamOpenRoleDataAccessPlanHelper.require(plan, IamOpenRoleDataAccessPlanHelper.dimensions(binding, false, null));
        return responseWithReadLock(binding, actor);
    }

    /**
     * 读取固定应用规则；无规则统一 404。
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public OpenRoleRuleResponse getRule(String openRoleId, Long applicationId, DataAccessPlan plan) {
        IamOpenRoleActor actor = IamOpenRoleProtocolHelper.requireServiceActor();
        IamOpenRoleBindingEntity binding = ruleBinding(openRoleId, applicationId, actor, plan, null, false);
        RoleAuthorizationRuleResponse rule = ruleService.getRoleAuthorizationRule(binding.getRoleId(), applicationId);
        if (rule == null) throw new IamOpenRoleException(ErrorCode.OPEN_ROLE_NOT_FOUND);
        return ruleResponse(binding, rule);
    }

    /**
     * 清单和角色版本均通过当前数据库核对，缓存不参与治理写判定。
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public OpenRoleRuleResponse putRule(String openRoleId, Long applicationId, String ifMatch,
                                        PutOpenRoleRuleRequest request, DataAccessPlan plan) {
        IamOpenRoleActor actor = IamOpenRoleProtocolHelper.requireServiceActor();
        IamOpenRoleBindingEntity binding = ruleBinding(openRoleId, applicationId, actor, plan, ifMatch, true);
        if (request == null || request.getManifestVersion() == null || request.getManifestVersion() <= 0L
                || request.getManifestDigest() == null || !request.getManifestDigest().matches(SimpleIamServerConstant.OPEN_ROLE_DIGEST_PATTERN)) {
            throw new IamOpenRoleException(ErrorCode.OPEN_ROLE_INVALID);
        }
        IamApplicationPermissionManifestEntity manifest = manifestService.requireManifest(applicationId);
        entityManager.refresh(manifest, LockModeType.PESSIMISTIC_READ);
        if (!manifest.getManifestVersion().equals(request.getManifestVersion())
                || !manifest.getManifestDigest().equalsIgnoreCase(request.getManifestDigest())) {
            throw new IamOpenRoleException(ErrorCode.OPEN_ROLE_CONFLICT);
        }
        RoleAuthorizationRuleResponse rule = ruleService.putRoleAuthorizationRule(binding.getRoleId(), applicationId,
                normalizeCodes(request.getPagePermissions()), normalizeCodes(request.getApiPermissions()), request.getDataGrantTemplate(), actor);
        return ruleResponse(binding, rule);
    }

    /**
     * 无规则也重新鉴权与检查条件版本，不以幂等为理由绕过门禁。
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public long deleteRule(String openRoleId, Long applicationId, String ifMatch, DataAccessPlan plan) {
        IamOpenRoleActor actor = IamOpenRoleProtocolHelper.requireServiceActor();
        IamOpenRoleBindingEntity binding = ruleBinding(openRoleId, applicationId, actor, plan, ifMatch, true);
        ruleService.deleteRoleAuthorizationRule(binding.getRoleId(), applicationId, actor);
        return binding.getRevision();
    }

    /**
     * 部门关系查询与 count 都以完整授权项过滤，迁出后不返回部门展示资料。
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public OpenDepartmentRolePageResponse listDepartments(
            String openRoleId, DataAccessPlan plan, int page, int size) {
        IamOpenRoleActor actor = IamOpenRoleProtocolHelper.requireServiceActor();
        validatePage(page, size);
        IamOpenRoleDataAccessPlanHelper.validate(plan, SimpleIamServerConstant.DATA_RESOURCE_DIMENSION_APPLICATION_ID,
                SimpleIamServerConstant.DATA_RESOURCE_DIMENSION_ROOT_DEPARTMENT_ID,
                SimpleIamServerConstant.DATA_RESOURCE_DIMENSION_OPEN_ROLE_ID, SimpleIamServerConstant.DATA_RESOURCE_DIMENSION_DEPARTMENT_ID);
        IamOpenRoleBindingEntity binding = owned(openRoleId, actor);
        readLock(binding, actor);
        mutationSupport.requireActive(binding);
        requireBoundRole(binding, LockModeType.PESSIMISTIC_READ);
        Page<IamDepartmentRoleEntity> relations = departmentRoleRepository.findAll(departmentSpecification(binding, plan),
                PageRequest.of(page - 1, size, Sort.by(SimpleIamServerConstant.DATA_RESOURCE_DIMENSION_DEPARTMENT_ID)));
        List<OpenDepartmentRoleResponse> response = relations.getContent().stream().map(relation ->
                        new OpenDepartmentRoleResponse(relation.getDepartmentId(),
                                directoryService.isInRoot(binding.getRootDepartmentId(), relation.getDepartmentId()), relation.getCreatedAt()))
                .collect(Collectors.toList());
        return new OpenDepartmentRolePageResponse(binding.getRevision(), response, relations.getTotalElements(), page, size);
    }

    /**
     * 新增挂载用当前祖先链取锁；撤销仅收缩本角色关系，允许迁出或消失。
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public long changeDepartment(String openRoleId, Long departmentId, String ifMatch, DataAccessPlan plan, boolean assign) {
        IamOpenRoleActor actor = IamOpenRoleProtocolHelper.requireServiceActor();
        IamOpenRoleProtocolHelper.positive(departmentId);
        IamOpenRoleDataAccessPlanHelper.validate(plan, SimpleIamServerConstant.DATA_RESOURCE_DIMENSION_APPLICATION_ID,
                SimpleIamServerConstant.DATA_RESOURCE_DIMENSION_ROOT_DEPARTMENT_ID,
                SimpleIamServerConstant.DATA_RESOURCE_DIMENSION_OPEN_ROLE_ID, SimpleIamServerConstant.DATA_RESOURCE_DIMENSION_DEPARTMENT_ID);
        IamOpenRoleBindingEntity binding = owned(openRoleId, actor);
        IamOpenRoleDataAccessPlanHelper.require(plan, IamOpenRoleDataAccessPlanHelper.dimensions(binding, true, departmentId));
        binding = mutationSupport.begin(binding.getRoleId());
        IamOpenRoleProtocolHelper.requireIfMatch(ifMatch, binding);
        if (assign) roleService.assignDepartmentRole(departmentId, binding.getRoleId(), actor);
        else roleService.revokeDepartmentRole(departmentId, binding.getRoleId(), actor);
        return binding.getRevision();
    }

    /**
     * 最小目标应用只读；停用事实可读，内置约束不可被 all=true 绕过。
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public OpenTargetApplicationResponse getApplication(Long applicationId, DataAccessPlan plan) {
        IamOpenRoleProtocolHelper.requireServiceActor();
        requireApplicationPlan(applicationId, plan);
        IamTrustedApplicationEntity app = requireTargetApplication(applicationId, false);
        return new OpenTargetApplicationResponse(app.getId(), app.getApplicationCode(), app.getApplicationName(), app.getStatus(), false);
    }

    /**
     * 权限清单读取仍重新鉴权，不引入 latest 内容缓存。
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public ApplicationPermissionManifestResponse getManifest(Long applicationId, DataAccessPlan plan) {
        IamOpenRoleProtocolHelper.requireServiceActor();
        requireApplicationPlan(applicationId, plan);
        requireTargetApplication(applicationId, false);
        return manifestService.getManifest(applicationId);
    }

    private IamOpenRoleBindingEntity ruleBinding(String id, Long applicationId, IamOpenRoleActor actor,
                                                 DataAccessPlan plan, String ifMatch, boolean write) {
        IamOpenRoleProtocolHelper.positive(applicationId);
        IamOpenRoleDataAccessPlanHelper.validate(plan, SimpleIamServerConstant.DATA_RESOURCE_DIMENSION_APPLICATION_ID,
                SimpleIamServerConstant.DATA_RESOURCE_DIMENSION_ROOT_DEPARTMENT_ID, SimpleIamServerConstant.DATA_RESOURCE_DIMENSION_OPEN_ROLE_ID);
        IamOpenRoleBindingEntity binding = owned(id, actor);
        mutationSupport.requireApplication(binding, applicationId);
        IamOpenRoleDataAccessPlanHelper.require(plan, IamOpenRoleDataAccessPlanHelper.dimensions(binding, true, null));
        if (write) {
            binding = mutationSupport.begin(binding.getRoleId());
            requireTargetApplication(binding.getApplicationId(), false);
            IamOpenRoleProtocolHelper.requireIfMatch(ifMatch, binding);
        } else {
            readLock(binding, actor);
            mutationSupport.requireActive(binding);
            requireBoundRole(binding, LockModeType.PESSIMISTIC_READ);
        }
        return binding;
    }

    private IamOpenRoleBindingEntity owned(String id, IamOpenRoleActor actor) {
        IamOpenRoleBindingEntity binding = bindingRepository.findById(IamOpenRoleProtocolHelper.uuid(id))
                .orElseThrow(() -> new IamOpenRoleException(ErrorCode.OPEN_ROLE_NOT_FOUND));
        if (!IamOpenRoleProtocolHelper.isOwner(binding, actor))
            throw new IamOpenRoleException(ErrorCode.OPEN_ROLE_NOT_FOUND);
        return binding;
    }

    private void readLock(IamOpenRoleBindingEntity binding, IamOpenRoleActor actor) {
        entityManager.refresh(binding, LockModeType.PESSIMISTIC_READ);
        if (!IamOpenRoleProtocolHelper.isOwner(binding, actor))
            throw new IamOpenRoleException(ErrorCode.OPEN_ROLE_NOT_FOUND);
        if (binding.getRevision() == null || binding.getRevision() < SimpleIamServerConstant.OPEN_ROLE_INITIAL_REVISION) {
            throw new IamOpenRoleException(ErrorCode.OPEN_ROLE_CONFLICT);
        }
    }

    private OpenRoleResponse responseWithReadLock(IamOpenRoleBindingEntity binding, IamOpenRoleActor actor) {
        readLock(binding, actor);
        if (SimpleIamServerConstant.OPEN_ROLE_STATE_DELETED.equals(binding.getState()))
            return toResponse(binding, null);
        mutationSupport.requireActive(binding);
        return toResponse(binding, requireBoundRole(binding, LockModeType.PESSIMISTIC_READ));
    }

    private IamRoleEntity requireBoundRole(IamOpenRoleBindingEntity binding, LockModeType lock) {
        IamRoleEntity role = entityManager.find(IamRoleEntity.class, binding.getRoleId(), lock);
        if (role == null) throw new IamOpenRoleException(ErrorCode.OPEN_ROLE_CONFLICT);
        entityManager.refresh(role, lock);
        if (!IamOpenRoleProtocolHelper.roleCode(binding.getOpenRoleId()).equals(role.getCode())
                || !Integer.valueOf(SimpleIamServerConstant.STATUS_INACTIVE).equals(role.getBuiltIn())) {
            throw new IamOpenRoleException(ErrorCode.OPEN_ROLE_CONFLICT);
        }
        return role;
    }

    private IamOpenRoleCreationResult replay(IamOpenRoleBindingEntity binding, IamOpenRoleActor actor, String digest) {
        readLock(binding, actor);
        if (!digest.equals(binding.getCreationDigest()) || !SimpleIamServerConstant.OPEN_ROLE_STATE_ACTIVE.equals(binding.getState())) {
            throw new IamOpenRoleException(ErrorCode.OPEN_ROLE_CONFLICT);
        }
        log.debug("委托角色幂等回读：requestId={}, openRoleId={}, revision={}", actor.getRequestId(), binding.getOpenRoleId(), binding.getRevision());
        return new IamOpenRoleCreationResult(toResponse(binding, requireBoundRole(binding, LockModeType.PESSIMISTIC_READ)), false);
    }

    private IamOpenRoleBindingEntity findCreation(IamOpenRoleActor actor, String externalId) {
        return bindingRepository.findByOwnerSourceIdAndOwnerSubjectTypeAndOwnerSubjectIdAndExternalId(
                actor.getSourceId(), actor.getSubjectType(), actor.getSubjectId(), externalId).orElse(null);
    }

    private OpenRoleResponse toResponse(IamOpenRoleBindingEntity binding, IamRoleEntity role) {
        OpenRoleResponse.OpenRoleResponseBuilder response = OpenRoleResponse.builder().openRoleId(binding.getOpenRoleId())
                .externalId(binding.getExternalId()).applicationId(binding.getApplicationId()).rootDepartmentId(binding.getRootDepartmentId())
                .revision(binding.getRevision()).state(binding.getState());
        if (role != null) response.code(role.getCode()).name(role.getName()).description(role.getDescription())
                .rulePresent(ruleRepository.findByRoleIdAndApplicationId(binding.getRoleId(), binding.getApplicationId()).isPresent())
                .departmentBindingCount(departmentRoleRepository.countByRoleId(binding.getRoleId()));
        return response.build();
    }

    private OpenRoleRuleResponse ruleResponse(IamOpenRoleBindingEntity binding, RoleAuthorizationRuleResponse rule) {
        return OpenRoleRuleResponse.builder().openRoleId(binding.getOpenRoleId()).applicationId(binding.getApplicationId())
                .revision(binding.getRevision()).pagePermissions(rule.getPagePermissions()).apiPermissions(rule.getApiPermissions())
                .dataGrantTemplate(rule.getDataGrantTemplate()).build();
    }

    private IamTrustedApplicationEntity requireTargetApplication(Long applicationId, boolean active) {
        IamTrustedApplicationEntity app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new IamOpenRoleException(ErrorCode.OPEN_ROLE_NOT_FOUND));
        if (active) entityManager.refresh(app, LockModeType.PESSIMISTIC_WRITE);
        if (builtInResolver.isBuiltIn(app) || (active && !Integer.valueOf(SimpleIamServerConstant.STATUS_ACTIVE).equals(app.getStatus()))) {
            throw new IamOpenRoleException(ErrorCode.OPEN_ROLE_CONFLICT);
        }
        return app;
    }

    private void requireApplicationPlan(Long applicationId, DataAccessPlan plan) {
        IamOpenRoleProtocolHelper.positive(applicationId);
        IamOpenRoleDataAccessPlanHelper.require(plan, Collections.singletonMap(
                SimpleIamServerConstant.DATA_RESOURCE_DIMENSION_APPLICATION_ID, applicationId.toString()));
    }

    private List<String> normalizeCodes(List<String> codes) {
        if (codes == null || codes.size() > SimpleIamServerConstant.OPEN_ROLE_PERMISSION_BUDGET) {
            throw new IamOpenRoleException(ErrorCode.OPEN_ROLE_INVALID);
        }
        SortedSet<String> normalized = new TreeSet<>();
        for (String code : codes)
            normalized.add(IamOpenRoleProtocolHelper.text(code, SimpleIamServerConstant.OPEN_ROLE_SUBJECT_MAX_LENGTH, true));
        return new ArrayList<>(normalized);
    }

    private void validatePage(int page, int size) {
        if (page < SimpleIamServerConstant.DEFAULT_ADMIN_PAGE || size <= 0 || size > SimpleIamServerConstant.MAX_ADMIN_PAGE_SIZE) {
            throw new IamOpenRoleException(ErrorCode.OPEN_ROLE_INVALID);
        }
    }

    private Specification<IamDepartmentRoleEntity> departmentSpecification(IamOpenRoleBindingEntity binding, DataAccessPlan plan) {
        Map<String, String> fixed = IamOpenRoleDataAccessPlanHelper.dimensions(binding, true, null);
        return (root, query, builder) -> {
            List<Predicate> grants = new ArrayList<>();
            for (DataGrant grant : plan.getGrants()) {
                List<Predicate> conditions = new ArrayList<>();
                for (DataConstraint constraint : grant.getConstraints()) {
                    if (SimpleIamServerConstant.DATA_RESOURCE_DIMENSION_DEPARTMENT_ID.equals(constraint.getDimension())) {
                        List<Long> ids = constraint.getValues().stream().map(Long::valueOf).collect(Collectors.toList());
                        conditions.add(root.get(SimpleIamServerConstant.DATA_RESOURCE_DIMENSION_DEPARTMENT_ID).in(ids));
                    } else {
                        conditions.add(constraint.getValues().contains(fixed.get(constraint.getDimension())) ? builder.conjunction() : builder.disjunction());
                    }
                }
                grants.add(builder.and(conditions.toArray(new Predicate[0])));
            }
            Predicate allowed = plan.getOutcome() == DataAccessOutcome.ALLOW_ALL ? builder.conjunction() : builder.or(grants.toArray(new Predicate[0]));
            return builder.and(builder.equal(root.get(SimpleIamServerConstant.OPEN_ROLE_FIELD_ROLE_ID), binding.getRoleId()), allowed);
        };
    }
}
