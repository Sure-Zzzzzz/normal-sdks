package io.github.surezzzzzz.sdk.crm.server.core.domain.fulfillment;

import io.github.surezzzzzz.sdk.crm.server.core.support.CrmValidationHelper;
import lombok.Getter;

/**
 * 已通过安全校验的履约消费者摘要。
 *
 * @author surezzzzzz
 */
@Getter
public final class FulfillmentConsumer {

    private final String consumerId;
    private final String tenantId;
    private final String capability;
    private final int protocolVersion;

    /**
     * 创建FulfillmentConsumer。
     *
     * @param consumerId      consumerId参数。
     * @param tenantId        租户唯一标识
     * @param capability      capability参数。
     * @param protocolVersion 消费者协议版本
     *
     */
    public FulfillmentConsumer(String consumerId, String tenantId, String capability, int protocolVersion) {
        this.consumerId = CrmValidationHelper.required(consumerId, "consumerId");
        this.tenantId = CrmValidationHelper.required(tenantId, "tenantId");
        this.capability = CrmValidationHelper.required(capability, "capability");
        this.protocolVersion = CrmValidationHelper.positiveVersion(protocolVersion, "protocolVersion");
    }


}
