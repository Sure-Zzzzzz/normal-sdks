package io.github.surezzzzzz.sdk.auth.iam.server.dto.authorization.response;

import io.github.surezzzzzz.sdk.auth.iam.server.constant.RoleSource;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamRoleEntity;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 角色摘要响应
 *
 * @author surezzzzzz
 */
@Data
@AllArgsConstructor
public class RoleSummaryResponse {

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
     * 角色来源：direct 个人直接 / department_inherited 部门继承
     */
    private String source;

    /**
     * 从角色实体转换摘要
     *
     * @param role   角色实体
     * @param source 角色来源
     * @return 角色摘要
     */
    public static RoleSummaryResponse from(IamRoleEntity role, RoleSource source) {
        return new RoleSummaryResponse(role.getId(), role.getCode(), role.getName(),
                role.getDescription(), role.getBuiltIn(), source.getCode());
    }
}
