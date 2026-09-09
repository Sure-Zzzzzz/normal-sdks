package io.github.surezzzzzz.sdk.auth.iam.server.entity;

import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import lombok.Data;

import javax.persistence.*;
import java.time.Instant;

/**
 * IAM 用户实体
 *
 * @author surezzzzzz
 */
@Data
@Entity
@Table(name = "iam_user")
public class IamUserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username", length = 64, nullable = false, unique = true)
    private String username;

    @Column(name = "password_hash", length = 200, nullable = false)
    private String passwordHash;

    @Column(name = "display_name", length = 128)
    private String displayName;

    @Column(name = "email", length = 128)
    private String email;

    @Column(name = "phone", length = 32)
    private String phone;

    @Column(name = "department_id")
    private Long departmentId;

    /**
     * 外部身份源编码（如 ldap-password），本地账号为 null
     */
    @Column(name = "identity_source", length = 64)
    private String identitySource;

    /**
     * 外部体系稳定唯一标识（LDAP DN/uid、OIDC sub）
     */
    @Column(name = "external_id", length = 128)
    private String externalId;

    /**
     * 状态：1=启用，0=禁用
     */
    @Column(name = "status", nullable = false)
    private Integer status = SimpleIamServerConstant.STATUS_ACTIVE;

    @Column(name = "failed_login_count", nullable = false)
    private Integer failedLoginCount = SimpleIamServerConstant.DEFAULT_FAILED_LOGIN_COUNT;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    /**
     * 须改密标记：true=须先修改密码（管理员建号/重置密码置 true，自助改密成功清 false）
     */
    @Column(name = "must_change_password", nullable = false)
    private Boolean mustChangePassword = Boolean.FALSE;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    /**
     * 权限版本：角色/权限/绑定任一变更时递增，会话校验对比后热刷新登录态权限
     */
    @Column(name = "permission_version", nullable = false)
    private Long permissionVersion = 0L;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
