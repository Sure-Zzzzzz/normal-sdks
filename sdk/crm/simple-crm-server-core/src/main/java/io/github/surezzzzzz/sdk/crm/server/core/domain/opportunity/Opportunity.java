package io.github.surezzzzzz.sdk.crm.server.core.domain.opportunity;

import io.github.surezzzzzz.sdk.crm.server.core.error.CrmErrorCode;
import io.github.surezzzzzz.sdk.crm.server.core.error.CrmException;
import io.github.surezzzzzz.sdk.crm.server.core.domain.identity.CrmOwner;
import io.github.surezzzzzz.sdk.crm.server.core.support.CrmValidationHelper;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 销售商机聚合。商机描述客户机会，报价描述冻结的商业承诺。
 *
 * @author surezzzzzz
 */
@Getter
public final class Opportunity {
    private final String opportunityId;
    private final String customerId;
    private final String owner;
    private final String title;
    private final String primaryOfferingId;
    private final String contactId;
    private final LocalDate expectedCloseDate;
    private final BigDecimal estimatedAmount;
    private final String currency;
    private final OpportunityStage stage;
    private final String lostReason;
    private final long aggregateVersion;
    private final Instant createdAt;
    private final Instant updatedAt;

    public Opportunity(String opportunityId, String customerId, String owner, String title,
                       String primaryOfferingId, String contactId, LocalDate expectedCloseDate,
                       BigDecimal estimatedAmount, String currency, OpportunityStage stage,
                       String lostReason, long aggregateVersion, Instant createdAt, Instant updatedAt) {
        this.opportunityId = CrmValidationHelper.required(opportunityId, "opportunityId");
        this.customerId = CrmValidationHelper.required(customerId, "customerId");
        this.owner = CrmValidationHelper.required(owner, "owner");
        this.title = CrmValidationHelper.required(title, "title");
        this.primaryOfferingId = CrmValidationHelper.optional(primaryOfferingId);
        this.contactId = CrmValidationHelper.optional(contactId);
        this.expectedCloseDate = expectedCloseDate;
        String normalizedCurrency = currency == null ? null : CrmValidationHelper.currency(currency, "currency");
        if (estimatedAmount != null && normalizedCurrency == null) {
            throw CrmException.validation("currency");
        }
        this.estimatedAmount = estimatedAmount == null ? null
                : CrmValidationHelper.monetaryAmount(estimatedAmount, normalizedCurrency, "estimatedAmount");
        this.currency = normalizedCurrency;
        this.stage = CrmValidationHelper.requiredObject(stage, "stage");
        this.lostReason = CrmValidationHelper.optional(lostReason);
        if (stage == OpportunityStage.LOST && this.lostReason == null) {
            throw CrmException.validation("lostReason");
        }
        if (stage != OpportunityStage.LOST && this.lostReason != null) {
            throw CrmException.validation("lostReason");
        }
        this.aggregateVersion = CrmValidationHelper.positiveVersion(aggregateVersion, "aggregateVersion");
        this.createdAt = CrmValidationHelper.requiredObject(createdAt, "createdAt");
        this.updatedAt = CrmValidationHelper.requiredObject(updatedAt, "updatedAt");
    }

    public Opportunity progressedTo(OpportunityStage next, String reason, Instant at) {
        CrmValidationHelper.requiredObject(next, "stage");
        CrmValidationHelper.requiredObject(at, "updatedAt");
        if (stage == OpportunityStage.WON || stage == OpportunityStage.LOST) {
            throw new CrmException(CrmErrorCode.INVALID_STATE_TRANSITION, "terminal opportunity cannot transition");
        }
        if (next == stage) {
            return this;
        }
        if (next == OpportunityStage.WON) {
            throw new CrmException(CrmErrorCode.INVALID_STATE_TRANSITION, "won is derived from quotation confirmation");
        }
        if (!isForwardStage(next)) {
            throw new CrmException(CrmErrorCode.INVALID_STATE_TRANSITION, "opportunity stage cannot move backward");
        }
        if (next == OpportunityStage.LOST && (reason == null || reason.trim().isEmpty())) {
            throw CrmException.validation("lostReason");
        }
        return copy(owner, next, next == OpportunityStage.LOST ? reason.trim() : null, aggregateVersion + 1L, at);
    }

    public Opportunity won(Instant at) {
        if (stage == OpportunityStage.WON) {
            return this;
        }
        if (stage == OpportunityStage.LOST) {
            throw new CrmException(CrmErrorCode.INVALID_STATE_TRANSITION, "lost opportunity cannot be won");
        }
        return copy(owner, OpportunityStage.WON, null, aggregateVersion + 1L, at);
    }

    public Opportunity transferredTo(String newOwner, Instant at) {
        if (stage == OpportunityStage.WON || stage == OpportunityStage.LOST) {
            throw new CrmException(CrmErrorCode.INVALID_STATE_TRANSITION,
                    "terminal opportunity cannot transfer");
        }
        return copy(CrmOwner.of(newOwner).getOwner(),
                stage, lostReason,
                aggregateVersion + 1L, CrmValidationHelper.requiredObject(at, "updatedAt"));
    }

    private boolean isForwardStage(OpportunityStage next) {
        if (next == OpportunityStage.LOST) {
            return true;
        }
        switch (stage) {
            case NEW:
                return next == OpportunityStage.QUALIFIED;
            case QUALIFIED:
                return next == OpportunityStage.PROPOSAL;
            case PROPOSAL:
                return next == OpportunityStage.NEGOTIATION;
            case NEGOTIATION:
                return false;
            default:
                return false;
        }
    }

    private Opportunity copy(String newOwner, OpportunityStage newStage, String newLostReason,
                             long newVersion, Instant at) {
        return new Opportunity(opportunityId, customerId, newOwner, title, primaryOfferingId, contactId,
                expectedCloseDate, estimatedAmount, currency, newStage, newLostReason,
                newVersion, createdAt, at);
    }
}
