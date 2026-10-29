package io.github.surezzzzzz.sdk.crm.server.entity;

import io.github.surezzzzzz.sdk.crm.server.core.domain.opportunity.Opportunity;
import io.github.surezzzzzz.sdk.crm.server.core.domain.opportunity.OpportunityStage;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** 商机 JDBC 映射实体。 */
@Getter
public final class SimpleCrmServerOpportunityEntity {
    private final String opportunityId;
    private final String customerId;
    private final String owner;
    private final String title;
    private final String primaryOfferingId;
    private final String contactId;
    private final LocalDate expectedCloseDate;
    private final BigDecimal estimatedAmount;
    private final String currency;
    private final String stage;
    private final String lostReason;
    private final long aggregateVersion;
    private final Instant createdAt;
    private final Instant updatedAt;

    public SimpleCrmServerOpportunityEntity(String opportunityId, String customerId, String owner, String title,
                                            String primaryOfferingId, String contactId, LocalDate expectedCloseDate,
                                            BigDecimal estimatedAmount, String currency, String stage, String lostReason,
                                            long aggregateVersion, Instant createdAt, Instant updatedAt) {
        this.opportunityId = opportunityId;
        this.customerId = customerId;
        this.owner = owner;
        this.title = title;
        this.primaryOfferingId = primaryOfferingId;
        this.contactId = contactId;
        this.expectedCloseDate = expectedCloseDate;
        this.estimatedAmount = estimatedAmount;
        this.currency = currency;
        this.stage = stage;
        this.lostReason = lostReason;
        this.aggregateVersion = aggregateVersion;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Opportunity toDomain() {
        return new Opportunity(opportunityId, customerId, owner, title, primaryOfferingId, contactId,
                expectedCloseDate, normalizeAmountScale(estimatedAmount, currency), currency,
                OpportunityStage.fromCode(stage), lostReason, aggregateVersion, createdAt, updatedAt);
    }

    /**
     * 读路径金额 scale 归一：DB DECIMAL(20,6) 读回的 scale（6）大于币种默认小数位时，
     * 写校验（monetaryAmount）会拒绝重建——落库已按写校验归一过，读回按币种归一为合法形态。
     *
     * @param amount DB 读回金额（可空）
     * @param currency 币种（可空）
     * @return 归一后金额
     */
    private static BigDecimal normalizeAmountScale(BigDecimal amount, String currency) {
        if (amount == null || currency == null) {
            return amount;
        }
        try {
            int fractionDigits = java.util.Currency.getInstance(currency).getDefaultFractionDigits();
            if (fractionDigits >= 0 && amount.scale() > fractionDigits) {
                return amount.setScale(fractionDigits, java.math.RoundingMode.UNNECESSARY);
            }
        } catch (IllegalArgumentException ignored) {
            // 未知币种保持原样，交由领域校验给出错误
        }
        return amount;
    }

    public static SimpleCrmServerOpportunityEntity from(Opportunity value) {
        return new SimpleCrmServerOpportunityEntity(value.getOpportunityId(), value.getCustomerId(), value.getOwner(),
                value.getTitle(), value.getPrimaryOfferingId(), value.getContactId(), value.getExpectedCloseDate(),
                value.getEstimatedAmount(), value.getCurrency(), value.getStage().getCode(), value.getLostReason(),
                value.getAggregateVersion(), value.getCreatedAt(), value.getUpdatedAt());
    }
}
