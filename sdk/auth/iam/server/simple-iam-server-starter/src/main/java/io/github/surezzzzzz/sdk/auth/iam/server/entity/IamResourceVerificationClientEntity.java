package io.github.surezzzzzz.sdk.auth.iam.server.entity;

import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.Instant;

/**
 * IAM 资源令牌验证客户端实体。
 *
 * @author surezzzzzz
 */
@Data
@Entity
@Table(name = "iam_resource_verification_client")
public class IamResourceVerificationClientEntity {

    @Id
    @Column(length = 100)
    private String id;

    @Column(name = "application_id", nullable = false)
    private Long applicationId;

    @Column(name = "client_id", length = 100, nullable = false, unique = true)
    private String clientId;

    @Column(name = "client_secret_hash", length = 200, nullable = false)
    private String clientSecretHash;

    @Column(name = "status", nullable = false)
    private Integer status = SimpleIamServerConstant.STATUS_ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;
}
