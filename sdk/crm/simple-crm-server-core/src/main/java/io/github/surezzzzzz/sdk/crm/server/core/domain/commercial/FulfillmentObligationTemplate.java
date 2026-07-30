package io.github.surezzzzzz.sdk.crm.server.core.domain.commercial;

import io.github.surezzzzzz.sdk.crm.server.core.support.CrmValidationHelper;
import lombok.Getter;

/**
 * 尚未选择消费者的冻结履约义务模板。
 *
 * @author surezzzzzz
 */
@Getter
public final class FulfillmentObligationTemplate {

    private final String subjectReference;
    private final String fulfillmentScope;
    private final String requiredConsumerCapability;

    /**
     * 创建FulfillmentObligationTemplate。
     *
     * @param subjectReference           履约标的引用
     * @param fulfillmentScope           履约范围
     * @param requiredConsumerCapability 消费者必须具备的能力
     *
     */
    public FulfillmentObligationTemplate(String subjectReference, String fulfillmentScope,
                                         String requiredConsumerCapability) {
        this.subjectReference = CrmValidationHelper.required(subjectReference, "subjectReference");
        this.fulfillmentScope = CrmValidationHelper.required(fulfillmentScope, "fulfillmentScope");
        this.requiredConsumerCapability = CrmValidationHelper.required(requiredConsumerCapability,
                "requiredConsumerCapability");
    }


}
