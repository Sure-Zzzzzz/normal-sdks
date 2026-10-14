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
    private final String capability;
    private final int protocolVersion;
    /**
     * 是否启用（停用后不参与新履约选择，历史不受影响）。
     */
    private final boolean enabled;

    /**
     * 创建FulfillmentConsumer。
     *
     * @param consumerId      consumerId参数。
     * @param capability      capability参数。
     * @param protocolVersion 消费者协议版本
     *
     */
    public FulfillmentConsumer(String consumerId, String capability, int protocolVersion) {
        this(consumerId, capability, protocolVersion, true);
    }

    /**
     * 创建FulfillmentConsumer（含启停）。
     *
     * @param consumerId      消费者标识（须绑定其 AKP clientId）
     * @param capability      满足的消费者能力
     * @param protocolVersion 协议版本
     * @param enabled         是否启用
     */
    public FulfillmentConsumer(String consumerId, String capability, int protocolVersion, boolean enabled) {
        this.consumerId = CrmValidationHelper.required(consumerId, "consumerId");
        this.capability = CrmValidationHelper.required(capability, "capability");
        this.protocolVersion = CrmValidationHelper.positiveVersion(protocolVersion, "protocolVersion");
        this.enabled = enabled;
    }


}
