package io.github.surezzzzzz.sdk.auth.iam.server.entity.portal;

import lombok.Data;

import javax.persistence.*;
import java.time.Instant;

/**
 * Portal 全局设置单例。
 *
 * <p>固定主键为 1；登录首页只引用应用 ID，应用内默认 PAGE 由该应用 Portal 配置统一维护。
 */
@Data
@Entity
@Table(name = "iam_portal_setting")
public class IamPortalSettingEntity {

    public static final int SINGLETON_ID = 1;

    @Id
    private Integer id;

    @Column(name = "login_landing_application_id")
    private Long loginLandingApplicationId;

    /**
     * Portal 全局配置乐观锁版本。登录首页、应用根节点排序及其成员变化必须竞争同一版本。
     */
    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
