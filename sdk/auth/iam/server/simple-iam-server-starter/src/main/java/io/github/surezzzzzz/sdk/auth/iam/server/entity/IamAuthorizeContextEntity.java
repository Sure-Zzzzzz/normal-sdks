package io.github.surezzzzzz.sdk.auth.iam.server.entity;

import io.github.surezzzzzz.sdk.auth.iam.server.constant.IamAuthorizeContextStatus;
import lombok.Data;

import javax.persistence.*;
import java.time.Instant;

/**
 * IAM 授权交易上下文实体
 *
 * <p>保存 SAS 授权码流程外侧的会话绑定与用户交互状态；SAS 仍是授权码和令牌的唯一协议真相源。
 *
 * @author surezzzzzz
 */
@Data
@Entity
@Table(name = "iam_authorize_context")
public class IamAuthorizeContextEntity {

    @Id
    @Column(length = 100)
    private String id;

    @Column(name = "registered_client_id", length = 100, nullable = false)
    private String registeredClientId;

    @Column(name = "client_id", length = 100, nullable = false)
    private String clientId;

    @Column(name = "redirect_uri", length = 1000, nullable = false)
    private String redirectUri;

    @Column(name = "requested_scopes", length = 1000, nullable = false)
    private String requestedScopes;

    @Column(name = "state_hash", length = 64)
    private String stateHash;

    @Column(name = "nonce_hash", length = 64)
    private String nonceHash;

    @Column(name = "code_challenge_hash", length = 64)
    private String codeChallengeHash;

    @Column(name = "code_challenge_method", length = 32)
    private String codeChallengeMethod;

    @Column(name = "servlet_session_id_hash", length = 64)
    private String servletSessionIdHash;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "iam_session_id", length = 100)
    private String iamSessionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 32, nullable = false)
    private IamAuthorizeContextStatus status = IamAuthorizeContextStatus.PENDING_LOGIN;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "denied_at")
    private Instant deniedAt;

    @Column(name = "completed_at")
    private Instant completedAt;
}
