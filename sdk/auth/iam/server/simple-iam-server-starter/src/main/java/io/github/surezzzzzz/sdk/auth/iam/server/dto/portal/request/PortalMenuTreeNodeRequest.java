package io.github.surezzzzzz.sdk.auth.iam.server.dto.portal.request;

import lombok.Data;

import java.util.List;

/**
 * Portal 菜单树节点请求。
 *
 * <p>PAGE 的 route 为相对 routePrefix 的路径，可选择 STANDARD 或 IMMERSIVE 展示模式；
 * GROUP 不允许携带 route、页面权限或展示模式。
 *
 * @author surezzzzzz
 */
@Data
public class PortalMenuTreeNodeRequest {

    /**
     * 应用内唯一菜单编码
     */
    private String code;

    /**
     * 菜单名称
     */
    private String name;

    /**
     * 节点类型：GROUP 或 PAGE
     */
    private String nodeType;

    /**
     * 菜单节点图标编码；为空时客户端按节点类型回退
     */
    private String icon;

    /**
     * PAGE 的相对路由
     */
    private String route;

    /**
     * PAGE 绑定的页面权限码；为空时继承应用准入
     */
    private String requiredPagePermission;

    /**
     * PAGE 的 Portal 宿主展示模式：STANDARD 或 IMMERSIVE；GROUP 不允许指定。
     */
    private String presentationMode;

    /**
     * 同级排序值
     */
    private Integer sortOrder;

    /**
     * 子节点；PAGE 必须为空
     */
    private List<PortalMenuTreeNodeRequest> children;
}
