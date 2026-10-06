package io.github.surezzzzzz.sdk.auth.iam.server.service.authorization;

import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.*;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.authorization.request.CreateRoleRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.authorization.request.UpdateRoleRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.authorization.response.PermissionSummaryResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.authorization.response.RoleResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.authorization.response.RoleSummaryResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.openrole.response.OpenRoleBindingResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.authorization.*;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.department.IamDepartmentEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.department.IamDepartmentRoleEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.user.IamUserEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.event.AdminActionType;
import io.github.surezzzzzz.sdk.auth.iam.server.event.AdminSubjectType;
import io.github.surezzzzzz.sdk.auth.iam.server.exception.SimpleIamServerException;
import io.github.surezzzzzz.sdk.auth.iam.server.model.IamOpenRoleActor;
import io.github.surezzzzzz.sdk.auth.iam.server.publisher.IamAuditEventPublisher;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.authorization.*;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.department.IamDepartmentRoleRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.user.IamUserRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.service.department.IamDepartmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 角色管理服务
 *
 * <p>角色 / 权限写操作与关系分配均发布管理面审计事件；关系分配以被改变集合的主体
 * 为事件目标（用户↔角色以用户为主体、角色↔权限以角色为主体）。
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleIamServerComponent
@RequiredArgsConstructor
public class IamRoleService {

    private final IamRoleRepository roleRepository;
    private final IamUserRoleRepository userRoleRepository;
    private final IamDepartmentRoleRepository departmentRoleRepository;
    private final IamPermissionRepository permissionRepository;
    private final IamRolePermissionRepository rolePermissionRepository;
    private final IamRoleAuthorizationRuleRepository ruleRepository;
    private final IamUserRepository userRepository;
    private final IamAuditEventPublisher auditEventPublisher;
    private final IamAuthorizationProjectionService projectionService;
    private final IamEffectiveRoleResolver effectiveRoleResolver;
    private final IamDepartmentService departmentService;
    private final IamOpenRoleMutationSupport openRoleSupport;

    /**
     * 给用户分配角色
     */
    @Transactional
    public void assignRole(Long userId, Long roleId) {
        openRoleSupport.requireUnmanaged(roleId);
        List<IamUserRoleEntity> existing = userRoleRepository.findByUserId(userId);
        boolean alreadyAssigned = existing.stream().anyMatch(r -> r.getRoleId().equals(roleId));
        if (alreadyAssigned) {
            return;
        }
        IamUserRoleEntity entity = new IamUserRoleEntity();
        entity.setUserId(userId);
        entity.setRoleId(roleId);
        entity.setCreatedAt(Instant.now());
        userRoleRepository.save(entity);
        bumpPermissionVersion(Collections.singletonList(userId));

        // 触发授权投影更新（场景2：用户角色分配）
        projectionService.onUserRoleAssigned(userId);

        auditEventPublisher.publishAdminAction(AdminActionType.ASSIGNED, AdminSubjectType.USER,
                subjectIdOfUser(userId), subjectNameOfUser(userId), "roleId=" + roleId);
    }

    /**
     * 给用户撤销角色
     *
     * <p>撤销 iam_admin 角色受最后管理员保护：目标用户是最后一个可用管理员时拒绝，
     * 防止全员失去管理入口（兜底恢复需要重启触发 bootstrap 重建，代价过高）。
     */
    @Transactional
    public void revokeRole(Long userId, Long roleId) {
        roleRepository.findById(roleId)
                .filter(role -> SimpleIamServerConstant.BUILT_IN_ROLE_IAM_ADMIN.equals(role.getCode()))
                .ifPresent(role -> assertNotLastActiveAdmin(userId));
        List<IamUserRoleEntity> existing = userRoleRepository.findByUserId(userId);
        boolean assigned = existing.stream().anyMatch(r -> r.getRoleId().equals(roleId));
        userRoleRepository.deleteByUserIdAndRoleId(userId, roleId);
        if (assigned) {
            bumpPermissionVersion(Collections.singletonList(userId));

            // 触发授权投影更新（场景2：用户角色撤销）
            projectionService.onUserRoleAssigned(userId);
        }
        auditEventPublisher.publishAdminAction(AdminActionType.UNASSIGNED, AdminSubjectType.USER,
                subjectIdOfUser(userId), subjectNameOfUser(userId), "roleId=" + roleId);
    }

