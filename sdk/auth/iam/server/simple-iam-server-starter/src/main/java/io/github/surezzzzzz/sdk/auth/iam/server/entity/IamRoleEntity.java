package io.github.surezzzzzz.sdk.auth.iam.server.entity;

import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import lombok.Data;

import javax.persistence.*;
import java.time.Instant;

/**
 * IAM 角色实体
 *
 * @author surezzzzzz
 */
@Data
@Entity
@Table(name = "iam_role")
public class IamRoleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", length = 64, nullable = false, unique = true)
    private String code;

    @Column(name = "name", length = 128, nullable = false)
    private String name;

    @Column(name = "description", length = 255)
    private String description;

    /**
     * 是否内置：1=内置（不可删），0=自定义
     */
    @Column(name = "built_in", nullable = false)
    private Integer builtIn = SimpleIamServerConstant.DEFAULT_BUILT_IN;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
