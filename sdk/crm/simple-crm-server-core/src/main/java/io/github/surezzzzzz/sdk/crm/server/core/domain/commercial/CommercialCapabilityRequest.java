package io.github.surezzzzzz.sdk.crm.server.core.domain.commercial;

import io.github.surezzzzzz.sdk.crm.server.core.domain.common.Money;
import io.github.surezzzzzz.sdk.crm.server.core.support.CrmValidationHelper;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * 商业能力的确定性输入。
 *
 * @author surezzzzzz
 */
@Getter
public final class CommercialCapabilityRequest {

    private final BigDecimal quantity;
    private final String unit;
    private final Money unitPrice;
    private final String subjectReference;
    private final String fulfillmentScope;
    private final String requiredConsumerCapability;

    /**
     * 创建CommercialCapabilityRequest。
     *
     * @param quantity                   正数数量
     * @param unit                       计量单位
     * @param unitPrice                  单价
     * @param subjectReference           履约标的引用
     * @param fulfillmentScope           履约范围
     * @param requiredConsumerCapability 消费者必须具备的能力
     *
     */
    public CommercialCapabilityRequest(BigDecimal quantity, String unit, Money unitPrice,
                                       String subjectReference, String fulfillmentScope,
                                       String requiredConsumerCapability) {
        this.quantity = CrmValidationHelper.positiveDecimal(quantity, "quantity");
        this.unit = CrmValidationHelper.required(unit, "unit");
        this.unitPrice = CrmValidationHelper.requiredObject(unitPrice, "unitPrice");
        this.subjectReference = CrmValidationHelper.required(subjectReference, "subjectReference");
        this.fulfillmentScope = CrmValidationHelper.required(fulfillmentScope, "fulfillmentScope");
        this.requiredConsumerCapability = CrmValidationHelper.required(requiredConsumerCapability,
                "requiredConsumerCapability");
    }


}
