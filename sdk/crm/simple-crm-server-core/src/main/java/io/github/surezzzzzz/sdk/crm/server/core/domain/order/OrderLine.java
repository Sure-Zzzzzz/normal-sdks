package io.github.surezzzzzz.sdk.crm.server.core.domain.order;

import io.github.surezzzzzz.sdk.crm.server.core.domain.commercial.CommercialTermsSnapshot;
import io.github.surezzzzzz.sdk.crm.server.core.domain.commercial.FixedPriceCommercialTermsSnapshot;
import io.github.surezzzzzz.sdk.crm.server.core.domain.commercial.FulfillmentObligationTemplate;
import io.github.surezzzzzz.sdk.crm.server.core.domain.common.Money;
import io.github.surezzzzzz.sdk.crm.server.core.error.CrmException;
import io.github.surezzzzzz.sdk.crm.server.core.support.CrmValidationHelper;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * 订单中的不可变商业承诺行。
 *
 * @author surezzzzzz
 */
@Getter
public final class OrderLine {

    private final String orderLineId;
    private final String sourceQuotationLineId;
    private final String offeringReference;
    private final BigDecimal quantity;
    private final String unit;
    private final Money unitPrice;
    private final Money lineTotal;
    private final CommercialTermsSnapshot commercialTermsSnapshot;
    private final FulfillmentObligationTemplate fulfillmentObligationTemplate;

    /**
     * 创建OrderLine。
     *
     * @param orderLineId                   订单行唯一标识
     * @param sourceQuotationLineId         来源报价行唯一标识
     * @param offeringReference             商品或服务业务引用
     * @param quantity                      正数数量
     * @param unit                          计量单位
     * @param unitPrice                     单价
     * @param lineTotal                     行金额
     * @param commercialTermsSnapshot       冻结商业条款快照
     * @param fulfillmentObligationTemplate 冻结履约义务模板
     *
     */
    public OrderLine(String orderLineId, String sourceQuotationLineId, String offeringReference,
                     BigDecimal quantity, String unit, Money unitPrice, Money lineTotal,
                     CommercialTermsSnapshot commercialTermsSnapshot,
                     FulfillmentObligationTemplate fulfillmentObligationTemplate) {
        this.orderLineId = CrmValidationHelper.required(orderLineId, "orderLineId");
        this.sourceQuotationLineId = CrmValidationHelper.required(sourceQuotationLineId, "sourceQuotationLineId");
        this.offeringReference = CrmValidationHelper.required(offeringReference, "offeringReference");
        this.quantity = CrmValidationHelper.positiveDecimal(quantity, "quantity");
        this.unit = CrmValidationHelper.required(unit, "unit");
        this.unitPrice = CrmValidationHelper.requiredObject(unitPrice, "unitPrice");
        this.lineTotal = CrmValidationHelper.requiredObject(lineTotal, "lineTotal");
        if (!this.unitPrice.getCurrency().equals(this.lineTotal.getCurrency())) {
            throw CrmException.validation("currency");
        }
        this.commercialTermsSnapshot = CrmValidationHelper.requiredObject(commercialTermsSnapshot,
                "commercialTermsSnapshot");
        this.fulfillmentObligationTemplate = CrmValidationHelper.requiredObject(fulfillmentObligationTemplate,
                "fulfillmentObligationTemplate");
        validateFixedPriceSnapshot();
    }


    private void validateFixedPriceSnapshot() {
        if (commercialTermsSnapshot instanceof FixedPriceCommercialTermsSnapshot) {
            FixedPriceCommercialTermsSnapshot fixedPrice = (FixedPriceCommercialTermsSnapshot) commercialTermsSnapshot;
            if (quantity.compareTo(fixedPrice.getQuantity()) != 0 || !unit.equals(fixedPrice.getUnit())
                    || unitPrice.getAmount().compareTo(fixedPrice.getUnitPrice().getAmount()) != 0
                    || !unitPrice.getCurrency().equals(fixedPrice.getUnitPrice().getCurrency())
                    || lineTotal.getAmount().compareTo(unitPrice.multiply(quantity).getAmount()) != 0) {
                throw CrmException.validation("commercialTermsSnapshot");
            }
        }
    }
}
