package io.github.surezzzzzz.sdk.auth.iam.server.entity;

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

    @Column(name = "code", length = 64, nullable = false)
    private String code;

    @Column(name = "name", length = 128, nullable = false)
    private String name;

    @Column(name = "route", length = 255, nullable = false)
    private String route;

    /**
     * 排序值
     */
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = SimpleIamServerConstant.DEFAULT_SORT_ORDER;
}
