package io.github.surezzzzzz.sdk.crm.server.core.domain.offering;

import io.github.surezzzzzz.sdk.crm.server.core.domain.commercial.CommercialCapabilityType;
import io.github.surezzzzzz.sdk.crm.server.core.error.CrmErrorCode;
import io.github.surezzzzzz.sdk.crm.server.core.error.CrmException;
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
    private final String offeringReference;
    private final String displayName;
    private final OfferingState state;
    private final CommercialCapabilityType capabilityType;
    private final String requiredConsumerCapability;
    /**
     * 归属主体（iam:/aksk: 前缀，创建者）。
     */
    private final String owner;
    private final long aggregateVersion;
    private final Instant createdAt;
    private final Instant updatedAt;

    /**
     * 创建Offering。
     *
     * @param offeringId                 商品或服务唯一标识
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
    public Offering(String offeringId, String offeringReference, String displayName,
                    OfferingState state, CommercialCapabilityType capabilityType,
                    String requiredConsumerCapability, String owner, long aggregateVersion,
                    Instant createdAt, Instant updatedAt) {
        this.offeringId = CrmValidationHelper.required(offeringId, "offeringId");
        this.offeringReference = CrmValidationHelper.required(offeringReference, "offeringReference");
        this.displayName = CrmValidationHelper.required(displayName, "displayName");
        this.state = CrmValidationHelper.requiredObject(state, "state");
        this.capabilityType = CrmValidationHelper.requiredObject(capabilityType, "capabilityType");
        this.owner = CrmValidationHelper.required(owner, owner);
        this.requiredConsumerCapability = CrmValidationHelper.required(requiredConsumerCapability,
                "requiredConsumerCapability");
        this.aggregateVersion = CrmValidationHelper.positiveVersion(aggregateVersion, "aggregateVersion");
        this.createdAt = CrmValidationHelper.requiredObject(createdAt, "createdAt");
        this.updatedAt = CrmValidationHelper.requiredObject(updatedAt, "updatedAt");
    }


    /**
     * 上架：录入或下架状态可上架，上架后可进入新报价。
     *
     * @param updatedAt 变更后的更新时间
     * @return 上架后的商品事实
     */
    public Offering published(Instant updatedAt) {
        if (state == OfferingState.PUBLISHED) {
            throw new CrmException(CrmErrorCode.INVALID_STATE_TRANSITION, "offering already published");
        }
        return new Offering(offeringId, offeringReference, displayName, OfferingState.PUBLISHED, capabilityType,
                requiredConsumerCapability, owner, aggregateVersion + 1L, createdAt, updatedAt);
    }

    /**
     * 下架：可售状态可下架，下架后不进新报价、历史不受影响，可重新上架。
     *
     * @param updatedAt 变更后的更新时间
     * @return 下架后的商品事实
     */
    public Offering disabled(Instant updatedAt) {
        if (state != OfferingState.PUBLISHED) {
            throw new CrmException(CrmErrorCode.INVALID_STATE_TRANSITION, "offering is not published");
        }
        return new Offering(offeringId, offeringReference, displayName, OfferingState.DISABLED, capabilityType,
                requiredConsumerCapability, owner, aggregateVersion + 1L, createdAt, updatedAt);
    }

    /**
     * 是否可售（可进入新报价）。
     *
     * @return 可售返回 true
     */
    public boolean isPublished() {
        return state == OfferingState.PUBLISHED;
    }
}
