package io.github.surezzzzzz.sdk.auth.iam.server.dto.portal.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Portal 集成配置响应
 *
 * <p>{@code routePrefix} 由服务端按 {@code /app/{applicationCode}} 生成并回显。
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public class PortalIntegrationResponse {

    /**
     * 是否启用 Portal 集成
     */
    private boolean enabled;

    /**
     * Portal 路由前缀（/app/{code}，服务端生成）
     */
    private String routePrefix;

    /**
     * 微前端 entry URL（按部署环境存值）
     */
    private String entry;

    /**
     * 后端 API 基地址（按部署环境存值）
     */
    private String apiBase;

    /**
     * Portal 菜单项列表
     */
    private List<MenuItemResponse> menus;
}
