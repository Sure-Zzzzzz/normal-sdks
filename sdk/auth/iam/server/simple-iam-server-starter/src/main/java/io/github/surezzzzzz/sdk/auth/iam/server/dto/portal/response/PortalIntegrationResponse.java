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
     * Portal 初始页面入口；动态业务应用必须位于 routePrefix 下。
     */
    private String entry;

    /**
     * 业务 API 的统一入口公共路径；不是独立 API 服务地址。
     */
    private String apiBase;

    /**
     * 页面和 API 共用的业务自有 Nginx HTTP origin。
     */
    private String targetOrigin;

    /**
     * Portal 菜单项列表
     */
    private List<MenuItemResponse> menus;

    /**
     * 1.1 管理面递归菜单树
     */
    private List<PortalMenuTreeNodeResponse> menuTree;

    /**
     * 应用根路由默认入口；null 表示按历史行为处理。
     */
    private PortalDefaultEntryResponse defaultEntry;

    /**
     * 完整 Portal 配置乐观锁版本。
     */
    private Long configVersion;
}