    /**
     * 删除或禁用用户前的最后管理员保护：目标用户是 iam_admin 角色最后一个可用
     * （active 状态）用户时抛出异常。
     *
     * @param userId 用户 ID
     */
    public void assertNotLastActiveAdmin(Long userId) {
        if (isLastActiveAdminUser(userId)) {
            String username = subjectNameOfUser(userId);
            throw new SimpleIamServerException(ErrorCode.LAST_ADMIN_PROTECTED,
                    String.format(ServerErrorMessage.LAST_ADMIN_PROTECTED, username));
        }
    }

    /**
     * 判定用户是否为 iam_admin 角色最后一个可用（active 状态）用户。
     *
     * <p>有效持有者 = 个人直接绑定 ∪ 所属直属部门挂载了 iam_admin 的用户
     * （{@link IamEffectiveRoleResolver}），覆盖部门继承来源，不再局限于个人直接绑定。
     * 角色不存在（理论仅在 bootstrap 前出现）或用户未持有该角色时返回 false。
     *
     * @param userId 用户 ID
     * @return true 表示删除 / 禁用该用户或撤销其 iam_admin 角色会导致无可用管理员
     */
    public boolean isLastActiveAdminUser(Long userId) {
        IamRoleEntity adminRole = roleRepository.findByCode(SimpleIamServerConstant.BUILT_IN_ROLE_IAM_ADMIN).orElse(null);
        if (adminRole == null) {
            return false;
        }
        Set<Long> holderIds = effectiveRoleResolver.resolveUserIdsByRoleId(adminRole.getId());
        if (!holderIds.contains(userId)) {
            return false;
        }
        List<Long> otherUserIds = holderIds.stream()
                .filter(id -> !id.equals(userId))
                .collect(Collectors.toList());
        if (otherUserIds.isEmpty()) {
            return true;
        }
        return userRepository.findAllById(otherUserIds).stream()
                .noneMatch(user -> Integer.valueOf(SimpleIamServerConstant.STATUS_ACTIVE).equals(user.getStatus()));
    }

    /**
     * 审计事件目标用户名；主体已被并发删除时降级为 null（事件仍须发布，不带语义字段）。
     */
    private String subjectNameOfUser(Long userId) {
        return userRepository.findById(userId).map(IamUserEntity::getUsername).orElse(null);
    }

    /**
     * 审计事件目标用户的公开主体；并发删除后不再以数据库自增 ID 回退。
     */
    private String subjectIdOfUser(Long userId) {
        return userRepository.findById(userId).map(IamUserEntity::getSubjectId).orElse(null);
    }

    /**
     * 审计事件目标角色名；同上降级规则。
     */
    private String subjectNameOfRole(Long roleId) {
        return roleRepository.findById(roleId).map(IamRoleEntity::getName).orElse(null);
    }

    /**
     * 查询用户的所有有效角色（个人直接角色 ∪ 所属直属部门挂载的角色）
     *
     * @param userId 用户 ID
     * @return 按角色编码升序排列的有效角色
     */
    public List<IamRoleEntity> getUserRoles(Long userId) {
        Set<Long> roleIds = effectiveRoleResolver.resolveRoleIds(userId);
        if (roleIds.isEmpty()) {
            return Collections.emptyList();
        }
        return roleRepository.findAllById(roleIds).stream()
                .sorted(Comparator.comparing(IamRoleEntity::getCode))
                .collect(Collectors.toList());
    }

    /**
     * 查询用户的所有权限编码（跨角色聚合）
     *
     * @param userId 用户 ID
     * @return 按权限编码升序排列的有效权限编码
     */
    public List<String> getUserPermissionCodes(Long userId) {
        return getUserPermissions(userId).stream()
                .map(IamPermissionEntity::getCode)
                .collect(Collectors.toList());
    }

