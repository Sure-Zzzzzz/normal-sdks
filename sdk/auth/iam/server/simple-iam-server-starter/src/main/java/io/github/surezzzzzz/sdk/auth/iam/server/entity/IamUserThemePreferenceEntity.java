package io.github.surezzzzzz.sdk.auth.iam.server.entity;

import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.Instant;

/**
 * IAM 用户 Portal 主题偏好实体。
 *
 * @author surezzzzzz
 */
@Data
@Entity
@Table(name = "iam_user_theme_preference")
public class IamUserThemePreferenceEntity {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "contract_version", nullable = false)
    private Integer contractVersion;

    @Column(name = "mode", length = 16, nullable = false)
    private String mode;

    @Column(name = "custom_tokens_json", columnDefinition = "LONGTEXT")
    private String customTokensJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
