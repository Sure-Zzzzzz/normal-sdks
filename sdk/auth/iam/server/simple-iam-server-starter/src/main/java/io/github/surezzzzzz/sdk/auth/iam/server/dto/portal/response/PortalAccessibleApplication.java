package io.github.surezzzzzz.sdk.auth.iam.server.dto.portal.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Portal 侧边栏可访问应用响应
 *
 * <p>Portal 前端拉取侧边栏数据用：当前用户有权访问、启用 Portal 集成的应用 + 菜单。
 * 动态业务应用的 {@code entry}/{@code apiBase} 是统一入口公共路径；平台内置应用仍由固定入口承载。
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public class PortalAccessibleApplication {

    /**
     * 应用编码
     */
    private String applicationCode;

    /**
     * 应用展示名
     */
    private String applicationName;

    /**
     * 应用描述
     */
    private String description;

    /**
     * 应用图标标识
     */
    private String icon;

    /**
     * Portal 路由前缀（/app/{code}）
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
     * Portal 菜单项（完整路径）
     */
    private List<PortalMenuItem> menus;

    /**
     * 1.1 Portal 侧边栏递归菜单树
     */
    private List<PortalAccessibleMenuTreeNode> menuTree;

    /**
     * 当前用户可用的应用默认入口；引用 PAGE 不在裁剪树中时必须为 null。
     */
    private PortalDefaultEntryResponse defaultEntry;
}