    /**
     * 查询用户的所有有效权限（个人直接角色 ∪ 所属直属部门挂载角色，跨角色聚合）
     *
     * @param userId 用户 ID
     * @return 按权限编码升序排列、已去重的有效权限
     */
    public List<IamPermissionEntity> getUserPermissions(Long userId) {
        Set<Long> roleIds = effectiveRoleResolver.resolveRoleIds(userId);
        if (roleIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> permissionIds = roleIds.stream()
                .flatMap(roleId -> rolePermissionRepository.findByRoleId(roleId).stream())
                .map(IamRolePermissionEntity::getPermissionId)
                .distinct()
                .collect(Collectors.toList());
        if (permissionIds.isEmpty()) {
            return Collections.emptyList();
        }
        return permissionRepository.findAllById(permissionIds).stream()
                .sorted(Comparator.comparing(IamPermissionEntity::getCode))
                .collect(Collectors.toList());
    }

    /**
     * 查询用户的所有有效角色摘要，标注每个角色的来源（个人直接 / 部门继承）；
     * 同一角色个人也直接持有时以个人直接为准
     *
     * @param userId 用户 ID
     * @return 按角色编码升序排列的角色摘要
     */
    public List<RoleSummaryResponse> getUserRoleSummaries(Long userId) {
        return getUserRoleSummaries(effectiveRoleResolver.resolveRoleIdsWithSource(userId));
    }

    /**
     * 按已解析的有效角色来源查询角色摘要；供同一请求内需要与权限摘要共享同一份解析结果的调用方使用，
     * 避免重复解析个人角色与部门挂载角色
     *
     * @param roleSources 用户有效角色及其来源，通常来自 {@link IamEffectiveRoleResolver#resolveRoleIdsWithSource}
     * @return 按角色编码升序排列的角色摘要
     */
    public List<RoleSummaryResponse> getUserRoleSummaries(Map<Long, RoleSource> roleSources) {
        if (roleSources.isEmpty()) {
            return Collections.emptyList();
        }
        return roleRepository.findAllById(roleSources.keySet()).stream()
                .sorted(Comparator.comparing(IamRoleEntity::getCode))
                .map(role -> RoleSummaryResponse.from(role, roleSources.get(role.getId())))
                .collect(Collectors.toList());
    }

    /**
     * 查询用户的所有有效权限摘要（跨角色聚合去重），标注每个权限的来源：
     * 只要存在一个个人直接持有的角色授予该权限即标 DIRECT，否则标 DEPARTMENT_INHERITED
     *
     * @param userId 用户 ID
     * @return 按权限编码升序排列的权限摘要
     */
    public List<PermissionSummaryResponse> getUserPermissionSummaries(Long userId) {
        return getUserPermissionSummaries(effectiveRoleResolver.resolveRoleIdsWithSource(userId));
    }

    /**
     * 按已解析的有效角色来源查询权限摘要；供同一请求内需要与角色摘要共享同一份解析结果的调用方使用，
     * 避免重复解析个人角色与部门挂载角色
     *
     * @param roleSources 用户有效角色及其来源，通常来自 {@link IamEffectiveRoleResolver#resolveRoleIdsWithSource}
     * @return 按权限编码升序排列的权限摘要
     */
    public List<PermissionSummaryResponse> getUserPermissionSummaries(Map<Long, RoleSource> roleSources) {
        if (roleSources.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, RoleSource> permissionSources = new LinkedHashMap<>();
        for (Map.Entry<Long, RoleSource> entry : roleSources.entrySet()) {
            for (IamRolePermissionEntity rolePermission : rolePermissionRepository.findByRoleId(entry.getKey())) {
                if (entry.getValue() == RoleSource.DIRECT) {
                    permissionSources.put(rolePermission.getPermissionId(), RoleSource.DIRECT);
                } else {
                    permissionSources.putIfAbsent(rolePermission.getPermissionId(), RoleSource.DEPARTMENT_INHERITED);
                }
            }
        }
        if (permissionSources.isEmpty()) {
            return Collections.emptyList();
        }
        return permissionRepository.findAllById(permissionSources.keySet()).stream()
                .sorted(Comparator.comparing(IamPermissionEntity::getCode))
                .map(permission -> PermissionSummaryResponse.from(permission, permissionSources.get(permission.getId())))
                .collect(Collectors.toList());
    }

    /**
     * 查询所有角色
     */
    public List<IamRoleEntity> getAllRoles() {
        return roleRepository.findAll();
    }

    /**
     * 管理列表统一补齐委托边界，保留原顺序和普通角色的 null 摘要。
     * 列表只用于展示；修改前仍须读取带锁的角色详情取得当前版本。
     */
    @Transactional(readOnly = true)
    public List<RoleResponse> toResponses(List<IamRoleEntity> roles) {
        Map<Long, OpenRoleBindingResponse> summaries = openRoleSupport.summaries(roles.stream()
                .map(IamRoleEntity::getId).collect(Collectors.toList()));
        return roles.stream().map(role -> {
            RoleResponse response = RoleResponse.from(role);
            response.setOpenRoleBinding(summaries.get(role.getId()));
            return response;
        }).collect(Collectors.toList());
    }

    /**
     * 分页响应复用批量摘要，保持原分页元数据。
     */
    @Transactional(readOnly = true)
    public Page<RoleResponse> toResponses(Page<IamRoleEntity> roles) {
        return new PageImpl<>(toResponses(roles.getContent()), roles.getPageable(), roles.getTotalElements());
    }

    /**
     * 管理台分页查询角色
     */
    public Page<IamRoleEntity> listRoles(String keyword, int page, int size) {
        int safePage = Math.max(page, 1) - 1;
        int safeSize = Math.min(Math.max(size, 1), SimpleIamServerConstant.MAX_ADMIN_PAGE_SIZE);
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        String normalizedKeyword = keyword == null || keyword.trim().isEmpty() ? null : keyword.trim();
        return roleRepository.searchForConsole(normalizedKeyword, pageable);
    }

    /**
     * 管理台分页查询角色成员（删除角色 / 调整角色权限的影响面可视）
     */
    public Page<IamUserEntity> listRoleMembers(Long roleId, int page, int size) {
        getById(roleId);
        int safePage = Math.max(page, 1) - 1;
        int safeSize = Math.min(Math.max(size, 1), SimpleIamServerConstant.MAX_ADMIN_PAGE_SIZE);
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<IamUserRoleEntity> bindings = userRoleRepository.findByRoleId(roleId, pageable);
        List<Long> userIds = bindings.getContent().stream()
                .map(IamUserRoleEntity::getUserId).collect(Collectors.toList());
        if (userIds.isEmpty()) {
            return Page.empty(pageable);
        }
        Map<Long, IamUserEntity> usersById = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(IamUserEntity::getId, Function.identity()));
        List<IamUserEntity> members = userIds.stream().map(usersById::get)
                .filter(Objects::nonNull).collect(Collectors.toList());
        return new PageImpl<>(members, pageable, bindings.getTotalElements());
    }

    /**
     * 创建角色
     */
    @Transactional
    public IamRoleEntity createRole(CreateRoleRequest request) {
        return createRole(request, null, null);
    }

    /**
     * 创建委托角色时复用角色业务，并在同一事务事件中携带真实操作元数据。
     */
    @Transactional
    public IamRoleEntity createRole(CreateRoleRequest request, IamOpenRoleBindingEntity binding, IamOpenRoleActor actor) {
        String code = normalizeRequired(request.getCode(), "角色编码不能为空");
        if (roleRepository.existsByCode(code)) {
            throw new SimpleIamServerException("角色编码已存在：" + code);
        }
        IamRoleEntity role = new IamRoleEntity();
        role.setCode(code);
        role.setName(normalizeRequired(request.getName(), "角色名称不能为空"));
        role.setDescription(normalizeOptional(request.getDescription()));
        role.setBuiltIn(SimpleIamServerConstant.STATUS_INACTIVE);
        role.setCreatedAt(Instant.now());
        role.setUpdatedAt(Instant.now());
        IamRoleEntity saved = roleRepository.save(role);
        log.info("角色创建成功：code={}, id={}", saved.getCode(), saved.getId());
        auditEventPublisher.publishAdminAction(AdminActionType.CREATED, AdminSubjectType.ROLE,
                String.valueOf(saved.getId()), binding == null ? saved.getCode() : null,
                binding == null ? null : openRoleSupport.detail(binding, actor, OpenRoleOperation.CREATE_ROLE, null));
        return saved;
    }

    /**
     * 更新角色
     */
    @Transactional
    public IamRoleEntity updateRole(Long roleId, UpdateRoleRequest request) {
        IamOpenRoleBindingEntity binding = openRoleSupport.begin(roleId);
        IamOpenRoleActor actor = binding == null ? null : openRoleSupport.actor();
        IamRoleEntity role = getById(roleId);
        String name = binding == null ? normalizeRequired(request.getName(), "角色名称不能为空")
                : io.github.surezzzzzz.sdk.auth.iam.server.support.IamOpenRoleProtocolHelper.text(
                request.getName(), SimpleIamServerConstant.OPEN_ROLE_NAME_MAX_LENGTH, true);
        String description = binding == null ? normalizeOptional(request.getDescription())
                : io.github.surezzzzzz.sdk.auth.iam.server.support.IamOpenRoleProtocolHelper.text(
                request.getDescription(), SimpleIamServerConstant.OPEN_ROLE_DESCRIPTION_MAX_LENGTH, false);
        if (binding != null && Objects.equals(name, role.getName()) && Objects.equals(description, role.getDescription()))
            return role;
        role.setName(name);
        role.setDescription(description);
        role.setUpdatedAt(Instant.now());
        IamRoleEntity saved = roleRepository.save(role);
        openRoleSupport.changed(binding);
        auditEventPublisher.publishAdminAction(AdminActionType.UPDATED, AdminSubjectType.ROLE,
                String.valueOf(roleId), binding == null ? saved.getCode() : null,
                binding == null ? null : openRoleSupport.detail(binding, actor, OpenRoleOperation.UPDATE_ROLE, null));
        return saved;
    }

    /**
     * 删除角色
     *
     * <p>同步清理个人直接绑定与部门挂载两类关联；受影响用户（含部门继承成员）
     * 权限版本号统一 bump，须在快照采集之后、关联删除之前完成快照。
     */
    @Transactional
    public void deleteRole(Long roleId) {
        IamOpenRoleBindingEntity binding = openRoleSupport.begin(roleId);
        IamOpenRoleActor actor = binding == null ? null : openRoleSupport.actor();
        IamRoleEntity role = getById(roleId);
        if (SimpleIamServerConstant.STATUS_ACTIVE == role.getBuiltIn()) {
            throw new SimpleIamServerException("内置角色不可删除：" + role.getCode());
        }
        List<Long> memberUserIds = new ArrayList<>(effectiveRoleResolver.resolveUserIdsByRoleId(roleId));
        userRoleRepository.findByRoleId(roleId).forEach(userRole -> userRoleRepository.delete(userRole));
        departmentRoleRepository.deleteByRoleId(roleId);
        rolePermissionRepository.deleteByRoleId(roleId);
        ruleRepository.deleteByRoleId(roleId);
        roleRepository.delete(role);
        bumpPermissionVersion(memberUserIds);
        // 触发授权投影更新（场景2：角色删除=全员解绑），须在规则与绑定删除完成后重算
        for (Long memberUserId : memberUserIds) {
            projectionService.onUserRoleAssigned(memberUserId);
        }
        if (binding != null) {
            binding.setState(SimpleIamServerConstant.OPEN_ROLE_STATE_DELETED);
            openRoleSupport.changed(binding);
        }
        log.info("角色删除成功：code={}, id={}", role.getCode(), roleId);
        auditEventPublisher.publishAdminAction(AdminActionType.DELETED, AdminSubjectType.ROLE,
                String.valueOf(roleId), binding == null ? role.getCode() : null,
                binding == null ? null : openRoleSupport.detail(binding, actor, OpenRoleOperation.DELETE_ROLE, null));
    }

    /**
     * 查询部门当前挂载的角色
     */
    public List<IamRoleEntity> getDepartmentRoles(Long departmentId) {
        departmentService.getById(departmentId);
        List<Long> roleIds = departmentRoleRepository.findByDepartmentId(departmentId).stream()
                .map(IamDepartmentRoleEntity::getRoleId).collect(Collectors.toList());
        if (roleIds.isEmpty()) {
            return Collections.emptyList();
        }
        return roleRepository.findAllById(roleIds).stream()
                .sorted(Comparator.comparing(IamRoleEntity::getCode))
                .collect(Collectors.toList());
    }

    /**
     * 反查挂载了该角色的部门（角色详情页“继承来源部门”只读展示）
     */
    public List<IamDepartmentEntity> getRoleDepartments(Long roleId) {
        getById(roleId);
        List<Long> departmentIds = departmentRoleRepository.findByRoleId(roleId).stream()
                .map(IamDepartmentRoleEntity::getDepartmentId)
                .collect(Collectors.toList());
        return departmentService.getByIds(departmentIds);
    }

    /**
     * 给部门挂载角色：部门下全体成员（个人直接角色之外）自动获得该角色，重复挂载幂等。
     */
    @Transactional
    public void assignDepartmentRole(Long departmentId, Long roleId) {
        assignDepartmentRole(departmentId, roleId, openRoleSupport.actorForRole(roleId));
    }

    /**
     * 显式传递机器操作者；管理面沿用可信会话快照。
     */
    @Transactional
    public void assignDepartmentRole(Long departmentId, Long roleId, IamOpenRoleActor suppliedActor) {
        IamOpenRoleBindingEntity binding = openRoleSupport.begin(roleId);
        IamOpenRoleActor actor = suppliedActor;
        openRoleSupport.requireDepartment(binding, departmentId);
        departmentService.getById(departmentId);
        getById(roleId);
        if (departmentRoleRepository.existsByDepartmentIdAndRoleId(departmentId, roleId)) {
            return;
        }
        IamDepartmentRoleEntity entity = new IamDepartmentRoleEntity();
        entity.setDepartmentId(departmentId);
        entity.setRoleId(roleId);
        entity.setCreatedAt(Instant.now());
        departmentRoleRepository.save(entity);
        bumpAndRecomputeDepartmentMembers(departmentId);
        openRoleSupport.changed(binding);
        auditEventPublisher.publishAdminAction(AdminActionType.ASSIGNED, AdminSubjectType.DEPARTMENT,
                String.valueOf(departmentId), binding == null ? departmentService.getDepartmentName(departmentId) : null,
                binding == null ? "roleId=" + roleId : openRoleSupport.detail(binding, actor, OpenRoleOperation.ASSIGN_DEPARTMENT, departmentId));
    }

    /**
     * 撤销部门挂载的角色。
     *
     * <p>{@code iam_admin} 受部门级最后管理员保护：撤销后若系统再无任何 active 状态的
     * 有效管理员（个人直接绑定 ∪ 其余部门挂载），拒绝本次撤销。
     */
    @Transactional
    public void revokeDepartmentRole(Long departmentId, Long roleId) {
        revokeDepartmentRole(departmentId, roleId, openRoleSupport.actorForRole(roleId));
    }

    /**
     * 委托关系迁出或目标消失后仍允许清理，但不允许借清理新增授权。
     */
    @Transactional
    public void revokeDepartmentRole(Long departmentId, Long roleId, IamOpenRoleActor suppliedActor) {
        IamOpenRoleBindingEntity binding = openRoleSupport.begin(roleId);
        IamOpenRoleActor actor = suppliedActor;
        if (binding == null) departmentService.getById(departmentId);
        IamRoleEntity role = getById(roleId);
        if (SimpleIamServerConstant.BUILT_IN_ROLE_IAM_ADMIN.equals(role.getCode())) {
            assertDepartmentRevokeNotLastActiveAdmin(departmentId, role.getId());
        }
        boolean assigned = departmentRoleRepository.existsByDepartmentIdAndRoleId(departmentId, roleId);
        departmentRoleRepository.deleteByDepartmentIdAndRoleId(departmentId, roleId);
        if (assigned) {
            bumpAndRecomputeDepartmentMembers(departmentId);
            openRoleSupport.changed(binding);
        }
        if (binding != null && !assigned) return;
        auditEventPublisher.publishAdminAction(AdminActionType.UNASSIGNED, AdminSubjectType.DEPARTMENT,
                String.valueOf(departmentId), binding == null ? departmentService.getDepartmentName(departmentId) : null,
                binding == null ? "roleId=" + roleId : openRoleSupport.detail(binding, actor, OpenRoleOperation.REVOKE_DEPARTMENT, departmentId));
    }

    /**
     * 部门级最后管理员保护：计算撤销本部门的 iam_admin 挂载后，系统是否仍有其他
     * active 状态的有效管理员（个人直接绑定 ∪ 除本部门外仍挂 iam_admin 的其他部门成员）。
     */
    private void assertDepartmentRevokeNotLastActiveAdmin(Long departmentId, Long adminRoleId) {
        Set<Long> otherHolderIds = new LinkedHashSet<>();
        for (IamUserRoleEntity userRole : userRoleRepository.findByRoleId(adminRoleId)) {
            otherHolderIds.add(userRole.getUserId());
        }
        List<Long> otherDepartmentIds = departmentRoleRepository.findByRoleId(adminRoleId).stream()
                .map(IamDepartmentRoleEntity::getDepartmentId)
                .filter(id -> !id.equals(departmentId))
                .collect(Collectors.toList());
        if (!otherDepartmentIds.isEmpty()) {
            for (IamUserEntity user : userRepository.findByDepartmentIdIn(otherDepartmentIds)) {
                otherHolderIds.add(user.getId());
            }
        }
        boolean hasOtherActiveAdmin = !otherHolderIds.isEmpty() && userRepository.findAllById(otherHolderIds).stream()
                .anyMatch(user -> Integer.valueOf(SimpleIamServerConstant.STATUS_ACTIVE).equals(user.getStatus()));
        if (!hasOtherActiveAdmin) {
            throw new SimpleIamServerException(ErrorCode.LAST_ADMIN_PROTECTED,
                    String.format(ServerErrorMessage.LAST_ADMIN_PROTECTED,
                            "部门「" + departmentService.getDepartmentName(departmentId) + "」的全部成员"));
        }
    }

    /**
     * 转部门前的最后管理员保护：若该用户当前有效角色含 iam_admin，且转入新部门后
     * （新部门未挂 iam_admin 且用户本人无个人直接 iam_admin）该用户将失去管理员身份，
     * 而其当前又是系统唯一有效的 active 管理员，则拒绝本次转部门。
     *
     * @param userId          待转部门的用户 ID
     * @param newDepartmentId 目标部门 ID；{@code null} 表示清空部门
     */
    public void assertDepartmentTransferNotLastActiveAdmin(Long userId, Long newDepartmentId) {
        IamRoleEntity adminRole = roleRepository.findByCode(SimpleIamServerConstant.BUILT_IN_ROLE_IAM_ADMIN).orElse(null);
        if (adminRole == null) {
            return;
        }
        Set<Long> currentRoleIds = effectiveRoleResolver.resolveRoleIds(userId);
        if (!currentRoleIds.contains(adminRole.getId())) {
            return;
        }
        boolean personalDirectAdmin = userRoleRepository.findByUserId(userId).stream()
                .anyMatch(userRole -> userRole.getRoleId().equals(adminRole.getId()));
        boolean newDepartmentGrantsAdmin = newDepartmentId != null
                && departmentRoleRepository.existsByDepartmentIdAndRoleId(newDepartmentId, adminRole.getId());
        if (personalDirectAdmin || newDepartmentGrantsAdmin) {
            return;
        }
        assertNotLastActiveAdmin(userId);
    }

    /**
     * 部门角色挂载 / 撤销后，对该部门当前全部成员统一 bump 权限版本号并重算授权投影。
     */
    private void bumpAndRecomputeDepartmentMembers(Long departmentId) {
        List<Long> memberUserIds = userRepository.findByDepartmentId(departmentId).stream()
                .map(IamUserEntity::getId).collect(Collectors.toList());
        bumpPermissionVersion(memberUserIds);
        for (Long memberUserId : memberUserIds) {
            projectionService.onUserRoleAssigned(memberUserId);
        }
    }

    /**
     * 管理台分页查询权限
     */
    public Page<IamPermissionEntity> listPermissions(String keyword, int page, int size) {
        int safePage = Math.max(page, 1) - 1;
        int safeSize = Math.min(Math.max(size, 1), SimpleIamServerConstant.MAX_ADMIN_PAGE_SIZE);
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        String normalizedKeyword = keyword == null || keyword.trim().isEmpty() ? null : keyword.trim();
        return permissionRepository.searchForConsole(normalizedKeyword, pageable);
    }

    /**
     * 查询所有权限
     */
    public List<IamPermissionEntity> getAllPermissions() {
        return permissionRepository.findAll();
    }

    /**
     * 查询角色权限
     */
    public List<IamPermissionEntity> getRolePermissions(Long roleId) {
        List<Long> permissionIds = rolePermissionRepository.findByRoleId(roleId)
                .stream()
                .map(IamRolePermissionEntity::getPermissionId)
                .collect(Collectors.toList());
        if (permissionIds.isEmpty()) {
            return Collections.emptyList();
        }
        return permissionRepository.findAllById(permissionIds);
    }

    /**
     * 给角色分配权限
     */
    @Transactional
    public void assignPermission(Long roleId, Long permissionId) {
        openRoleSupport.requireUnmanaged(roleId);
        getById(roleId);
        getPermissionById(permissionId);
        boolean alreadyAssigned = rolePermissionRepository.findByRoleId(roleId).stream()
                .anyMatch(rolePermission -> rolePermission.getPermissionId().equals(permissionId));
        if (alreadyAssigned) {
            return;
        }
        IamRolePermissionEntity entity = new IamRolePermissionEntity();
        entity.setRoleId(roleId);
        entity.setPermissionId(permissionId);
        entity.setCreatedAt(Instant.now());
        rolePermissionRepository.save(entity);
        bumpUsersOfRole(roleId);
        auditEventPublisher.publishAdminAction(AdminActionType.ASSIGNED, AdminSubjectType.ROLE,
                String.valueOf(roleId), subjectNameOfRole(roleId), "permissionId=" + permissionId);
    }

    /**
     * 撤销角色权限
     */
    @Transactional
    public void revokePermission(Long roleId, Long permissionId) {
        openRoleSupport.requireUnmanaged(roleId);
        boolean assigned = rolePermissionRepository.findByRoleId(roleId).stream()
                .anyMatch(rolePermission -> rolePermission.getPermissionId().equals(permissionId));
        rolePermissionRepository.deleteByRoleIdAndPermissionId(roleId, permissionId);
        if (assigned) {
            bumpUsersOfRole(roleId);
        }
        auditEventPublisher.publishAdminAction(AdminActionType.UNASSIGNED, AdminSubjectType.ROLE,
                String.valueOf(roleId), subjectNameOfRole(roleId), "permissionId=" + permissionId);
    }

    /**
     * 获取权限（不存在则抛异常）
     */
    public IamPermissionEntity getPermissionById(Long permissionId) {
        return permissionRepository.findById(permissionId)
                .orElseThrow(() -> new SimpleIamServerException(ErrorCode.PERMISSION_DENIED,
                        String.format(ServerErrorMessage.PERMISSION_NOT_FOUND, permissionId)));
    }

    /**
     * 获取角色（不存在则抛异常）
     */
    public IamRoleEntity getById(Long roleId) {
        return roleRepository.findById(roleId)
                .orElseThrow(() -> new SimpleIamServerException("角色不存在：" + roleId));
    }

    /**
     * 管理详情返回同事务角色内容和委托边界，普通角色契约保持兼容。
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RoleResponse getDetail(Long roleId) {
        openRoleSupport.readLock(roleId);
        RoleResponse response = RoleResponse.from(getById(roleId));
        response.setOpenRoleBinding(openRoleSupport.summary(roleId));
        return response;
    }

    /**
     * 获取角色（按编码）
     */
    public IamRoleEntity getByCode(String code) {
        return roleRepository.findByCode(code)
                .orElseThrow(() -> new SimpleIamServerException("角色不存在：" + code));
    }

    private String normalizeRequired(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new SimpleIamServerException(message);
        }
        return value.trim();
    }

    private String normalizeOptional(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    /**
     * 递增持有该角色的全部用户（个人直接 ∪ 部门继承）的权限版本
     * （角色权限调整时触发在线会话热刷新）
     */
    private void bumpUsersOfRole(Long roleId) {
        bumpPermissionVersion(new ArrayList<>(effectiveRoleResolver.resolveUserIdsByRoleId(roleId)));
    }

    /**
     * 递增指定用户集合的权限版本；空集合跳过（幂等空转不触发刷新）
     */
    private void bumpPermissionVersion(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        userRepository.bumpPermissionVersion(userIds);
    }
}
