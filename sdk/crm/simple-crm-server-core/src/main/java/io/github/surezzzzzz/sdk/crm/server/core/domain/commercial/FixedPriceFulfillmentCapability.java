package io.github.surezzzzzz.sdk.crm.server.core.domain.commercial;

import io.github.surezzzzzz.sdk.crm.server.core.domain.common.Money;
import io.github.surezzzzzz.sdk.crm.server.core.support.CrmValidationHelper;

/**
 * 固定单价履约能力的首发实现。
 *
 * @author surezzzzzz
 */
public final class FixedPriceFulfillmentCapability implements CommercialCapability {

    /**
     * 获取Type。
     *
     * @return 处理后的领域事实或校验结果。
     *
     */
    @Override
    public CommercialCapabilityType getType() {
        return CommercialCapabilityType.FIXED_PRICE_FULFILLMENT_V1;
    }

    /**
     * 计算冻结商业结果和履约义务。
     *
     * @param request 商业能力计算请求
     * @return 处理后的领域事实或校验结果。
     *
     */
    @Override
    public CommercialCapabilityResult evaluate(CommercialCapabilityRequest request) {
        CrmValidationHelper.requiredObject(request, "request");
        Money lineTotal = request.getUnitPrice().multiply(request.getQuantity());
        FixedPriceCommercialTermsSnapshot terms = new FixedPriceCommercialTermsSnapshot(
                request.getQuantity(), request.getUnit(), request.getUnitPrice());
        FulfillmentObligationTemplate template = new FulfillmentObligationTemplate(
                request.getSubjectReference(), request.getFulfillmentScope(),
                request.getRequiredConsumerCapability());
        return new CommercialCapabilityResult(lineTotal, terms, template);
    }
}
