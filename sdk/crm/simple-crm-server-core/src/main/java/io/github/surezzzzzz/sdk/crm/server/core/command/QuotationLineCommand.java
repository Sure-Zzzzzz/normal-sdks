package io.github.surezzzzzz.sdk.crm.server.core.command;

import io.github.surezzzzzz.sdk.crm.server.core.domain.common.Money;
import io.github.surezzzzzz.sdk.crm.server.core.support.CrmValidationHelper;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * 创建报价时的一行商业输入。
 *
 * @author surezzzzzz
 */
@Getter
public final class QuotationLineCommand {

    private final String offeringId;
    private final BigDecimal quantity;
    private final String unit;
    private final Money unitPrice;
    private final String subjectReference;
    private final String fulfillmentScope;

    /**
     * 创建QuotationLineCommand。
     *
     * @param offeringId       商品或服务唯一标识
     * @param quantity         正数数量
     * @param unit             计量单位
     * @param unitPrice        单价
     * @param subjectReference 履约标的引用
     * @param fulfillmentScope 履约范围
     *
     */
    public QuotationLineCommand(String offeringId, BigDecimal quantity, String unit, Money unitPrice,
                                String subjectReference, String fulfillmentScope) {
        this.offeringId = CrmValidationHelper.required(offeringId, "offeringId");
        this.quantity = CrmValidationHelper.positiveDecimal(quantity, "quantity");
        this.unit = CrmValidationHelper.required(unit, "unit");
        this.unitPrice = CrmValidationHelper.requiredObject(unitPrice, "unitPrice");
        this.subjectReference = CrmValidationHelper.required(subjectReference, "subjectReference");
        this.fulfillmentScope = CrmValidationHelper.required(fulfillmentScope, "fulfillmentScope");
    }


}
