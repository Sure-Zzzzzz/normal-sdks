package io.github.surezzzzzz.sdk.auth.iam.server.dto.organization.response;

import io.github.surezzzzzz.sdk.auth.iam.server.dto.department.response.DepartmentResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.response.AdminPageResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.user.response.AdminUserResponse;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * 部门工作区响应
 *
 * @author surezzzzzz
 */
@Data
@AllArgsConstructor
public class OrganizationDepartmentWorkspaceResponse {

    /**
     * 当前部门
     */
    private DepartmentResponse department;

    /**
     * 当前部门的直属子部门
     */
    private List<DepartmentResponse> directChildren;

    /**
     * 当前部门的直属成员分页结果
     */
    private AdminPageResponse<AdminUserResponse> members;
}
