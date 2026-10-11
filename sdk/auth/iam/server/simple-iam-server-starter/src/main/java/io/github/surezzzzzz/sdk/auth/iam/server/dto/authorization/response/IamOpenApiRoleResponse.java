package io.github.surezzzzzz.sdk.auth.iam.server.dto.authorization.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;

/**
 * 开放 API 角色目录响应（1.3.7）：对账/下拉所需字段集。
 *
 * @author surezzzzzz
 */
@Data
@AllArgsConstructor
public class IamOpenApiRoleResponse {

    /**
     * 角色数字 ID（挂角色操作用）
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
     * 描述
     */
    private String description;

    /**
     * 是否内置（1=内置）
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
}
