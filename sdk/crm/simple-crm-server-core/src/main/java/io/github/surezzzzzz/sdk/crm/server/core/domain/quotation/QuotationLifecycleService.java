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
     * @param owner     归属主体字符串（iam:/aksk: 前缀）
     * @return 处理后的领域事实或校验结果。
     *
     */
    public QuotationIssuance issue(Quotation quotation, QuotationVersion version, Instant now, String owner) {
        requireSameQuotation(quotation, version);
        CrmValidationHelper.requiredObject(now, "now");
        CrmValidationHelper.required(owner, "owner");
        if (!now.isBefore(version.getValidUntil())) {
            throw new CrmException(CrmErrorCode.QUOTATION_EXPIRED, "quotation has expired");
        }
        return new QuotationIssuance(quotation.issued(version.getVersion(), now), version.issue(now, owner));
    }

    /**
     * 确认报价版本并冻结确认事实。
     *
     * @param quotation        报价聚合当前事实
     * @param version          报价版本事实或版本号
     * @param now              当前权威业务时间
     * @param owner            归属主体字符串（iam:/aksk: 前缀）
     * @param idGenerator      类型化资源标识生成器
     * @param consumerSelector 履约消费者选择器
     * @return 处理后的领域事实或校验结果。
     *
     */
    public QuotationConfirmation confirm(Quotation quotation, QuotationVersion version, Instant now,
                                         String owner, CrmIdGenerator idGenerator,
                                         FulfillmentConsumerSelector consumerSelector) {
        return confirm(quotation, version, now, owner, idGenerator, consumerSelector, null, null);
    }

    /**
     * 确认指定报价并生成订单与履约事实；重议派生的报价确认时同事务将被取代旧报价置为 SUPERSEDED。
     *
     * @param quotation              报价聚合当前事实
     * @param version                报价版本事实或版本号
     * @param now                    当前权威业务时间
     * @param owner                  归属主体字符串（iam:/aksk: 前缀）
     * @param idGenerator            类型化资源标识生成器
     * @param consumerSelector       履约消费者选择器
     * @param supersedeTarget        被取代的旧报价事实（本报价为重议派生时必填，否则必须为 null）
     * @param supersedeTargetVersion 被取代旧报价的版本事实
     * @return 待在同一权威事务持久化的完整确认事实
     */
    public QuotationConfirmation confirm(Quotation quotation, QuotationVersion version, Instant now,
                                         String owner, CrmIdGenerator idGenerator,
                                         FulfillmentConsumerSelector consumerSelector,
                                         Quotation supersedeTarget, QuotationVersion supersedeTargetVersion) {
        requireSameQuotation(quotation, version);
        CrmValidationHelper.requiredObject(now, "now");
        CrmValidationHelper.required(owner, "owner");
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
                    quotationLine.getFulfillmentObligationTemplate()), "consumer");
            if (!quotationLine
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
                    "fulfillmentId"), orderId, orderLineId, 1,
                    FulfillmentState.PENDING_DISPATCH, quotationLine.getFulfillmentObligationTemplate(),
                    consumer.getConsumerId(), consumer.getProtocolVersion(), quotation.getOwner(), 0, null));
        }
        Order order = new Order(orderId, quotation.getQuotationId(), version.getVersion(),
                quotation.getCustomerId(), version.getSettlementCurrency(), version.getTotalAmount(), owner, now,
                1L, OrderDisplayState.PENDING_FULFILLMENT, orderLines);
        Quotation confirmedQuotation = quotation.confirmed(version.getVersion(), orderId, now);
        QuotationVersion confirmedVersion = version.confirm(now, owner);
        Quotation supersededTarget = null;
        QuotationVersion supersededTargetVersion = null;
        if (quotation.getSupersedesQuotationId() != null) {
            if (supersedeTarget == null || supersedeTargetVersion == null
                    || !quotation.getSupersedesQuotationId().equals(supersedeTarget.getQuotationId())
                    || !supersedeTarget.getQuotationId().equals(supersedeTargetVersion.getQuotationId())
                    || supersedeTargetVersion.getState() != QuotationState.ISSUED) {
                throw new CrmException(CrmErrorCode.INVALID_STATE_TRANSITION,
                        "supersede target must be an issued quotation");
            }
            supersededTarget = supersedeTarget.superseded(now);
            supersededTargetVersion = supersedeTargetVersion.superseded(now);
        } else if (supersedeTarget != null || supersedeTargetVersion != null) {
            throw CrmException.validation("supersedeTarget");
        }
        return new QuotationConfirmation(confirmedQuotation, confirmedVersion, order, fulfillmentItems,
                supersededTarget, supersededTargetVersion);
    }

    /**
     * 撤回已签发报价：聚合与版本同事务置 WITHDRAWN，撤回后不可再确认。
     *
     * @param quotation 报价聚合当前事实
     * @param version   报价版本事实或版本号
     * @param now       当前权威业务时间
     * @param owner     撤回人 owner（iam:/aksk: 前缀）
     * @return 撤回后的事实对
     */
    public QuotationWithdrawal withdraw(Quotation quotation, QuotationVersion version, Instant now, String owner) {
        requireSameQuotation(quotation, version);
        CrmValidationHelper.requiredObject(now, "now");
        CrmValidationHelper.required(owner, "owner");
        if (quotation.isConfirmed() || !quotation.isIssued()) {
            throw new CrmException(CrmErrorCode.INVALID_STATE_TRANSITION, "quotation cannot be withdrawn");
        }
        return new QuotationWithdrawal(quotation.withdrawn(now), version.withdrawn(now, owner));
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

    /**
     * 重议派生新草稿：自已签发报价版本复制冻结快照（行/币种/总金额/有效截止时间），新报价记录取代来源；
     * 新报价确认时来源报价自动 SUPERSEDED。源报价可为惰性过期态（落库仍为 ISSUED）。
     *
     * @param sourceQuotation 源报价聚合当前事实（须已签发未确认）
     * @param sourceVersion   源报价当前可确认版本事实
     * @param now             当前权威业务时间
     * @param idGenerator     类型化资源标识生成器
     * @return 以复制快照为起点的新草稿事实对
     */
    public QuotationDraft renegotiate(Quotation sourceQuotation, QuotationVersion sourceVersion, Instant now,
                                      CrmIdGenerator idGenerator) {
        requireSameQuotation(sourceQuotation, sourceVersion);
        CrmValidationHelper.requiredObject(now, "now");
        CrmValidationHelper.requiredObject(idGenerator, "idGenerator");
        if (sourceQuotation.isConfirmed() || !sourceQuotation.isIssued()) {
            throw new CrmException(CrmErrorCode.INVALID_STATE_TRANSITION,
                    "only issued quotation can be renegotiated");
        }
        String newQuotationId = CrmValidationHelper.required(
                idGenerator.nextId(CrmIdResourceType.QUOTATION), "quotationId");
        Quotation newQuotation = new Quotation(newQuotationId, sourceQuotation.getCustomerId(),
                sourceQuotation.getOwner(), 1L, 1, null, null, sourceQuotation.getQuotationId(), now, now);
        // 行快照复制但行 ID 必须换新：本报价的行是自己的事实，沿用源行 ID 会撞全局唯一键
        List<QuotationLine> copiedLines = new ArrayList<QuotationLine>();
        for (QuotationLine sourceLine : sourceVersion.getLines()) {
            copiedLines.add(new QuotationLine(
                    idGenerator.nextId(CrmIdResourceType.QUOTATION_LINE),
                    sourceLine.getOfferingId(), sourceLine.getOfferingReference(),
                    sourceLine.getQuantity(), sourceLine.getUnit(), sourceLine.getUnitPrice(),
                    sourceLine.getLineTotal(), sourceLine.getCommercialTermsSnapshot(),
                    sourceLine.getFulfillmentObligationTemplate()));
        }
        QuotationVersion draftVersion = new QuotationVersion(newQuotationId, 1, QuotationState.DRAFT,
                sourceVersion.getSettlementCurrency(), sourceVersion.getValidUntil(),
                copiedLines, sourceVersion.getTotalAmount(), null, null, null, null);
        return new QuotationDraft(newQuotation, draftVersion);
    }
}
