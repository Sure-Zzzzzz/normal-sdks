package io.github.surezzzzzz.sdk.auth.iam.server.entity;

import lombok.Data;

import javax.persistence.*;
import java.time.Instant;

/**
 * IAM 角色-权限关联实体
 *
 * @author surezzzzzz
 */
@Data
@Entity
@Table(name = "iam_role_permission")
public class IamRolePermissionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "role_id", nullable = false)
    private Long roleId;

    @Column(name = "permission_id", nullable = false)
    private Long permissionId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
