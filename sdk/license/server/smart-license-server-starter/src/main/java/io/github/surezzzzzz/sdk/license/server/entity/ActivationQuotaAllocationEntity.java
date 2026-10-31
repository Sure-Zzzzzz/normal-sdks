package io.github.surezzzzzz.sdk.license.server.entity;

import java.time.Instant;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

/**
 * 部署激活的本地额度分配事实；一条激活对同一指标只能有一条当前快照。
 *
 * <p>分配编号、授权修订、保留规则和固定窗口锚均随激活固化，签发时只回读此表，
 * 不从模型权益或请求参数临时推导可用额度。</p>
 *
 * @author surezzzzzz
 */
@Entity
@Table(name = "license_activation_quota_allocation",
        uniqueConstraints = @UniqueConstraint(columnNames = {"activation_id", "metric"}))
public class ActivationQuotaAllocationEntity {

    @Id
    @Column(name = "allocation_id", length = 64, nullable = false)
    private String allocationId;

    @Column(name = "grant_id", length = 128, nullable = false)
    private String grantId;

    @Column(name = "activation_id", length = 64, nullable = false)
    private String activationId;

    @Column(name = "grant_revision", nullable = false)
    private long grantRevision;

    @Column(name = "metric", length = 128, nullable = false)
    private String metric;

    @Column(name = "quantity", nullable = false)
    private long quantity;

    /** FIXED/PERPETUAL；固定保留必须同时有 retainUntil。 */
    @Column(name = "retention", length = 16, nullable = false)
    private String retention;

    @Column(name = "retain_until")
    private Long retainUntil;

    /** 固定窗口指标的授权级锚点；TOTAL 指标为空。 */
    @Column(name = "window_anchor_at")
    private Long windowAnchorAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ActivationQuotaAllocationEntity() {
    }

    public ActivationQuotaAllocationEntity(String allocationId, String grantId, String activationId,
                                           long grantRevision, String metric, long quantity,
                                           String retention, Long retainUntil, Long windowAnchorAt) {
        if (quantity < 1L || metric == null || metric.isEmpty()) {
            throw new IllegalArgumentException("额度分配数量或指标无效");
        }
        if (!"FIXED".equals(retention) && !"PERPETUAL".equals(retention)) {
            throw new IllegalArgumentException("额度分配保留规则无效");
        }
        if ("FIXED".equals(retention) && (retainUntil == null || retainUntil <= 0L)) {
            throw new IllegalArgumentException("固定额度分配缺少保留截止");
        }
        if ("PERPETUAL".equals(retention) && retainUntil != null) {
            throw new IllegalArgumentException("永久额度分配不得携带保留截止");
        }
        this.allocationId = allocationId;
        this.grantId = grantId;
        this.activationId = activationId;
        this.grantRevision = grantRevision;
        this.metric = metric;
        this.quantity = quantity;
        this.retention = retention;
        this.retainUntil = retainUntil;
        this.windowAnchorAt = windowAnchorAt;
        this.createdAt = Instant.now();
    }

    public String getAllocationId() {
        return allocationId;
    }

    public String getGrantId() {
        return grantId;
    }

    public String getActivationId() {
        return activationId;
    }

    public long getGrantRevision() {
        return grantRevision;
    }

    public String getMetric() {
        return metric;
    }

    public long getQuantity() {
        return quantity;
    }

    public String getRetention() {
        return retention;
    }

    public Long getRetainUntil() {
        return retainUntil;
    }

    public Long getWindowAnchorAt() {
        return windowAnchorAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
