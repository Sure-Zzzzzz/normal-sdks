package io.github.surezzzzzz.sdk.auth.iam.server.dto.authorization.response;

import io.github.surezzzzzz.sdk.auth.iam.server.constant.RoleSource;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamPermissionEntity;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 权限摘要响应
 *
 * @author surezzzzzz
 */
@Data
@AllArgsConstructor
public class PermissionSummaryResponse {

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
     * 权限类型
     */
    private String type;

    /**
     * 权限来源：direct 个人直接角色授予 / department_inherited 部门继承角色授予
     */
    private String source;

    /**
     * 从权限实体转换摘要
     *
     * @param permission 权限实体
     * @param source     权限来源
     * @return 权限摘要
     */
    public static PermissionSummaryResponse from(IamPermissionEntity permission, RoleSource source) {
        return new PermissionSummaryResponse(permission.getId(), permission.getCode(),
                permission.getName(), permission.getType(), source.getCode());
    }
}
