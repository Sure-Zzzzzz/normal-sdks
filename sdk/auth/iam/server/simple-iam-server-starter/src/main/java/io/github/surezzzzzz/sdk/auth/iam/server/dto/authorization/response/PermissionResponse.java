package io.github.surezzzzzz.sdk.auth.iam.server.dto.authorization.response;

import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamPermissionEntity;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;

/**
 * 权限响应
 *
 * @author surezzzzzz
 */
@Data
@AllArgsConstructor
public class PermissionResponse {

    /**
     * 权限 ID
     */
    private Long id;
    /**
     * 权限编码
     */
    private String code;
    /**
     * 权限名称
     */
    private String name;
    /**
     * 权限描述
     */
    private String description;
    /**
     * 权限类型
     */
    private String type;
    /**
     * 是否内置权限
     */
    private Integer builtIn;
    /**
     * 创建时间
     */
    private Instant createdAt;
    /**
     * 更新时间
     */
    private Instant updatedAt;

    /**
     * 从权限实体转换响应
     *
     * @param permission 权限实体
     * @return 权限响应
     */
    public static PermissionResponse from(IamPermissionEntity permission) {
        return new PermissionResponse(permission.getId(), permission.getCode(), permission.getName(),
                permission.getDescription(), permission.getType(), permission.getBuiltIn(),
                permission.getCreatedAt(), permission.getUpdatedAt());
    }
}
