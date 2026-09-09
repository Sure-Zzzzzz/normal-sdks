package io.github.surezzzzzz.sdk.auth.iam.server.entity;

import lombok.Data;

import javax.persistence.*;
import java.time.Instant;

/**
 * IAM 部门-角色关联实体
 *
 * @author surezzzzzz
 */
@Data
@Entity
@Table(name = "iam_department_role")
public class IamDepartmentRoleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "department_id", nullable = false)
    private Long departmentId;

    @Column(name = "role_id", nullable = false)
    private Long roleId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
