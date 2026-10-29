package io.github.surezzzzzz.sdk.crm.server.core.domain.quotation;

import io.github.surezzzzzz.sdk.crm.server.core.command.CreateQuotationCommand;
import io.github.surezzzzzz.sdk.crm.server.core.command.EditQuotationCommand;
import io.github.surezzzzzz.sdk.crm.server.core.command.QuotationLineCommand;
import io.github.surezzzzzz.sdk.crm.server.core.domain.commercial.CommercialCapability;
import io.github.surezzzzzz.sdk.crm.server.core.domain.commercial.CommercialCapabilityRegistry;
import io.github.surezzzzzz.sdk.crm.server.core.domain.commercial.CommercialCapabilityRequest;
import io.github.surezzzzzz.sdk.crm.server.core.domain.commercial.CommercialCapabilityResult;
import io.github.surezzzzzz.sdk.crm.server.core.domain.common.Money;
import io.github.surezzzzzz.sdk.crm.server.core.domain.customer.Customer;
import io.github.surezzzzzz.sdk.crm.server.core.domain.customer.CustomerState;
import io.github.surezzzzzz.sdk.crm.server.core.domain.identity.CrmOwner;
import io.github.surezzzzzz.sdk.crm.server.core.domain.offering.Offering;
import io.github.surezzzzzz.sdk.crm.server.core.domain.type.CrmIdResourceType;
import io.github.surezzzzzz.sdk.crm.server.core.error.CrmErrorCode;
import io.github.surezzzzzz.sdk.crm.server.core.error.CrmException;
import io.github.surezzzzzz.sdk.crm.server.core.port.system.CrmIdGenerator;
import io.github.surezzzzzz.sdk.crm.server.core.support.CrmValidationHelper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 报价草稿的纯领域构造服务。
 *
 * @author surezzzzzz
 */
public final class QuotationDraftService {

    private final CommercialCapabilityRegistry capabilityRegistry;

    /**
     * 创建QuotationDraftService。
     *
     * @param capabilityRegistry 已注册商业能力注册表
     *
     */
    public QuotationDraftService(CommercialCapabilityRegistry capabilityRegistry) {
        this.capabilityRegistry = CrmValidationHelper.requiredObject(capabilityRegistry, "capabilityRegistry");
    }

    /**
     * 构造报价草稿领域事实。
     *
     * @param actor       归属主体（人 iam:/服务 aksk:）
     * @param customer    已按租户边界加载的客户事实
     * @param command     传输无关的业务命令
     * @param offerings   已按租户边界加载的商品或服务事实集合
     * @param now         当前权威业务时间
     * @param idGenerator 类型化资源标识生成器
     * @return 处理后的领域事实或校验结果。
     *
     */
    public QuotationDraft create(CrmOwner actor, Customer customer, CreateQuotationCommand command,
                                 List<Offering> offerings, Instant now, CrmIdGenerator idGenerator) {
        CrmOwner requiredActor = CrmValidationHelper.requiredObject(actor, "actor");
        Customer requiredCustomer = CrmValidationHelper.requiredObject(customer, "customer");
        CreateQuotationCommand requiredCommand = CrmValidationHelper.requiredObject(command, "command");
        Map<String, Offering> offeringsById = offeringsById(offerings);
        Instant createdAt = CrmValidationHelper.requiredObject(now, "now");
        CrmValidationHelper.requiredObject(idGenerator, "idGenerator");
        validateCustomer(requiredActor, requiredCustomer, requiredCommand);
        if (!createdAt.isBefore(requiredCommand.getValidUntil())) {
            throw CrmException.validation("validUntil");
        }

        String quotationId = nextId(idGenerator, CrmIdResourceType.QUOTATION, "quotationId");
        CalculatedLines calculated = calculateLines(requiredCommand.getLines(), offeringsById, idGenerator);
        Quotation quotation = new Quotation(quotationId, requiredCustomer.getCustomerId(),
                requiredCommand.getOpportunityId(), requiredActor.getOwner(), 1L, 1, null, null, null, createdAt, createdAt);
        QuotationVersion quotationVersion = new QuotationVersion(quotationId, 1, QuotationState.DRAFT,
                calculated.settlementCurrency, requiredCommand.getValidUntil(), calculated.lines,
                calculated.totalAmount, null, null, null, null);
        return new QuotationDraft(quotation, quotationVersion);
    }

