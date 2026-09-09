package io.github.surezzzzzz.sdk.auth.iam.server.dto.portal.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Portal 侧边栏可访问应用响应
 *
 * <p>Portal 前端拉取侧边栏数据用：当前用户有权访问、启用 Portal 集成的应用 + 菜单。
 * {@code entry}/{@code apiBase} 按部署环境存值，后端直读库不读 Spring profile。
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
     * 微前端 entry URL（按部署环境存值，运行时直读）
     */
    private String entry;

    /**
     * 后端 API 基地址
     */
    private String apiBase;

    /**
     * Portal 菜单项（完整路径）
     */
    private List<PortalMenuItem> menus;
}
