package io.github.surezzzzzz.sdk.auth.iam.server.dto.portal.request;

import lombok.Data;

import java.util.List;

/**
 * Portal 集成配置请求
 *
 * <p>注册可信应用时一并提交 Portal 集成配置；{@code enabled=true} 时 {@code entry} 必填。
 * {@code routePrefix} 由服务端按 {@code /app/{applicationCode}} 生成，不在请求中。
 * {@code entry}/{@code apiBase} 按部署环境直接存值。
 *
 * @author surezzzzzz
 */
@Data
public class PortalIntegrationRequest {

    /**
     * 是否启用 Portal 集成，默认 false
     */
    private Boolean enabled;

    /**
     * 微前端 entry URL（enabled=true 时必填）
     */
    private String entry;

    /**
     * 后端 API 基地址
     */
    private String apiBase;

    /**
     * Portal 菜单项列表
     */
    private List<MenuItemRequest> menus;
}
