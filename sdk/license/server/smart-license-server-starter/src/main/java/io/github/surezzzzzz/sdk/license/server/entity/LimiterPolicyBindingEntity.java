package io.github.surezzzzzz.sdk.license.server.entity;

import io.github.surezzzzzz.sdk.license.core.spi.LimiterPolicyPort;
import io.github.surezzzzzz.sdk.license.server.constant.LimiterPolicyBindingState;
import java.time.Instant;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Version;
import lombok.Getter;

/**
 * License 自有的 limiter 策略投影台账。
 *
 * <p>该表只记录本域授权与远端规则的绑定事实，不复制 limiter 的策略表；远端写入
 * 结果未知时保留台账，后续按完整策略键回读恢复。</p>
 *
 * @author surezzzzzz
 */
@Getter
@Entity
@Table(name = "license_limiter_policy_binding")
public class LimiterPolicyBindingEntity {

    @Id
    @Column(name = "binding_id", length = 128, nullable = false)
    private String bindingId;

    @Column(name = "grant_id", length = 128, nullable = false)
    private String grantId;

    @Column(name = "grant_revision", nullable = false)
    private long grantRevision;

    @Column(name = "customer_id", length = 128, nullable = false)
    private String customerId;

    @Column(name = "service_code", length = 128, nullable = false)
    private String serviceCode;

    @Column(name = "resource_code", length = 128, nullable = false)
    private String resourceCode;

    @Column(name = "namespace", length = 128)
    private String namespace;

    @Column(name = "desired_policy", columnDefinition = "TEXT", nullable = false)
    private String desiredPolicy;

    @Column(name = "rule_id", length = 64)
    private String ruleId;

    @Column(name = "rule_row_version")
    private Long ruleRowVersion;

    @Column(name = "service_revision")
    private Long serviceRevision;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", length = 24, nullable = false)
    private LimiterPolicyBindingState state;

    @Column(name = "operation_id", length = 128, nullable = false)
    private String operationId;

    @Column(name = "failure_reason", length = 128)
    private String failureReason;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;

    protected LimiterPolicyBindingEntity() {
    }

    public LimiterPolicyBindingEntity(String bindingId, String grantId, long grantRevision,
                                      LimiterPolicyPort.PolicyRequest request, String desiredPolicy) {
        this.bindingId = bindingId;
        this.grantId = grantId;
        this.grantRevision = grantRevision;
        this.customerId = request.getKey().getCustomerId();
        this.serviceCode = request.getKey().getServiceCode();
        this.resourceCode = request.getKey().getResourceCode();
        this.namespace = request.getKey().getNamespace();
        this.desiredPolicy = desiredPolicy;
        this.operationId = request.getOperationId();
        this.state = request.isEnabled() ? LimiterPolicyBindingState.PENDING_SYNC
                : LimiterPolicyBindingState.DISABLE_PENDING;
        this.updatedAt = Instant.now();
    }

    public void synced(LimiterPolicyPort.PolicyResult result, boolean enabled) {
        this.ruleId = result.getRuleId();
        this.ruleRowVersion = result.getRowVersion();
        // 幂等命中及回读不产生新的服务修订，保留上次已确认值。
        if (result.getRevision() != LimiterPolicyPort.PolicyResult.NO_REVISION) {
            this.serviceRevision = result.getRevision();
        }
        if (result.getKey() != null && result.getKey().getNamespace() != null) {
            // namespace 是 Management 的服务声明，License 只回写远端确认值。
            this.namespace = result.getKey().getNamespace();
        }
        this.state = enabled ? LimiterPolicyBindingState.SYNCED : LimiterPolicyBindingState.DISABLED;
        this.failureReason = null;
        this.updatedAt = Instant.now();
    }

    public void pending(String desiredPolicy, long grantRevision, String operationId, boolean enabled) {
        this.desiredPolicy = desiredPolicy;
        this.grantRevision = grantRevision;
        this.operationId = operationId;
        this.state = enabled ? LimiterPolicyBindingState.PENDING_SYNC
                : LimiterPolicyBindingState.DISABLE_PENDING;
        this.failureReason = null;
        this.updatedAt = Instant.now();
    }

    public void failed(LimiterPolicyBindingState target, String reason) {
        this.state = target;
        this.failureReason = reason;
        this.updatedAt = Instant.now();
    }

    /** 同一客户的另一个授权接管了这个全局限频目标，旧绑定只保留历史事实。 */
    public void superseded() {
        this.state = LimiterPolicyBindingState.SUPERSEDED;
        this.failureReason = null;
        this.updatedAt = Instant.now();
    }
}
