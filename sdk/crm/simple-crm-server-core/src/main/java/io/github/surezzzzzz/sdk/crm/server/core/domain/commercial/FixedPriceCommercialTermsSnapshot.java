package io.github.surezzzzzz.sdk.crm.server.core.domain.commercial;

import io.github.surezzzzzz.sdk.crm.server.core.domain.common.Money;
import io.github.surezzzzzz.sdk.crm.server.core.support.CrmValidationHelper;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * 固定单价履约能力生成的冻结商业条款。
 *
 * @author surezzzzzz
 */
@Getter
public final class FixedPriceCommercialTermsSnapshot implements CommercialTermsSnapshot {

    private final BigDecimal quantity;
    private final String unit;
    private final Money unitPrice;

    /**
     * 创建FixedPriceCommercialTermsSnapshot。
     *
     * @param quantity  正数数量
     * @param unit      计量单位
     * @param unitPrice 单价
     *
     */
    public FixedPriceCommercialTermsSnapshot(BigDecimal quantity, String unit, Money unitPrice) {
        this.quantity = CrmValidationHelper.positiveDecimal(quantity, "quantity");
        this.unit = CrmValidationHelper.required(unit, "unit");
        this.unitPrice = CrmValidationHelper.requiredObject(unitPrice, "unitPrice");
    }

    /**
     * 获取CapabilityType。
     *
     * @return 处理后的领域事实或校验结果。
     *
     */
    @Override
    public CommercialCapabilityType getCapabilityType() {
        return CommercialCapabilityType.FIXED_PRICE_FULFILLMENT_V1;
    }


}
