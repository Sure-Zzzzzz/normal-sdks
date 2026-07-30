package io.github.surezzzzzz.sdk.crm.server.core.domain.fulfillment;

import io.github.surezzzzzz.sdk.crm.server.core.domain.commercial.FulfillmentObligationTemplate;
import io.github.surezzzzzz.sdk.crm.server.core.support.CrmValidationHelper;
import lombok.Getter;

/**
 * 订单行对应的冻结履约义务。
 *
 * @author surezzzzzz
 */
@Getter
public final class FulfillmentItem {

    private final String fulfillmentId;
    private final String tenantId;
    private final String orderId;
    private final String orderLineId;
    private final int version;
    private final FulfillmentState state;
    private final FulfillmentObligationTemplate obligationTemplate;
    private final String consumerId;
    private final int consumerProtocolVersion;

    /**
     * 创建FulfillmentItem。
     *
     * @param fulfillmentId           履约项唯一标识
     * @param tenantId                租户唯一标识
     * @param orderId                 订单唯一标识
     * @param orderLineId             订单行唯一标识
     * @param version                 报价版本事实或版本号
     * @param state                   业务状态
     * @param obligationTemplate      冻结的履约义务模板
     * @param consumerId              consumerId参数。
     * @param consumerProtocolVersion consumerProtocolVersion参数。
     *
     */
    public FulfillmentItem(String fulfillmentId, String tenantId, String orderId, String orderLineId,
                           int version, FulfillmentState state,
                           FulfillmentObligationTemplate obligationTemplate, String consumerId,
                           int consumerProtocolVersion) {
        this.fulfillmentId = CrmValidationHelper.required(fulfillmentId, "fulfillmentId");
        this.tenantId = CrmValidationHelper.required(tenantId, "tenantId");
        this.orderId = CrmValidationHelper.required(orderId, "orderId");
        this.orderLineId = CrmValidationHelper.required(orderLineId, "orderLineId");
        this.version = CrmValidationHelper.positiveVersion(version, "version");
        this.state = CrmValidationHelper.requiredObject(state, "state");
        this.obligationTemplate = CrmValidationHelper.requiredObject(obligationTemplate, "obligationTemplate");
        this.consumerId = CrmValidationHelper.required(consumerId, "consumerId");
        this.consumerProtocolVersion = CrmValidationHelper.positiveVersion(consumerProtocolVersion,
                "consumerProtocolVersion");
    }


}
