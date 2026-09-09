package io.github.surezzzzzz.sdk.auth.iam.server.dto.authorization.response;

import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamRoleEntity;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;

/**
 * 角色响应
 *
 * @author surezzzzzz
 */
@Data
@AllArgsConstructor
public class RoleResponse {

    /**
     * 角色 ID
     */
    private Long id;
    /**
     * 角色编码
     */
    private String code;
    /**
     * 角色名称
     */
    private String name;
    /**
     * 角色描述
     */
    private String description;
    /**
     * 是否内置角色
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
     * 从角色实体转换响应
     *
     * @param role 角色实体
     * @return 角色响应
     */
    public static RoleResponse from(IamRoleEntity role) {
        return new RoleResponse(role.getId(), role.getCode(), role.getName(), role.getDescription(),
                role.getBuiltIn(), role.getCreatedAt(), role.getUpdatedAt());
    }
}
