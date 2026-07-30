package io.github.surezzzzzz.sdk.crm.server.core.domain.commercial;

import io.github.surezzzzzz.sdk.crm.server.core.domain.common.Money;
import io.github.surezzzzzz.sdk.crm.server.core.support.CrmValidationHelper;
import lombok.Getter;

/**
 * 商业能力计算出的冻结价格和履约义务。
 *
 * @author surezzzzzz
 */
@Getter
public final class CommercialCapabilityResult {

    private final Money lineTotal;
    private final CommercialTermsSnapshot commercialTermsSnapshot;
    private final FulfillmentObligationTemplate fulfillmentObligationTemplate;

    /**
     * 创建CommercialCapabilityResult。
     *
     * @param lineTotal                     行金额
     * @param commercialTermsSnapshot       冻结商业条款快照
     * @param fulfillmentObligationTemplate 冻结履约义务模板
     *
     */
    public CommercialCapabilityResult(Money lineTotal, CommercialTermsSnapshot commercialTermsSnapshot,
                                      FulfillmentObligationTemplate fulfillmentObligationTemplate) {
        this.lineTotal = CrmValidationHelper.requiredObject(lineTotal, "lineTotal");
        this.commercialTermsSnapshot = CrmValidationHelper.requiredObject(commercialTermsSnapshot,
                "commercialTermsSnapshot");
        this.fulfillmentObligationTemplate = CrmValidationHelper.requiredObject(fulfillmentObligationTemplate,
                "fulfillmentObligationTemplate");
    }


}
