package io.github.surezzzzzz.sdk.auth.iam.server.entity;

import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import lombok.Data;

import javax.persistence.*;
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

    /**
     * 默认入口引用同应用菜单 code；菜单快照重建后行 ID 不稳定，不能引用菜单 ID。
     */
    @Column(name = "default_page_menu_code", length = 64)
    private String defaultPageMenuCode;

    /**
     * 相对 routePrefix 的静态入口路径，为空时使用默认 PAGE 路由。
     */
    @Column(name = "default_entry_path", length = 255)
    private String defaultEntryPath;

    /**
     * Portal 配置乐观锁版本，菜单与默认入口变更不能静默互相覆盖。
     */
    @Version
    @Column(name = "config_version", nullable = false)
    private Long configVersion;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
