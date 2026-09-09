package io.github.surezzzzzz.sdk.auth.iam.server.dto.user.response;

import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamUserEntity;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * 开放 API 用户响应（组织与人员同步所需字段集，不带管理台视图字段）。
 *
 * @author surezzzzzz
 */
@Data
@AllArgsConstructor
public class UserRestResponse {

    private Long id;

    private String username;

    private String displayName;

    private Long departmentId;

    private String departmentName;

    private Integer status;

    private String email;

    private String phone;

    private List<String> roles;

    /**
     * 用户实体转开放 API 响应。
     *
     * @param user           用户实体
     * @param departmentName 部门名（可空）
     * @param roles          角色编码列表
     */
    public static UserRestResponse from(IamUserEntity user, String departmentName, List<String> roles) {
        return new UserRestResponse(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getDepartmentId(),
                departmentName,
                user.getStatus(),
                user.getEmail(),
                user.getPhone(),
                roles);
    }
}
