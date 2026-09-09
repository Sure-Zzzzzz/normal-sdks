package io.github.surezzzzzz.sdk.auth.iam.server.dto.user.request;

import lombok.Data;

/**
 * 更新用户请求
 *
 * @author surezzzzzz
 */
@Data
public class UpdateUserRequest {

    private String displayName;

    private String email;

    private String phone;

    private Long departmentId;

    private Boolean clearDepartment;
}
