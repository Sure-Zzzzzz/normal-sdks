package io.github.surezzzzzz.sdk.license.server.entity;

import java.time.Instant;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import javax.persistence.Version;
import io.github.surezzzzzz.sdk.license.server.constant.QuotaConstant;
import lombok.Getter;

/**
 * 额度操作台账：把预占/累计扣减的幂等事实和余额变化绑定到数据库。
 *
 * @author surezzzzzz
 */
@Entity
@Getter
@Table(name = "license_quota_reservation",
        uniqueConstraints = @UniqueConstraint(columnNames = {"operation_key"}))
public class QuotaReservationEntity {

    @Id
    @Column(name = "reservation_id", length = 128, nullable = false)
    private String reservationId;

    @Column(name = "grant_id", length = 128, nullable = false)
    private String grantId;

    @Column(name = "metric", length = 128, nullable = false)
    private String metric;

    @Column(name = "quantity", nullable = false)
    private long quantity;

    @Column(name = "kind", length = 16, nullable = false)
    private String kind;

    /** 消费与预占绑定的真实模型动作；重放不允许换动作。 */
    @Column(name = "action", length = 128, nullable = false)
    private String action;

    @Column(name = "grant_revision", nullable = false)
    private long grantRevision;

    @Column(name = "business_operation_id", length = 160, nullable = false)
    private String businessOperationId;

    /** grant/metric/kind/业务号/认证主体/来源的长度分帧SHA-256，避免超长复合唯一索引。 */
    @Column(name = "operation_key", length = 64, nullable = false)
    private String operationKey;

    /** 固定资格及部署实例只作为真实使用对象索引，主体仍取认证上下文。 */
    @Column(name = "assignment_id", length = 128)
    private String assignmentId;

    @Column(name = "activation_id", length = 128)
    private String activationId;

    @Column(name = "state", length = 16, nullable = false)
    private String state;

    @Column(name = "fence", nullable = false)
    private long fence;

    @Column(name = "valid_until", nullable = false)
    private long validUntil;

    @Column(name = "requester_subject", length = 160, nullable = false)
    private String requesterSubject;

    @Column(name = "authentication_source", length = 160, nullable = false)
    private String authenticationSource;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;

    protected QuotaReservationEntity() {
    }

    public QuotaReservationEntity(String reservationId, String grantId, String metric, long quantity,
                                  String kind, String action, long grantRevision,
                                  String businessOperationId, String operationKey,
                                  long fence, long validUntil, String requesterSubject,
                                  String authenticationSource, String assignmentId, String activationId) {
        this.reservationId = reservationId;
        this.grantId = grantId;
        this.metric = metric;
        this.quantity = quantity;
        this.kind = kind;
        this.action = action;
        this.grantRevision = grantRevision;
        this.businessOperationId = businessOperationId;
        this.operationKey = operationKey;
        this.assignmentId = assignmentId;
        this.activationId = activationId;
        this.state = QuotaConstant.ACTIVE;
        this.fence = fence;
        this.validUntil = validUntil;
        this.requesterSubject = requesterSubject;
        this.authenticationSource = authenticationSource;
        this.createdAt = Instant.now();
    }

    /** 显式释放：仅当前活动预占可进入终态。 */
    public void release() {
        if (!QuotaConstant.ACTIVE.equals(state)) {
            throw new IllegalStateException(QuotaConstant.ENDED);
        }
        state = QuotaConstant.RELEASED;
    }

    /** 到期回收只推进状态，不重置幂等号或删除首次消费事实。 */
    public void expire() {
        if (QuotaConstant.ACTIVE.equals(state)) {
            state = QuotaConstant.EXPIRED;
        }
    }

    /** 续期更换围栏；旧持有者不能用旧fence续期当前预占。 */
    public void renew(long newFence, long newValidUntil) {
        if (!QuotaConstant.ACTIVE.equals(state) || newFence <= fence || newValidUntil <= validUntil) {
            throw new IllegalStateException(QuotaConstant.ENDED);
        }
        fence = newFence;
        validUntil = newValidUntil;
    }
}
