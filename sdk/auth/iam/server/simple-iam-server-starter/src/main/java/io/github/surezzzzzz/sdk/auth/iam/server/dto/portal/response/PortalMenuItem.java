package io.github.surezzzzzz.sdk.auth.iam.server.dto.portal.response;

import lombok.Builder;
import lombok.Getter;

/**
 * Portal 侧边栏菜单项响应
 *
 * <p>{@code route} 为完整路径：{@code routePrefix} + 相对路径，供 qiankun 直接注册。
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public class PortalMenuItem {

    /**
     * 菜单项编码
     */
    private String code;

    /**
     * 菜单项名称
     */
    private String name;

    /**
     * 菜单项完整路由（routePrefix + 相对路径）
     */
    private String route;

    /**
     * 排序值
     */
    private Integer sortOrder;
}
