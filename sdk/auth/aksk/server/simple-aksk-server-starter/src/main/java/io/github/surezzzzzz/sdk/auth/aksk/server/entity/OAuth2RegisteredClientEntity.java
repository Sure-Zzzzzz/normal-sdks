package io.github.surezzzzzz.sdk.auth.aksk.server.entity;

import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.Instant;

/**
 * OAuth2 Registered Client Entity
 * 扩展的 oauth2_registered_client 表实体
 *
 * @author surezzzzzz
 */
@Data
@Entity
@Table(name = "oauth2_registered_client")
public class OAuth2RegisteredClientEntity {

    @Id
    @Column(length = 100)
    private String id;

    @Column(name = "client_id", length = 100, nullable = false)
    private String clientId;

    @Column(name = "client_id_issued_at", nullable = false)
    private Instant clientIdIssuedAt;

    @Column(name = "client_secret", length = 200)
    private String clientSecret;

    @Column(name = "client_secret_expires_at")
    private Instant clientSecretExpiresAt;

    @Column(name = "client_name", length = 200, nullable = false)
    private String clientName;

    @Column(name = "client_authentication_methods", length = 1000, nullable = false)
    private String clientAuthenticationMethods;

    @Column(name = "authorization_grant_types", length = 1000, nullable = false)
    private String authorizationGrantTypes;

    @Column(name = "redirect_uris", length = 1000)
    private String redirectUris;

    @Column(name = "scopes", length = 1000, nullable = false)
    private String scopes;

    @Column(name = "client_settings", length = 2000, nullable = false)
    private String clientSettings;

    @Column(name = "token_settings", length = 2000, nullable = false)
    private String tokenSettings;

    /**
     * 扩展字段：所属用户ID（用户级AKSK）
     */
    @Column(name = "owner_user_id", length = 100)
    private String ownerUserId;

    /**
     * 扩展字段：所属用户名（用户级AKSK）
     */
    @Column(name = "owner_username", length = 255)
    private String ownerUsername;

    /**
     * 扩展字段：客户端类型（1=平台级，2=用户级）
     */
    @Column(name = "client_type", nullable = false)
    private Integer clientType;

    /**
     * 扩展字段：是否启用（true=启用，false=禁用）
     */
    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;
}
