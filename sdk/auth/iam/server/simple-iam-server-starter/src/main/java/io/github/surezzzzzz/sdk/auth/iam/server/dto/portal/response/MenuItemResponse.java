package io.github.surezzzzzz.sdk.auth.iam.server.dto.portal.response;

import lombok.Builder;
import lombok.Getter;

/**
 * Portal 菜单项响应
 *
 * <p>{@code route} 为相对 {@code route_prefix} 的存储值（管理端展示用）；
 * 侧边栏读模型 {@link PortalMenuItem} 才拼成完整路径。
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public class MenuItemResponse {

    /**
     * 菜单项编码
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
