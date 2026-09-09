package io.github.surezzzzzz.sdk.auth.iam.server.service;

import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.RoleSource;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.department.response.DepartmentResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.organization.response.OrganizationDepartmentWorkspaceResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.organization.response.OrganizationTreeNodeResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.organization.response.OrganizationUserProfileResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.response.AdminPageResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.user.response.AdminUserResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.usergroup.response.UserGroupResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamDepartmentEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamUserEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.DepartmentMemberCount;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamUserRepository;
import lombok.RequiredArgsConstructor;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 组织与成员工作台查询服务
 *
 * @author surezzzzzz
 */
@SimpleIamServerComponent
@RequiredArgsConstructor
public class OrganizationService {

    private final DepartmentService departmentService;
    private final UserService userService;
    private final UserGroupService userGroupService;
    private final RoleService roleService;
    private final IamEffectiveRoleResolver effectiveRoleResolver;
    private final IamUserRepository userRepository;

    /**
     * 查询完整部门树与直属成员数
     *
     * <p>直属成员数统计所有仍归属该部门的用户，包含停用用户，不递归统计子部门成员。
     *
     * @return 按部门排序构建的完整部门树
     */
    public List<OrganizationTreeNodeResponse> getTree() {
        List<IamDepartmentEntity> departments = departmentService.getAllDepartments();
        if (departments.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, Long> memberCounts = userRepository.countDirectMembersByDepartment().stream()
                .collect(Collectors.toMap(DepartmentMemberCount::getDepartmentId,
                        DepartmentMemberCount::getMemberCount));
        Map<Long, OrganizationTreeNodeResponse> nodes = new HashMap<Long, OrganizationTreeNodeResponse>();
        for (IamDepartmentEntity department : departments) {
            nodes.put(department.getId(), new OrganizationTreeNodeResponse(
                    department.getId(), department.getCode(), department.getName(), department.getParentId(),
                    department.getStatus(), department.getSortOrder(),
                    memberCounts.containsKey(department.getId()) ? memberCounts.get(department.getId()) : 0L));
        }
        List<OrganizationTreeNodeResponse> roots = new ArrayList<OrganizationTreeNodeResponse>();
        for (IamDepartmentEntity department : departments) {
            OrganizationTreeNodeResponse node = nodes.get(department.getId());
            OrganizationTreeNodeResponse parent = nodes.get(department.getParentId());
            if (parent == null) {
                roots.add(node);
            } else {
                parent.getChildren().add(node);
            }
        }
        return roots;
    }

    /**
     * 查询部门工作区
     *
     * @param departmentId 部门 ID
     * @param status       成员状态，为空时不过滤
     * @param keyword      成员关键字，为空时不过滤
     * @param page         页码，从 1 开始
     * @param size         每页大小
     * @return 当前部门、直属子部门与直属成员分页结果
     */
    public OrganizationDepartmentWorkspaceResponse getDepartmentWorkspace(Long departmentId,
                                                                          Integer status,
                                                                          String keyword,
                                                                          int page,
                                                                          int size) {
        IamDepartmentEntity department = departmentService.getById(departmentId);
        List<DepartmentResponse> directChildren = departmentService.getAllDepartments().stream()
                .filter(item -> departmentId.equals(item.getParentId()))
                .map(item -> DepartmentResponse.from(item, department.getName()))
                .collect(Collectors.toList());
        AdminPageResponse<AdminUserResponse> members = AdminPageResponse.from(
                userService.listUsers(status, departmentId, keyword, null, null, false, page, size)
                        .map(user -> AdminUserResponse.from(user, department.getName())));
        return new OrganizationDepartmentWorkspaceResponse(
                toDepartmentResponse(department), directChildren, members);
    }

    /**
     * 查询用户组织关系详情
     *
     * @param userId 用户 ID
     * @return 用户、部门、协作组、直接角色和有效权限的聚合详情
     */
    public OrganizationUserProfileResponse getUserProfile(Long userId) {
        IamUserEntity user = userService.getById(userId);
        IamDepartmentEntity department = user.getDepartmentId() == null
                ? null : departmentService.getById(user.getDepartmentId());
        Map<Long, RoleSource> roleSources = effectiveRoleResolver.resolveRoleIdsWithSource(userId);
        return new OrganizationUserProfileResponse(
                AdminUserResponse.from(user, department == null ? null : department.getName()),
                department == null ? null : toDepartmentResponse(department),
                userGroupService.getUserGroups(userId).stream()
                        .map(UserGroupResponse::from)
                        .collect(Collectors.toList()),
                roleService.getUserRoleSummaries(roleSources),
                roleService.getUserPermissionSummaries(roleSources));
    }

    /**
     * 转换部门摘要
     *
     * @param department 部门实体
     * @return 部门摘要
     */
    private DepartmentResponse toDepartmentResponse(IamDepartmentEntity department) {
        return DepartmentResponse.from(department, departmentService.getDepartmentName(department.getParentId()));
    }
}
