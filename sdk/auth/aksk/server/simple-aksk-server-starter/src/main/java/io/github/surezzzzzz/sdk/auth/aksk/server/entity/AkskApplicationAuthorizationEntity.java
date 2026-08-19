package io.github.surezzzzzz.sdk.auth.aksk.server.entity;

import lombok.Data;

import javax.persistence.*;
import java.time.Instant;

/**
 * AKSK 服务主体应用授权投影。
 *
 * @author surezzzzzz
 */
@Data
@Entity
@Table(name = "aksk_application_authorization")
public class AkskApplicationAuthorizationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_id", length = 100, nullable = false, unique = true)
    private String clientId;

    @Column(name = "application_code", length = 64, nullable = false)
    private String applicationCode;

    @Column(name = "admitted", nullable = false)
    private Boolean admitted = Boolean.FALSE;

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

    @Version
    @Column(name = "lock_version", nullable = false)
    private Long lockVersion;

    @Column(name = "manifest_version", length = 128, nullable = false)
    private String manifestVersion;

    @Column(name = "manifest_digest", length = 256, nullable = false)
    private String manifestDigest;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled = Boolean.TRUE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;
}
