package io.github.surezzzzzz.sdk.auth.aksk.server.entity;

import lombok.Data;

import javax.persistence.*;
import java.time.Instant;

/**
 * AKSK 侧人员-目标应用 inherited 三权投影最终态。
 */
@Data
@Entity
@Table(name = "aksk_owner_authorization_projection")
public class AkskOwnerAuthorizationProjectionEntity {

    @Id
    @Column(name = "projection_key", length = 320)
    private String projectionKey;

    @Column(name = "owner_source_id", length = 64, nullable = false)
    private String ownerSourceId;

    @Column(name = "owner_subject_id", length = 128, nullable = false)
    private String ownerSubjectId;

    @Column(name = "target_application_id", nullable = false)
    private Long targetApplicationId;

    @Column(name = "active", nullable = false)
    private Integer active;

    @Column(name = "owner_security_epoch", nullable = false)
    private Long ownerSecurityEpoch;

    @Column(name = "application_authorization_epoch", nullable = false)
    private Long applicationAuthorizationEpoch;

    @Column(name = "owner_inherited_access_epoch", nullable = false)
    private Long ownerInheritedAccessEpoch;

    @Column(name = "projection_access_epoch", nullable = false)
    private Long projectionAccessEpoch;

    @Lob
    /** 物理列保留旧版本名称以兼容既有库，Java 侧不暴露身份源名称。 */
    @Column(name = "iam_authorization_json")
    private String authorizationJson;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
