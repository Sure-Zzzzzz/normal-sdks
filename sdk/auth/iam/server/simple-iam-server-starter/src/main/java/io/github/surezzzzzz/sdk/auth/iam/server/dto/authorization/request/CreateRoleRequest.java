package io.github.surezzzzzz.sdk.auth.iam.server.dto.authorization.request;

import lombok.Data;

/**
 * 创建角色请求
 *
 * @author surezzzzzz
 */
@Data
public class CreateRoleRequest {

    private String code;

    private String name;

    private String description;
}
