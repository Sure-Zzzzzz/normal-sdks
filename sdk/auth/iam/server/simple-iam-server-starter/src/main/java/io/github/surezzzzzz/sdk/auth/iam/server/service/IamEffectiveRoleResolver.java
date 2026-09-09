package io.github.surezzzzzz.sdk.auth.iam.server.service;

import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.RoleSource;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamDepartmentRoleEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamUserEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamUserRoleEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamDepartmentRoleRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamUserRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamUserRoleRepository;
import lombok.RequiredArgsConstructor;

import java.util.*;

/**
 * 用户有效角色解析器：用户有效角色 = 个人直接角色（{@code iam_user_role}）
 * ∪ 所属直属部门挂载的角色（{@code iam_department_role}），实时动态并集，不做一次性复制。
 *
 * <p>部门树有父子层级，但角色继承只认用户的直属部门，不做祖先部门链路的级联上溯。
 *
 * <p>独立收口，只依赖用户/角色关联的底层 Repository，不依赖 {@link RoleService} /
 * {@link IamAuthorizationProjectionService}，避免与两者形成循环 Bean 依赖
 * （{@code RoleService} 依赖 {@code IamAuthorizationProjectionService}）。
 *
 * @author surezzzzzz
 */
@SimpleIamServerComponent
@RequiredArgsConstructor
public class IamEffectiveRoleResolver {

    private final IamUserRoleRepository userRoleRepository;
    private final IamDepartmentRoleRepository departmentRoleRepository;
    private final IamUserRepository userRepository;

    /**
     * 该用户的有效角色ID集合：个人直接角色 ∪ 所属直属部门挂载的角色。
     */
    public Set<Long> resolveRoleIds(Long userId) {
        return resolveRoleIdsWithSource(userId).keySet();
    }

    /**
     * 解析用户有效角色及其来源：个人直接授予标记 DIRECT；直属部门挂载的角色标记 DEPARTMENT_INHERITED，
     * 若同一角色个人也直接持有，以个人直接为准
     */
    public Map<Long, RoleSource> resolveRoleIdsWithSource(Long userId) {
        Map<Long, RoleSource> roleSources = new LinkedHashMap<>();
        for (IamUserRoleEntity userRole : userRoleRepository.findByUserId(userId)) {
            roleSources.put(userRole.getRoleId(), RoleSource.DIRECT);
        }
        Long departmentId = userRepository.findById(userId)
                .map(IamUserEntity::getDepartmentId)
                .orElse(null);
        if (departmentId != null) {
            for (IamDepartmentRoleEntity departmentRole : departmentRoleRepository.findByDepartmentId(departmentId)) {
                roleSources.putIfAbsent(departmentRole.getRoleId(), RoleSource.DEPARTMENT_INHERITED);
            }
        }
        return roleSources;
    }

    /**
     * 持有该角色的用户ID集合：个人直接绑定该角色的用户 ∪ 挂载该角色的部门下的全部用户。
     */
    public Set<Long> resolveUserIdsByRoleId(Long roleId) {
        return resolveUserIdsByRoleIdIn(Collections.singletonList(roleId));
    }

    /**
     * 持有 {@code roleIds} 中任一角色的用户ID集合（批量版本）。
     */
    public Set<Long> resolveUserIdsByRoleIdIn(Collection<Long> roleIds) {
        Set<Long> userIds = new LinkedHashSet<>();
        if (roleIds == null || roleIds.isEmpty()) {
            return userIds;
        }
        for (IamUserRoleEntity userRole : userRoleRepository.findByRoleIdIn(roleIds)) {
            userIds.add(userRole.getUserId());
        }
        Set<Long> departmentIds = new LinkedHashSet<>();
        for (IamDepartmentRoleEntity departmentRole : departmentRoleRepository.findByRoleIdIn(roleIds)) {
            departmentIds.add(departmentRole.getDepartmentId());
        }
        if (!departmentIds.isEmpty()) {
            for (IamUserEntity user : userRepository.findByDepartmentIdIn(new ArrayList<>(departmentIds))) {
                userIds.add(user.getId());
            }
        }
        return userIds;
    }
}
