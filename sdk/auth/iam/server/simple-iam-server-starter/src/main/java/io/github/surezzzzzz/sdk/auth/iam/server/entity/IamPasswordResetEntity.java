package io.github.surezzzzzz.sdk.auth.iam.server.entity;

import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.Instant;

/**
 * IAM 密码重置凭证实体
 *
 * <p>管理员重置或自助重置时生成的临时凭证。
 *
 * @author surezzzzzz
 */
@Data
@Entity
@Table(name = "iam_password_reset")
public class IamPasswordResetEntity {

    @Id
    @Column(length = 100)
    private String id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "username", length = 64, nullable = false)
    private String username;

    @Column(name = "token_hash", length = 128, nullable = false)
    private String tokenHash;

    @Column(name = "requested_by", length = 64, nullable = false)
    private String requestedBy;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    /**
     * 状态：1=有效，0=已使用/已撤销
     */
    @Column(name = "status", nullable = false)
    private Integer status = SimpleIamServerConstant.STATUS_ACTIVE;
}
