package io.github.surezzzzzz.sdk.auth.iam.server.entity.portal;

import io.github.surezzzzzz.sdk.auth.iam.server.constant.PortalMenuNodeType;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.PortalPresentationMode;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import lombok.Data;

import javax.persistence.*;

/**
 * IAM 可信应用 Portal 菜单项实体
 *
 * <p>每个可信应用可挂多个菜单项，{@code route} 为相对 {@code route_prefix} 的路径。
 *
 * @author surezzzzzz
 */
@Data
@Entity
@Table(name = "iam_trusted_application_menu")
public class IamTrustedApplicationMenuEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "application_id", nullable = false)
    private Long applicationId;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "code", length = 64, nullable = false)
    private String code;

    @Column(name = "name", length = 128, nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "node_type", length = 16, nullable = false)
    private PortalMenuNodeType nodeType = PortalMenuNodeType.PAGE;

    /**
     * 菜单节点图标编码；为空时由 Portal 按节点类型稳定回退。
     */
    @Column(name = "icon", length = 64)
    private String icon;

    @Column(name = "route", length = 255)
    private String route;

    @Column(name = "required_page_permission", length = 256)
    private String requiredPagePermission;

    /**
     * PAGE 的 Portal 宿主展示模式；GROUP 固定为 STANDARD。IMMERSIVE 只去除宿主顶栏和侧栏，
     * 不改变会话、路由或页面权限边界。
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "presentation_mode", length = 16, nullable = false)
    private PortalPresentationMode presentationMode = PortalPresentationMode.STANDARD;

    /**
     * 排序值
     */
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = SimpleIamServerConstant.DEFAULT_SORT_ORDER;
}
