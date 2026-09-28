package io.github.surezzzzzz.sdk.auth.aksk.server.entity;

import lombok.Data;

import javax.persistence.*;
import java.time.Instant;

/**
 * 用户级 AKSK 的不可变身份源所属人绑定。
 *
 * <p>该表不保存三权快照和 reader 凭据。OWNER_INHERITED 的每次有效授权必须重新向身份源读取。</p>
 */
@Data
@Entity
@Table(name = "aksk_client_owner_binding")
public class AkskClientOwnerBindingEntity {

    @Id
    @Column(name = "client_id", length = 100)
    private String clientId;

    @Column(name = "owner_source_id", length = 64, nullable = false, updatable = false)
    private String ownerSourceId;

    @Column(name = "owner_subject_id", length = 128, nullable = false, updatable = false)
    private String ownerSubjectId;

    @Column(name = "target_application_id", nullable = false, updatable = false)
    private Long targetApplicationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "authorization_mode", length = 32, nullable = false, updatable = false)
    private AkskOwnerAuthorizationMode authorizationMode;

    @Column(name = "owner_state", nullable = false)
    private Integer ownerState;

    /**
     * 生命周期命令使用乐观锁，避免旧页面覆盖已轮换或终止的凭证。
     */
    @Version
    @Column(name = "lifecycle_version", nullable = false)
    private Long lifecycleVersion;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "binding_origin", length = 64, nullable = false, updatable = false)
    private String bindingOrigin;

    @Column(name = "bound_at", nullable = false, updatable = false)
    private Instant boundAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
