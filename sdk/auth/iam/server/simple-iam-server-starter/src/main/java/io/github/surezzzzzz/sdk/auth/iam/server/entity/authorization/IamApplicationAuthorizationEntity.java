package io.github.surezzzzzz.sdk.auth.iam.server.entity.authorization;

import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import lombok.Data;

import javax.persistence.*;
import java.time.Instant;

/**
 * IAM 用户应用授权投影实体。
 *
 * @author surezzzzzz
 */
@Data
@Entity
@Table(name = "iam_application_authorization")
public class IamApplicationAuthorizationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "application_id", nullable = false)
    private Long applicationId;

    @Column(name = "admitted", nullable = false)
    private Integer admitted = SimpleIamServerConstant.STATUS_INACTIVE;

    @Lob
    @Column(name = "roles_json", nullable = false)
    private String rolesJson;

    @Lob
    @Column(name = "page_permissions_json", nullable = false)
    private String pagePermissionsJson;

    @Lob
    @Column(name = "api_permissions_json", nullable = false)
    private String apiPermissionsJson;

    @Lob
    @Column(name = "data_grant_document_json")
    private String dataGrantDocumentJson;

    @Column(name = "authorization_version", nullable = false)
    private Long authorizationVersion;

    /**
     * 所属人权限版本映射出的安全纪元，供 AKU 签发与审计追溯。
     */
    @Column(name = "owner_security_epoch", nullable = false)
    private Long ownerSecurityEpoch = 1L;

    /**
     * 可信应用级授权纪元，和 OAuth 安全纪元独立。
     */
    @Column(name = "application_authorization_epoch", nullable = false)
    private Long applicationAuthorizationEpoch = 1L;

    /**
     * 所属人-应用投影访问纪元：任何可见授权或准入状态变化都递增，防止撤权后旧 AKU 复活。
     */
    @Column(name = "projection_access_epoch", nullable = false)
    private Long projectionAccessEpoch = 1L;

    @Column(name = "manifest_version", length = 128, nullable = false)
    private String manifestVersion;

    @Column(name = "manifest_digest", length = 256, nullable = false)
    private String manifestDigest;

    @Column(name = "status", nullable = false)
    private Integer status = SimpleIamServerConstant.STATUS_ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;
}
