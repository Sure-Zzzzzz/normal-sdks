package io.github.surezzzzzz.sdk.auth.iam.server.dto.portal.request;

import lombok.Data;

import java.util.List;

/**
 * Portal 集成配置请求
 *
 * <p>注册可信应用时一并提交 Portal 集成配置；动态应用 {@code enabled=true} 时 {@code entry}、
 * {@code apiBase} 与 {@code targetOrigin} 必填。
 * {@code routePrefix} 由服务端按 {@code /app/{applicationCode}} 生成，不在请求中。
 * {@code entry} 是 routePrefix 下的初始页面路径，{@code apiBase} 是 API 公共路径；页面与 API
 * 共用 {@code targetOrigin} 指向的业务自有 Nginx，由该 Nginx 自行分发到内部服务。
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
     * Portal 初始文档的公共页面路径，必须等于或位于 routePrefix 下。
     */
    private String entry;

    /**
     * API 的公共路径前缀，不是 API 服务地址。
     */
    private String apiBase;

    /**
     * 页面和 API 共用的业务自有 Nginx HTTP origin。
     */
    private String targetOrigin;

    /**
     * Portal 菜单项列表
     */
    private List<MenuItemRequest> menus;

    /**
     * 1.1 菜单树完整快照；与 {@link #menus} 不能同时提交。
     */
    private List<PortalMenuTreeNodeRequest> menuTree;
}
