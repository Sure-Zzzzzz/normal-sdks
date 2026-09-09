package io.github.surezzzzzz.sdk.auth.iam.server.dto.portal.request;

import lombok.Data;

/**
 * Portal 菜单项请求
 *
 * <p>{@code route} 为相对 {@code route_prefix} 的路径，服务端拼成完整路径供乾坤注册。
 *
 * @author surezzzzzz
 */
@Data
public class MenuItemRequest {

    /**
     * 菜单项编码，应用下唯一
     */
    private String code;

    /**
     * 菜单项名称
     */
    private String name;

    /**
     * 菜单项路由（相对 route_prefix）
     */
    private String route;

    /**
     * 排序值
     */
    private Integer sortOrder;
}
