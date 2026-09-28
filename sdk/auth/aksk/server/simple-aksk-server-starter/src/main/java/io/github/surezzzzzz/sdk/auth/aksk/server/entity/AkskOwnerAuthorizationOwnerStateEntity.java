package io.github.surezzzzzz.sdk.auth.aksk.server.entity;

import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.Instant;

/**
 * AKSK 侧缓存的身份源所属人安全最终态。
 */
@Data
@Entity
@Table(name = "aksk_owner_authorization_owner_state")
public class AkskOwnerAuthorizationOwnerStateEntity {

    @Id
    @Column(name = "owner_key", length = 193)
    private String ownerKey;

    @Column(name = "owner_source_id", length = 64, nullable = false)
    private String ownerSourceId;

    @Column(name = "owner_subject_id", length = 128, nullable = false)
    private String ownerSubjectId;

    @Column(name = "active", nullable = false)
    private Integer active;

    @Column(name = "owner_security_epoch", nullable = false)
    private Long ownerSecurityEpoch;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
