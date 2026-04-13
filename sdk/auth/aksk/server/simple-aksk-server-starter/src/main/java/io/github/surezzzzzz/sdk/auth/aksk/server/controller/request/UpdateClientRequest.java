package io.github.surezzzzzz.sdk.auth.aksk.server.controller.request;

import lombok.Data;

import java.util.List;

/**
 * Client更新请求（用于PATCH操作）
 * 所有字段均为可选，仅更新非null字段
 *
 * @author surezzzzzz
 */
@Data
public class UpdateClientRequest {

    /**
     * 是否启用（true=启用，false=禁用）
     */
    private Boolean enabled;

    /**
     * 权限范围列表（用于批量同步用户权限）
     */
    private List<String> scopes;

    /**
     * Client名称（用于更新名称）
     */
    private String name;
}
