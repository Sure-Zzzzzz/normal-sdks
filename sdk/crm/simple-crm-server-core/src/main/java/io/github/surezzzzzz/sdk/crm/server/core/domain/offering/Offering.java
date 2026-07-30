package io.github.surezzzzzz.sdk.crm.server.core.domain.offering;

import io.github.surezzzzzz.sdk.crm.server.core.domain.commercial.CommercialCapabilityType;
import io.github.surezzzzzz.sdk.crm.server.core.support.CrmValidationHelper;
import lombok.Getter;

import java.time.Instant;

/**
 * 可进入新报价的商业 Offering。
 *
 * @author surezzzzzz
 */
@Getter
public final class Offering {

    private final String offeringId;
    private final String tenantId;
    private final String offeringReference;
    private final String displayName;
    private final OfferingState state;
    private final CommercialCapabilityType capabilityType;
    private final String requiredConsumerCapability;
    private final long aggregateVersion;
    private final Instant createdAt;
    private final Instant updatedAt;

    /**
     * 创建Offering。
     *
     * @param offeringId                 商品或服务唯一标识
     * @param tenantId                   租户唯一标识
     * @param offeringReference          商品或服务业务引用
     * @param displayName                展示名称
     * @param state                      业务状态
     * @param capabilityType             商业能力类型
     * @param requiredConsumerCapability 消费者必须具备的能力
     * @param aggregateVersion           聚合版本
     * @param createdAt                  创建时间
     * @param updatedAt                  变更后的更新时间
     *
     */
    public Offering(String offeringId, String tenantId, String offeringReference, String displayName,
                    OfferingState state, CommercialCapabilityType capabilityType,
                    String requiredConsumerCapability, long aggregateVersion,
                    Instant createdAt, Instant updatedAt) {
        this.offeringId = CrmValidationHelper.required(offeringId, "offeringId");
        this.tenantId = CrmValidationHelper.required(tenantId, "tenantId");
        this.offeringReference = CrmValidationHelper.required(offeringReference, "offeringReference");
        this.displayName = CrmValidationHelper.required(displayName, "displayName");
        this.state = CrmValidationHelper.requiredObject(state, "state");
        this.capabilityType = CrmValidationHelper.requiredObject(capabilityType, "capabilityType");
        this.requiredConsumerCapability = CrmValidationHelper.required(requiredConsumerCapability,
                "requiredConsumerCapability");
        this.aggregateVersion = CrmValidationHelper.positiveVersion(aggregateVersion, "aggregateVersion");
        this.createdAt = CrmValidationHelper.requiredObject(createdAt, "createdAt");
        this.updatedAt = CrmValidationHelper.requiredObject(updatedAt, "updatedAt");
    }


}
