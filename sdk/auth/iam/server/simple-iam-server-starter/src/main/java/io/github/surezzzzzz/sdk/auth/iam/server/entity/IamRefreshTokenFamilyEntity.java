package io.github.surezzzzzz.sdk.auth.iam.server.entity;

import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.Instant;

/**
 * IAM Refresh Token 族实体
 *
 * <p>用于 refresh token 轮换 + 重用检测；族内任意 token 被复用则整族撤销。
 *
 * @author surezzzzzz
 */
@Data
@Entity
@Table(name = "iam_refresh_token_family")
public class IamRefreshTokenFamilyEntity {

    @Id
    @Column(length = 100)
    private String id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "username", length = 64, nullable = false)
    private String username;

    @Column(name = "session_id", length = 100)
    private String sessionId;

    @Column(name = "current_token_hash", length = 128, nullable = false)
    private String currentTokenHash;

    @Column(name = "previous_token_hash", length = 128)
    private String previousTokenHash;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "rotated_at")
    private Instant rotatedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    /**
     * 状态：1=活跃，0=已撤销
     */
    @Column(name = "status", nullable = false)
    private Integer status = SimpleIamServerConstant.STATUS_ACTIVE;
}
