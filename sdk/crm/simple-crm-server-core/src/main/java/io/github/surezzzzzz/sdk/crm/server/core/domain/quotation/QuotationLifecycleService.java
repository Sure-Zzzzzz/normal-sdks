package io.github.surezzzzzz.sdk.crm.server.core.domain.quotation;

import io.github.surezzzzzz.sdk.crm.server.core.domain.fulfillment.FulfillmentConsumer;
import io.github.surezzzzzz.sdk.crm.server.core.domain.fulfillment.FulfillmentItem;
import io.github.surezzzzzz.sdk.crm.server.core.domain.fulfillment.FulfillmentState;
import io.github.surezzzzzz.sdk.crm.server.core.domain.order.Order;
import io.github.surezzzzzz.sdk.crm.server.core.domain.order.OrderDisplayState;
import io.github.surezzzzzz.sdk.crm.server.core.domain.order.OrderLine;
import io.github.surezzzzzz.sdk.crm.server.core.domain.type.CrmIdResourceType;
import io.github.surezzzzzz.sdk.crm.server.core.error.CrmErrorCode;
import io.github.surezzzzzz.sdk.crm.server.core.error.CrmException;
import io.github.surezzzzzz.sdk.crm.server.core.port.system.CrmIdGenerator;
import io.github.surezzzzzz.sdk.crm.server.core.port.system.FulfillmentConsumerSelector;
import io.github.surezzzzzz.sdk.crm.server.core.support.CrmValidationHelper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 报价签发和确认的纯领域状态服务。
 *
 * @author surezzzzzz
 */
public final class QuotationLifecycleService {

    /**
     * 签发报价版本并冻结签发事实。
     *
     * @param quotation 报价聚合当前事实
     * @param version   报价版本事实或版本号
     * @param now       当前权威业务时间
     * @param actorId   执行操作的操作者标识
     * @return 处理后的领域事实或校验结果。
     *
     */
    public QuotationIssuance issue(Quotation quotation, QuotationVersion version, Instant now, String actorId) {
        requireSameQuotation(quotation, version);
        CrmValidationHelper.requiredObject(now, "now");
        CrmValidationHelper.required(actorId, "actorId");
        if (!now.isBefore(version.getValidUntil())) {
            throw new CrmException(CrmErrorCode.QUOTATION_EXPIRED, "quotation has expired");
        }
        return new QuotationIssuance(quotation.issued(version.getVersion(), now), version.issue(now, actorId));
    }

    /**
     * 确认报价版本并冻结确认事实。
     *
     * @param quotation        报价聚合当前事实
     * @param version          报价版本事实或版本号
     * @param now              当前权威业务时间
     * @param actorId          执行操作的操作者标识
     * @param idGenerator      类型化资源标识生成器
     * @param consumerSelector 履约消费者选择器
     * @return 处理后的领域事实或校验结果。
     *
     */
    public QuotationConfirmation confirm(Quotation quotation, QuotationVersion version, Instant now,
                                         String actorId, CrmIdGenerator idGenerator,
                                         FulfillmentConsumerSelector consumerSelector) {
        requireSameQuotation(quotation, version);
        CrmValidationHelper.requiredObject(now, "now");
        CrmValidationHelper.required(actorId, "actorId");
        CrmValidationHelper.requiredObject(idGenerator, "idGenerator");
        CrmValidationHelper.requiredObject(consumerSelector, "consumerSelector");
        if (quotation.isConfirmed()) {
            throw new CrmException(CrmErrorCode.QUOTATION_ALREADY_CONFIRMED, "quotation already confirmed");
        }
        if (!now.isBefore(version.getValidUntil())) {
            throw new CrmException(CrmErrorCode.QUOTATION_EXPIRED, "quotation has expired");
        }
        if (!quotation.canConfirm(version, now)) {
            throw new CrmException(CrmErrorCode.INVALID_STATE_TRANSITION, "quotation cannot be confirmed");
        }
        List<OrderLine> orderLines = new ArrayList<OrderLine>();
        List<FulfillmentItem> fulfillmentItems = new ArrayList<FulfillmentItem>();
        String orderId = nextId(idGenerator, CrmIdResourceType.ORDER, "orderId");
        for (QuotationLine quotationLine : version.getLines()) {
            String orderLineId = nextId(idGenerator, CrmIdResourceType.ORDER_LINE, "orderLineId");
            FulfillmentConsumer consumer = CrmValidationHelper.requiredObject(consumerSelector.select(
                    quotation.getTenantId(), quotationLine.getFulfillmentObligationTemplate()), "consumer");
            if (!quotation.getTenantId().equals(consumer.getTenantId()) || !quotationLine
                    .getFulfillmentObligationTemplate().getRequiredConsumerCapability()
                    .equals(consumer.getCapability())) {
                throw new CrmException(CrmErrorCode.INVALID_STATE_TRANSITION,
                        "selected consumer does not satisfy fulfillment obligation");
            }
            OrderLine orderLine = new OrderLine(orderLineId, quotationLine.getQuotationLineId(),
                    quotationLine.getOfferingReference(), quotationLine.getQuantity(), quotationLine.getUnit(),
                    quotationLine.getUnitPrice(), quotationLine.getLineTotal(),
                    quotationLine.getCommercialTermsSnapshot(), quotationLine.getFulfillmentObligationTemplate());
            orderLines.add(orderLine);
            fulfillmentItems.add(new FulfillmentItem(nextId(idGenerator, CrmIdResourceType.FULFILLMENT_ITEM,
                    "fulfillmentId"), quotation.getTenantId(), orderId, orderLineId, 1,
                    FulfillmentState.PENDING_DISPATCH, quotationLine.getFulfillmentObligationTemplate(),
                    consumer.getConsumerId(), consumer.getProtocolVersion()));
        }
        Order order = new Order(orderId, quotation.getTenantId(), quotation.getQuotationId(), version.getVersion(),
                quotation.getCustomerId(), version.getSettlementCurrency(), version.getTotalAmount(), actorId, now,
                1L, OrderDisplayState.PENDING_FULFILLMENT, orderLines);
        Quotation confirmedQuotation = quotation.confirmed(version.getVersion(), orderId, now);
        QuotationVersion confirmedVersion = version.confirm(now, actorId);
        return new QuotationConfirmation(confirmedQuotation, confirmedVersion, order, fulfillmentItems);
    }

    private String nextId(CrmIdGenerator idGenerator, CrmIdResourceType resourceType, String field) {
        return CrmValidationHelper.required(idGenerator.nextId(resourceType), field);
    }

    private void requireSameQuotation(Quotation quotation, QuotationVersion version) {
        CrmValidationHelper.requiredObject(quotation, "quotation");
        CrmValidationHelper.requiredObject(version, "quotationVersion");
        if (!quotation.getQuotationId().equals(version.getQuotationId())) {
            throw CrmException.validation("quotationVersion.quotationId");
        }
    }
}
