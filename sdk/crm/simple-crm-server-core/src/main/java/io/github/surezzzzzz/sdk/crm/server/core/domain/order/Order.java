package io.github.surezzzzzz.sdk.crm.server.core.domain.order;

import io.github.surezzzzzz.sdk.crm.server.core.domain.common.Money;
import io.github.surezzzzzz.sdk.crm.server.core.error.CrmException;
import io.github.surezzzzzz.sdk.crm.server.core.support.CrmValidationHelper;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 不可变商业承诺。
 *
 * @author surezzzzzz
 */
@Getter
public final class Order {

    private final String orderId;
    private final String tenantId;
    private final String sourceQuotationId;
    private final int sourceQuotationVersion;
    private final String customerId;
    private final String settlementCurrency;
    private final Money totalAmount;
    private final String confirmedByActorId;
    private final Instant confirmedAt;
    private final long orderVersion;
    private final OrderDisplayState displayState;
    private final List<OrderLine> lines;

    /**
     * 创建Order。
     *
     * @param orderId                订单唯一标识
     * @param tenantId               租户唯一标识
     * @param sourceQuotationId      来源报价唯一标识
     * @param sourceQuotationVersion 来源报价版本号
     * @param customerId             客户唯一标识
     * @param settlementCurrency     结算货币代码
     * @param totalAmount            totalAmount参数。
     * @param confirmedByActorId     confirmedByActorId参数。
     * @param confirmedAt            报价确认时间
     * @param orderVersion           orderVersion参数。
     * @param displayState           订单展示状态
     * @param lines                  冻结行事实集合
     *
     */
    public Order(String orderId, String tenantId, String sourceQuotationId, int sourceQuotationVersion,
                 String customerId, String settlementCurrency, Money totalAmount,
                 String confirmedByActorId, Instant confirmedAt, long orderVersion,
                 OrderDisplayState displayState, List<OrderLine> lines) {
        this.orderId = CrmValidationHelper.required(orderId, "orderId");
        this.tenantId = CrmValidationHelper.required(tenantId, "tenantId");
        this.sourceQuotationId = CrmValidationHelper.required(sourceQuotationId, "sourceQuotationId");
        this.sourceQuotationVersion = CrmValidationHelper.positiveVersion(sourceQuotationVersion,
                "sourceQuotationVersion");
        this.customerId = CrmValidationHelper.required(customerId, "customerId");
        this.settlementCurrency = CrmValidationHelper.currency(settlementCurrency, "settlementCurrency");
        this.totalAmount = CrmValidationHelper.requiredObject(totalAmount, "totalAmount");
        this.confirmedByActorId = CrmValidationHelper.required(confirmedByActorId, "confirmedByActorId");
        this.confirmedAt = CrmValidationHelper.requiredObject(confirmedAt, "confirmedAt");
        this.orderVersion = CrmValidationHelper.positiveVersion(orderVersion, "orderVersion");
        this.displayState = CrmValidationHelper.requiredObject(displayState, "displayState");
        if (lines == null || lines.isEmpty() || lines.contains(null)
                || !this.settlementCurrency.equals(this.totalAmount.getCurrency())) {
            throw CrmException.validation("order");
        }
        this.lines = Collections.unmodifiableList(new ArrayList<OrderLine>(lines));
        Money calculatedTotal = new Money(BigDecimal.ZERO, this.settlementCurrency);
        for (OrderLine line : this.lines) {
            if (!this.settlementCurrency.equals(line.getLineTotal().getCurrency())) {
                throw CrmException.validation("settlementCurrency");
            }
            calculatedTotal = calculatedTotal.add(line.getLineTotal());
        }
        if (calculatedTotal.getAmount().compareTo(this.totalAmount.getAmount()) != 0) {
            throw CrmException.validation("totalAmount");
        }
    }


}
