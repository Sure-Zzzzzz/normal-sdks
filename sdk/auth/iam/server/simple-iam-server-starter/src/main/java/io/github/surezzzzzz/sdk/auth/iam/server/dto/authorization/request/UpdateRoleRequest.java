package io.github.surezzzzzz.sdk.auth.iam.server.dto.authorization.request;

import lombok.Data;

/**
 * 更新角色请求
 *
 * @author surezzzzzz
 */
@Data
public class UpdateRoleRequest {

    private String name;

    private String description;
}
