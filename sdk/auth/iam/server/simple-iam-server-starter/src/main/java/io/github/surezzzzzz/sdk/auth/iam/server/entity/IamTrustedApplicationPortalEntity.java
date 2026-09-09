package io.github.surezzzzzz.sdk.auth.iam.server.entity;

import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.Instant;

/**
 * IAM 可信应用 Portal 集成配置实体
 *
 * <p>与 {@link IamTrustedApplicationEntity} 1:1 共享主键；启用后该应用作为 qiankun 微前端出现在 Portal 侧边栏。
 * {@code entry}/{@code apiBase} 按部署环境直接存值，后端不读 Spring profile。
 *
 * @author surezzzzzz
 */
@Data
@Entity
@Table(name = "iam_trusted_application_portal")
public class IamTrustedApplicationPortalEntity {

    @Id
    @Column(name = "application_id")
    private Long applicationId;

    /**
     * 是否启用 Portal 集成：1=启用，0=禁用
     */
    @Column(name = "enabled", nullable = false)
    private Integer enabled = SimpleIamServerConstant.DEFAULT_PORTAL_INTEGRATION_ENABLED;

    @Column(name = "route_prefix", length = 64, nullable = false)
    private String routePrefix;

    @Column(name = "entry", length = 512)
    private String entry;

    @Column(name = "api_base", length = 512)
    private String apiBase;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
