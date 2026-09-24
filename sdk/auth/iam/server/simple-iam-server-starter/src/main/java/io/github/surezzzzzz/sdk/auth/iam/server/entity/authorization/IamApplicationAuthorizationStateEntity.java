package io.github.surezzzzzz.sdk.auth.iam.server.entity.authorization;

import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.Instant;

/**
 * 可信应用的 AKU 授权状态。
 *
 * <p>OAuth 安全纪元属于可信应用主表；本表只表达人员授权投影的版本与重算屏障，二者不得混用。
 */
@Data
@Entity
@Table(name = "iam_application_authorization_state")
public class IamApplicationAuthorizationStateEntity {

    @Id
    @Column(name = "application_id")
    private Long applicationId;

    @Column(name = "authorization_epoch", nullable = false)
    private Long authorizationEpoch = 1L;

    /**
     * 仅供 OWNER_INHERITED 使用的目标应用访问纪元，与 OAuth 安全纪元及三权纪元隔离。
     */
    @Column(name = "owner_inherited_access_epoch", nullable = false)
    private Long ownerInheritedAccessEpoch = 1L;

    @Column(name = "owner_inheritance_enabled", nullable = false)
    private Integer ownerInheritanceEnabled = SimpleIamServerConstant.STATUS_INACTIVE;

    /**
     * 批量重算未收敛时 reader 必须失败关闭。
     */
    @Column(name = "recompute_barrier", nullable = false)
    private Integer recomputeBarrier = SimpleIamServerConstant.STATUS_INACTIVE;

    @Column(name = "barrier_version", nullable = false)
    private Long barrierVersion = 0L;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
