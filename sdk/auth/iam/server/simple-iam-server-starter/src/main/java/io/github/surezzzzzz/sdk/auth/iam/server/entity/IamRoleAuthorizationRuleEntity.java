package io.github.surezzzzzz.sdk.auth.iam.server.entity;

import lombok.Data;

import javax.persistence.*;
import java.time.Instant;

/**
 * IAM 角色应用授权规则实体。
 * 定义某角色在某应用下的权限集合。
 *
 * @author surezzzzzz
 */
@Data
@Entity
@Table(name = "iam_role_authorization_rule")
public class IamRoleAuthorizationRuleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "role_id", nullable = false)
    private Long roleId;

    @Column(name = "application_id", nullable = false)
    private Long applicationId;

    @Lob
    @Column(name = "page_permissions_json", nullable = false)
    private String pagePermissionsJson;

    @Lob
    @Column(name = "api_permissions_json", nullable = false)
    private String apiPermissionsJson;

    @Lob
    @Column(name = "data_grant_template_json")
    private String dataGrantTemplateJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
