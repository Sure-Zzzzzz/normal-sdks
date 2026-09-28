package io.github.surezzzzzz.sdk.auth.aksk.server.entity;

import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.Instant;

/**
 * AKSK 侧缓存的目标可信应用最终态。
 */
@Data
@Entity
@Table(name = "aksk_owner_authorization_target_application_state")
public class AkskOwnerAuthorizationTargetApplicationStateEntity {

    @Id
    @Column(name = "target_application_id")
    private Long targetApplicationId;

    @Column(name = "active", nullable = false)
    private Integer active;

    @Column(name = "application_authorization_epoch", nullable = false)
    private Long applicationAuthorizationEpoch;

    @Column(name = "owner_inherited_access_epoch", nullable = false)
    private Long ownerInheritedAccessEpoch;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
