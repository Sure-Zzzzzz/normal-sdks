package io.github.surezzzzzz.sdk.auth.iam.server.dto.user.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 创建用户请求
 *
 * @author surezzzzzz
 */
@Getter
@Setter
public class CreateUserRequest {

    private String username;

    private String password;

    private String displayName;

    private String email;

    private String phone;

    private Long departmentId;
}