    /**
     * 编辑报价草稿：仅当前草稿版本可整单替换行项与有效截止时间，版本号不变、聚合版本递增。
     *
     * @param quotation    报价聚合当前事实
     * @param draftVersion 当前草稿版本事实
     * @param command      传输无关的编辑命令（整单行项替换）
     * @param customer     已加载的客户事实
     * @param offerings    已加载的商品或服务事实集合
     * @param now          当前权威业务时间
     * @param idGenerator  类型化资源标识生成器
     * @return 编辑后的草稿事实对
     */
    public QuotationDraft edit(Quotation quotation, QuotationVersion draftVersion, EditQuotationCommand command,
                               Customer customer, List<Offering> offerings, Instant now,
                               CrmIdGenerator idGenerator) {
        Quotation requiredQuotation = CrmValidationHelper.requiredObject(quotation, "quotation");
        QuotationVersion requiredDraft = CrmValidationHelper.requiredObject(draftVersion, "draftVersion");
        EditQuotationCommand requiredCommand = CrmValidationHelper.requiredObject(command, "command");
        Customer requiredCustomer = CrmValidationHelper.requiredObject(customer, "customer");
        Instant updatedAt = CrmValidationHelper.requiredObject(now, "now");
        CrmValidationHelper.requiredObject(idGenerator, "idGenerator");
        if (requiredQuotation.isConfirmed() || requiredDraft.getState() != QuotationState.DRAFT
                || !requiredQuotation.getQuotationId().equals(requiredDraft.getQuotationId())
                || requiredDraft.getVersion() != requiredQuotation.getCurrentVersion()) {
            throw new CrmException(CrmErrorCode.INVALID_STATE_TRANSITION, "only current draft can be edited");
        }
        if (requiredCustomer.getState() != CustomerState.ACTIVE) {
            throw new CrmException(CrmErrorCode.INVALID_STATE_TRANSITION, "customer is not active");
        }
        if (!updatedAt.isBefore(requiredCommand.getValidUntil())) {
            throw CrmException.validation("validUntil");
        }
        CalculatedLines calculated = calculateLines(requiredCommand.getLines(), offeringsById(offerings),
                idGenerator);
        Quotation editedQuotation = new Quotation(requiredQuotation.getQuotationId(),
                requiredQuotation.getCustomerId(), requiredQuotation.getOpportunityId(), requiredQuotation.getOwner(),
                requiredQuotation.getAggregateVersion() + 1L, requiredQuotation.getCurrentVersion(),
                null, null, requiredQuotation.getSupersedesQuotationId(),
                requiredQuotation.getCreatedAt(), updatedAt);
        QuotationVersion editedVersion = new QuotationVersion(requiredQuotation.getQuotationId(),
                requiredDraft.getVersion(), QuotationState.DRAFT, calculated.settlementCurrency,
                requiredCommand.getValidUntil(), calculated.lines, calculated.totalAmount, null, null, null, null);
        return new QuotationDraft(editedQuotation, editedVersion);
    }

    /**
     * 按行命令集合重算冻结行、结算币种与总金额。
     */
    private CalculatedLines calculateLines(List<QuotationLineCommand> lineCommands,
                                           Map<String, Offering> offeringsById, CrmIdGenerator idGenerator) {
        if (lineCommands == null || lineCommands.isEmpty() || lineCommands.contains(null)) {
            throw CrmException.validation("lines");
        }
        List<QuotationLine> lines = new ArrayList<QuotationLine>();
        String settlementCurrency = null;
        Money totalAmount = null;
        for (QuotationLineCommand lineCommand : lineCommands) {
            Offering offering = CrmValidationHelper.requiredObject(offeringsById.get(lineCommand.getOfferingId()),
                    "offering");
            validateOffering(null, offering, lineCommand);
            CommercialCapability capability = capabilityRegistry.getRequired(offering.getCapabilityType());
            CommercialCapabilityResult result = capability.evaluate(new CommercialCapabilityRequest(
                    lineCommand.getQuantity(), lineCommand.getUnit(), lineCommand.getUnitPrice(),
                    lineCommand.getSubjectReference(), lineCommand.getFulfillmentScope(),
                    offering.getRequiredConsumerCapability()));
            if (result.getCommercialTermsSnapshot().getCapabilityType() != offering.getCapabilityType()) {
                throw new CrmException(CrmErrorCode.INVALID_STATE_TRANSITION,
                        "commercial capability result type is inconsistent");
            }
            String lineCurrency = result.getLineTotal().getCurrency();
            if (settlementCurrency == null) {
                settlementCurrency = lineCurrency;
                totalAmount = new Money(BigDecimal.ZERO, settlementCurrency);
            } else if (!settlementCurrency.equals(lineCurrency)) {
                throw CrmException.validation("settlementCurrency");
            }
            totalAmount = totalAmount.add(result.getLineTotal());
            lines.add(new QuotationLine(nextId(idGenerator, CrmIdResourceType.QUOTATION_LINE, "quotationLineId"),
                    offering.getOfferingId(), offering.getOfferingReference(), lineCommand.getQuantity(),
                    lineCommand.getUnit(), lineCommand.getUnitPrice(), result.getLineTotal(),
                    result.getCommercialTermsSnapshot(), result.getFulfillmentObligationTemplate()));
        }
        return new CalculatedLines(settlementCurrency, totalAmount, lines);
    }

    private Map<String, Offering> offeringsById(List<Offering> offerings) {
        if (offerings == null || offerings.isEmpty() || offerings.contains(null)) {
            throw CrmException.validation("offerings");
        }
        Map<String, Offering> offeringsById = new HashMap<String, Offering>();
        for (Offering offering : offerings) {
            if (offeringsById.put(offering.getOfferingId(), offering) != null) {
                throw CrmException.validation("offerings");
            }
        }
        return offeringsById;
    }

    private void validateCustomer(CrmOwner actor, Customer customer, CreateQuotationCommand command) {
        if (customer.getState() != CustomerState.ACTIVE) {
            throw new CrmException(CrmErrorCode.INVALID_STATE_TRANSITION, "customer is not active");
        }
    }

    private void validateOffering(CrmOwner actor, Offering offering, QuotationLineCommand lineCommand) {
        if (!offering.isPublished()) {
            throw new CrmException(CrmErrorCode.INVALID_STATE_TRANSITION, "offering is not published");
        }
    }

    private String nextId(CrmIdGenerator idGenerator, CrmIdResourceType resourceType, String field) {
        return CrmValidationHelper.required(idGenerator.nextId(resourceType), field);
    }

    /**
     * 行重算结果载体（内部复用）。
     */
    private static final class CalculatedLines {
        private final String settlementCurrency;
        private final Money totalAmount;
        private final List<QuotationLine> lines;

        private CalculatedLines(String settlementCurrency, Money totalAmount, List<QuotationLine> lines) {
            this.settlementCurrency = settlementCurrency;
            this.totalAmount = totalAmount;
            this.lines = lines;
        }
    }
}
