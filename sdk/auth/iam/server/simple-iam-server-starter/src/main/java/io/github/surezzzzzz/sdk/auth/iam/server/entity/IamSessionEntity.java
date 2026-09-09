package io.github.surezzzzzz.sdk.auth.iam.server.entity;

import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.Instant;

/**
 * IAM 会话实体
 *
 * <p>Redis 为主存储（热数据），MySQL 为持久化备份（冷数据 + 审计）。
 *
 * @author surezzzzzz
 */
@Data
@Entity
@Table(name = "iam_session")
public class IamSessionEntity {

    @Id
    @Column(length = 100)
    private String id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "username", length = 64, nullable = false)
    private String username;

    @Column(name = "issuer", length = 255)
    private String issuer;

    @Column(name = "access_token_jti", length = 100)
    private String accessTokenJti;

    @Column(name = "refresh_token_family_id", length = 100)
    private String refreshTokenFamilyId;

    @Column(name = "client_id", length = 100)
    private String clientId;

    @Column(name = "servlet_session_id_hash", length = 64)
    private String servletSessionIdHash;

    @Column(name = "auth_time")
    private Instant authTime;

    @Column(name = "last_active_at")
    private Instant lastActiveAt;

    @Column(name = "mfa_level", nullable = false)
    private Integer mfaLevel = SimpleIamServerConstant.DEFAULT_MFA_LEVEL;

    @Column(name = "remote_ip", length = 64)
    private String remoteIp;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    /**
     * 状态：1=活跃，0=已撤销
     */
    @Column(name = "status", nullable = false)
    private Integer status = SimpleIamServerConstant.STATUS_ACTIVE;
}
