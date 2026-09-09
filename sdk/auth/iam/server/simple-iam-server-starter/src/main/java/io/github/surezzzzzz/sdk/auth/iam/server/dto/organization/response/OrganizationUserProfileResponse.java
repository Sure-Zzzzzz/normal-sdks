package io.github.surezzzzzz.sdk.auth.iam.server.dto.organization.response;

import io.github.surezzzzzz.sdk.auth.iam.server.dto.authorization.response.PermissionSummaryResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.authorization.response.RoleSummaryResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.department.response.DepartmentResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.user.response.AdminUserResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.usergroup.response.UserGroupResponse;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * 用户组织关系详情响应
 *
 * @author surezzzzzz
 */
@Data
@AllArgsConstructor
public class OrganizationUserProfileResponse {

    /**
     * 用户基本信息
     */
    private AdminUserResponse user;

    /**
     * 用户所属部门，无归属时为空
     */
    private DepartmentResponse department;

    /**
     * 用户所属协作组
     */
    private List<UserGroupResponse> userGroups;

    /**
     * 用户有效角色（个人直接 ∪ 所属直属部门挂载），每项携带来源标记
     */
    private List<RoleSummaryResponse> roles;

    /**
     * 由有效角色聚合得到的有效权限，每项携带来源标记，只读展示
     */
    private List<PermissionSummaryResponse> effectivePermissions;
}
