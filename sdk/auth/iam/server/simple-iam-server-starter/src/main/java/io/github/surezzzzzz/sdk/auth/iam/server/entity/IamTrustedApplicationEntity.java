package io.github.surezzzzzz.sdk.auth.iam.server.entity;

import lombok.Data;

import javax.persistence.*;
import java.time.Instant;

/**
 * IAM 可信应用实体
 *
 * <p>一应用可关联多个 OAuth2 客户端（{@code oauth2_registered_client.application_id}），
 * 区分可信前端（接入 Portal 侧边栏）与可信后端（驱动授权码流程）。
 *
 * @author surezzzzzz
 */
@Data
@Entity
@Table(name = "iam_trusted_application")
public class IamTrustedApplicationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "application_code", length = 64, nullable = false, unique = true)
    private String applicationCode;

    @Column(name = "application_name", length = 128, nullable = false)
    private String applicationName;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "icon", length = 64)
    private String icon;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
