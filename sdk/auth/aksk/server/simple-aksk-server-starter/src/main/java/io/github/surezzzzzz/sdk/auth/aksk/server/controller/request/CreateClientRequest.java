package io.github.surezzzzzz.sdk.auth.aksk.server.controller.request;

import lombok.Data;

import java.util.List;

/**
 * 创建Client请求
 *
 * @author surezzzzzz
 */
@Data
public class CreateClientRequest {
    /**
     * Client类型: "platform" 或 "user"
     */
    private String type;

    /**
     * Client名称
     */
    private String name;

    /**
     * 所属用户ID (用户级AKSK必填)
     */
    private String ownerUserId;

    /**
     * 所属用户名 (可选)
     */
    private String ownerUsername;

    /**
     * 权限范围 (可选)
     */
    private List<String> scopes;
}
