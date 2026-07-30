package io.github.surezzzzzz.sdk.crm.server.core.domain.quotation;

import io.github.surezzzzzz.sdk.crm.server.core.command.CreateQuotationCommand;
import io.github.surezzzzzz.sdk.crm.server.core.command.QuotationLineCommand;
import io.github.surezzzzzz.sdk.crm.server.core.domain.commercial.CommercialCapability;
import io.github.surezzzzzz.sdk.crm.server.core.domain.commercial.CommercialCapabilityRegistry;
import io.github.surezzzzzz.sdk.crm.server.core.domain.commercial.CommercialCapabilityRequest;
import io.github.surezzzzzz.sdk.crm.server.core.domain.commercial.CommercialCapabilityResult;
import io.github.surezzzzzz.sdk.crm.server.core.domain.common.Money;
import io.github.surezzzzzz.sdk.crm.server.core.domain.customer.Customer;
import io.github.surezzzzzz.sdk.crm.server.core.domain.customer.CustomerState;
import io.github.surezzzzzz.sdk.crm.server.core.domain.identity.CrmActor;
import io.github.surezzzzzz.sdk.crm.server.core.domain.offering.Offering;
import io.github.surezzzzzz.sdk.crm.server.core.domain.offering.OfferingState;
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
     * @param actor       已认证且绑定租户的操作者
     * @param customer    已按租户边界加载的客户事实
     * @param command     传输无关的业务命令
     * @param offerings   已按租户边界加载的商品或服务事实集合
     * @param now         当前权威业务时间
     * @param idGenerator 类型化资源标识生成器
     * @return 处理后的领域事实或校验结果。
     *
     */
    public QuotationDraft create(CrmActor actor, Customer customer, CreateQuotationCommand command,
                                 List<Offering> offerings, Instant now, CrmIdGenerator idGenerator) {
        CrmActor requiredActor = CrmValidationHelper.requiredObject(actor, "actor");
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
        List<QuotationLine> lines = new ArrayList<QuotationLine>();
        String settlementCurrency = null;
        Money totalAmount = null;
        for (QuotationLineCommand lineCommand : requiredCommand.getLines()) {
            Offering offering = CrmValidationHelper.requiredObject(offeringsById.get(lineCommand.getOfferingId()),
                    "offering");
            validateOffering(requiredActor, offering, lineCommand);
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
        Quotation quotation = new Quotation(quotationId, requiredActor.getTenantId(), requiredCustomer.getCustomerId(),
                requiredActor.getActorId(), 1L, 1, null, null, createdAt, createdAt);
        QuotationVersion quotationVersion = new QuotationVersion(quotationId, 1, QuotationState.DRAFT,
                settlementCurrency, requiredCommand.getValidUntil(), lines, totalAmount, null, null, null, null);
        return new QuotationDraft(quotation, quotationVersion);
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

    private void validateCustomer(CrmActor actor, Customer customer, CreateQuotationCommand command) {
        if (!actor.getTenantId().equals(customer.getTenantId())
                || !customer.getCustomerId().equals(command.getCustomerId())) {
            throw new CrmException(CrmErrorCode.TENANT_MISMATCH, "customer is outside actor tenant");
        }
        if (customer.getState() != CustomerState.ACTIVE) {
            throw new CrmException(CrmErrorCode.INVALID_STATE_TRANSITION, "customer is not active");
        }
    }

    private void validateOffering(CrmActor actor, Offering offering, QuotationLineCommand lineCommand) {
        if (!actor.getTenantId().equals(offering.getTenantId())
                || !offering.getOfferingId().equals(lineCommand.getOfferingId())) {
            throw new CrmException(CrmErrorCode.TENANT_MISMATCH, "offering is outside actor tenant");
        }
        if (offering.getState() != OfferingState.ACTIVE) {
            throw new CrmException(CrmErrorCode.INVALID_STATE_TRANSITION, "offering is not active");
        }
    }

    private String nextId(CrmIdGenerator idGenerator, CrmIdResourceType resourceType, String field) {
        return CrmValidationHelper.required(idGenerator.nextId(resourceType), field);
    }
}
