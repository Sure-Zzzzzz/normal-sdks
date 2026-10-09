package io.github.surezzzzzz.sdk.auth.iam.server.entity.user;

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

    /**
     * 对外用户主体：SPI 生成，写后不改；软删行永久占位（永不复用）
     */
    @Column(name = "subject_id", length = 64, unique = true)
    private String subjectId;

    /**
     * 手机号绑定时间（绑定/换绑验证通过时写入，登录不更新）；NULL=未绑定
     */
    @Column(name = "phone_bound_at")
    private Instant phoneBoundAt;

    @Column(name = "phone", length = 32, unique = true)
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

    /**
     * 须改密原因（值域见 {@link MustChangePasswordReason}）：与须改密标记同写同清（1.3.6），
     * 持久化以支撑登录响应与 /me 的文案下传
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "must_change_password_reason", length = 32)
    private MustChangePasswordReason mustChangePasswordReason;

    /**
     * 密码最近一次设置时刻：建号为 null（策略未激活），策略开启后首次登录回填为登录时刻（激活基线），
     * 改密/重置/忘记密码重置刷新；口令最长生存期策略据此判定（1.3.6，默认关闭）
     */
    @Column(name = "password_updated_at")
    private Instant passwordUpdatedAt;

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
