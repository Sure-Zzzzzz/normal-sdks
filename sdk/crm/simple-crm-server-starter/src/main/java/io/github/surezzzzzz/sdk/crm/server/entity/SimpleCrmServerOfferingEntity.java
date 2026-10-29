package io.github.surezzzzzz.sdk.crm.server.entity;

import io.github.surezzzzzz.sdk.crm.server.core.domain.commercial.CommercialCapabilityType;
import io.github.surezzzzzz.sdk.crm.server.core.domain.offering.Offering;
import io.github.surezzzzzz.sdk.crm.server.core.domain.offering.OfferingState;
import lombok.Getter;

import java.time.Instant;

/**
 * 可销售目录 JDBC 映射实体（三态）。
 *
 * @author surezzzzzz
 */
@Getter
public final class SimpleCrmServerOfferingEntity {

    private final String offeringId;
    private final String offeringReference;
    private final String displayName;
    private final String stateCode;
    private final String capabilityTypeCode;
    private final String requiredConsumerCapability;
    private final String owner;
    private final long aggregateVersion;
    private final Instant createdAt;
    private final Instant updatedAt;

    /**
     * 创建SimpleCrmServerOfferingEntity。
     *
     * @param offeringId                商品或服务唯一标识
     * @param offeringReference         稳定业务引用
     * @param displayName               展示名称
     * @param stateCode                 状态稳定 code（draft/published/disabled）
     * @param capabilityTypeCode        商业能力类型稳定 code
     * @param requiredConsumerCapability 所需消费者能力
     * @param owner                     归属主体（iam:/aksk: 前缀）
     * @param aggregateVersion          聚合版本
     * @param createdAt                 创建时间
     * @param updatedAt                 变更时间
     */
    public SimpleCrmServerOfferingEntity(String offeringId, String offeringReference, String displayName, String stateCode,
                          String capabilityTypeCode, String requiredConsumerCapability, String owner,
                          long aggregateVersion, Instant createdAt, Instant updatedAt) {
        this.offeringId = offeringId;
        this.offeringReference = offeringReference;
        this.displayName = displayName;
        this.stateCode = stateCode;
        this.capabilityTypeCode = capabilityTypeCode;
        this.requiredConsumerCapability = requiredConsumerCapability;
        this.owner = owner;
        this.aggregateVersion = aggregateVersion;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * 实体转领域事实。
     *
     * @return 商品或服务领域事实
     */
    public Offering toDomain() {
        return new Offering(offeringId, offeringReference, displayName, OfferingState.fromCode(stateCode),
                CommercialCapabilityType.fromCode(capabilityTypeCode), requiredConsumerCapability, owner,
                aggregateVersion, createdAt, updatedAt);
    }

    /**
     * 领域事实转实体。
     *
     * @param offering 商品或服务领域事实
     * @return JDBC 映射实体
     */
    public static SimpleCrmServerOfferingEntity from(Offering offering) {
        return new SimpleCrmServerOfferingEntity(offering.getOfferingId(), offering.getOfferingReference(),
                offering.getDisplayName(), offering.getState().getCode(),
                offering.getCapabilityType().getCode(), offering.getRequiredConsumerCapability(),
                offering.getOwner(), offering.getAggregateVersion(), offering.getCreatedAt(),
                offering.getUpdatedAt());
    }
}
