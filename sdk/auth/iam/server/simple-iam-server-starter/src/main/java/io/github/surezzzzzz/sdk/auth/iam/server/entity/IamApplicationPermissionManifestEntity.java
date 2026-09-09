package io.github.surezzzzzz.sdk.auth.iam.server.entity;

import lombok.Data;

import javax.persistence.*;
import java.time.Instant;

/**
 * 可信应用权限清单实体：应用申报的角色 / 页面 / 接口权限码清单，
 * 是应用授权（iam_application_authorization）勾选范围的事实源。
 * 每应用一行；manifestVersion 服务端单调递增，digest 为规范化内容摘要。
 *
 * @author surezzzzzz
 */
@Data
@Entity
@Table(name = "iam_application_permission_manifest", uniqueConstraints = {
        @UniqueConstraint(name = "uk_manifest_application", columnNames = "application_id")
})
public class IamApplicationPermissionManifestEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "application_id", nullable = false)
    private Long applicationId;

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
    @Column(name = "data_resources_json", nullable = false)
    private String dataResourcesJson;

    @Column(name = "manifest_version", nullable = false)
    private Long manifestVersion;

    @Column(name = "manifest_digest", length = 256, nullable = false)
    private String manifestDigest;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
