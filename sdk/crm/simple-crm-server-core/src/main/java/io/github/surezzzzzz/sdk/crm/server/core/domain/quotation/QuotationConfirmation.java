package io.github.surezzzzzz.sdk.crm.server.core.domain.quotation;

import io.github.surezzzzzz.sdk.crm.server.core.domain.fulfillment.FulfillmentItem;
import io.github.surezzzzzz.sdk.crm.server.core.domain.order.Order;
import io.github.surezzzzzz.sdk.crm.server.core.domain.order.OrderLine;
import io.github.surezzzzzz.sdk.crm.server.core.error.CrmException;
import io.github.surezzzzzz.sdk.crm.server.core.support.CrmValidationHelper;
import lombok.Getter;

import java.util.*;

/**
 * 确认报价后应在同一事务内持久化的完整事实集合。
 *
 * @author surezzzzzz
 */
@Getter
public final class QuotationConfirmation {

    private final Quotation quotation;
    private final QuotationVersion quotationVersion;
    private final Order order;
    private final List<FulfillmentItem> fulfillmentItems;

    /**
     * 创建QuotationConfirmation。
     *
     * @param quotation        报价聚合当前事实
     * @param quotationVersion 报价版本事实
     * @param order            由报价确认生成的订单事实
     * @param fulfillmentItems 由报价确认生成的履约项事实集合
     *
     */
    public QuotationConfirmation(Quotation quotation, QuotationVersion quotationVersion, Order order,
                                 List<FulfillmentItem> fulfillmentItems) {
        this.quotation = CrmValidationHelper.requiredObject(quotation, "quotation");
        this.quotationVersion = CrmValidationHelper.requiredObject(quotationVersion, "quotationVersion");
        this.order = CrmValidationHelper.requiredObject(order, "order");
        if (fulfillmentItems == null || fulfillmentItems.isEmpty() || fulfillmentItems.contains(null)) {
            throw CrmException.validation("fulfillmentItems");
        }
        if (!this.quotation.getQuotationId().equals(this.quotationVersion.getQuotationId())
                || !this.quotation.getQuotationId().equals(this.order.getSourceQuotationId())
                || !this.quotation.getTenantId().equals(this.order.getTenantId())
                || this.quotationVersion.getVersion() != this.order.getSourceQuotationVersion()
                || !this.order.getOrderId().equals(this.quotation.getConfirmedOrderId())
                || this.quotation.getCurrentConfirmableVersion() != null
                || this.quotationVersion.getState() != QuotationState.CONFIRMED
                || this.order.getLines().size() != this.quotationVersion.getLines().size()
                || this.order.getLines().size() != fulfillmentItems.size()) {
            throw CrmException.validation("quotationConfirmation");
        }
        Set<String> quotationLineIds = new HashSet<String>();
        for (QuotationLine quotationLine : this.quotationVersion.getLines()) {
            quotationLineIds.add(quotationLine.getQuotationLineId());
        }
        Set<String> orderLineIds = new HashSet<String>();
        Set<String> sourceQuotationLineIds = new HashSet<String>();
        for (OrderLine orderLine : this.order.getLines()) {
            if (!orderLineIds.add(orderLine.getOrderLineId())
                    || !quotationLineIds.contains(orderLine.getSourceQuotationLineId())
                    || !sourceQuotationLineIds.add(orderLine.getSourceQuotationLineId())) {
                throw CrmException.validation("orderLines");
            }
        }
        Set<String> fulfillmentIds = new HashSet<String>();
        Set<String> fulfillmentOrderLineIds = new HashSet<String>();
        for (FulfillmentItem fulfillmentItem : fulfillmentItems) {
            if (!fulfillmentIds.add(fulfillmentItem.getFulfillmentId())
                    || !this.quotation.getTenantId().equals(fulfillmentItem.getTenantId())
                    || !this.order.getOrderId().equals(fulfillmentItem.getOrderId())
                    || !orderLineIds.contains(fulfillmentItem.getOrderLineId())
                    || !fulfillmentOrderLineIds.add(fulfillmentItem.getOrderLineId())) {
                throw CrmException.validation("fulfillmentItems");
            }
        }
        this.fulfillmentItems = Collections.unmodifiableList(new ArrayList<FulfillmentItem>(fulfillmentItems));
    }


}
