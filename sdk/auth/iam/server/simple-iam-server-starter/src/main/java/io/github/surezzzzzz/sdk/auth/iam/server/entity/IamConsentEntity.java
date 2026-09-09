package io.github.surezzzzzz.sdk.auth.iam.server.entity;

import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.Instant;

/**
 * IAM 授权确认投影实体
 *
 * <p>用于 IAM 查询和审计；SAS {@code oauth2_authorization_consent} 才是协议授权确认真相源。
 *
 * @author surezzzzzz
 */
@Data
@Entity
@Table(name = "iam_consent")
public class IamConsentEntity {

    @Id
    @Column(length = 100)
    private String id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "registered_client_id", length = 100, nullable = false)
    private String registeredClientId;

    @Column(name = "client_id", length = 100, nullable = false)
    private String clientId;

    @Column(name = "authorized_scopes", length = 1000, nullable = false)
    private String authorizedScopes;

    @Column(name = "status", nullable = false)
    private Integer status;

    @Column(name = "granted_at", nullable = false)
    private Instant grantedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;
}
