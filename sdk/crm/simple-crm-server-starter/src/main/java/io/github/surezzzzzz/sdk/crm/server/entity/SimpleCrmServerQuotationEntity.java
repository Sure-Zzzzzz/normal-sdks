package io.github.surezzzzzz.sdk.crm.server.entity;

import io.github.surezzzzzz.sdk.crm.server.core.domain.quotation.Quotation;
import lombok.Getter;

import java.time.Instant;

/**
 * 报价聚合 JDBC 映射实体。
 *
 * @author surezzzzzz
 */
@Getter
public final class SimpleCrmServerQuotationEntity {

    private final String quotationId;
    private final String customerId;
    private final String opportunityId;
    private final String owner;
    private final long aggregateVersion;
    private final int currentVersion;
    private final Integer currentConfirmableVersion;
    private final String confirmedOrderId;
    private final String supersedesQuotationId;
    private final Instant createdAt;
    private final Instant updatedAt;

    /**
     * 创建SimpleCrmServerQuotationEntity。
     *
     * @param quotationId              报价唯一标识
     * @param customerId               客户唯一标识
     * @param owner                    归属主体（iam:/aksk: 前缀）
     * @param aggregateVersion         聚合版本
     * @param currentVersion           当前版本号
     * @param currentConfirmableVersion 当前可确认版本号（可空）
     * @param confirmedOrderId         确认订单标识（可空）
     * @param supersedesQuotationId    重议来源报价标识（可空）
     * @param createdAt                创建时间
     * @param updatedAt                变更时间
     */
    public SimpleCrmServerQuotationEntity(String quotationId, String customerId, String owner, long aggregateVersion,
                           int currentVersion, Integer currentConfirmableVersion, String confirmedOrderId,
                           String supersedesQuotationId, Instant createdAt, Instant updatedAt) {
        this(quotationId, customerId, null, owner, aggregateVersion, currentVersion,
                currentConfirmableVersion, confirmedOrderId, supersedesQuotationId, createdAt, updatedAt);
    }

    public SimpleCrmServerQuotationEntity(String quotationId, String customerId, String opportunityId, String owner, long aggregateVersion,
                           int currentVersion, Integer currentConfirmableVersion, String confirmedOrderId,
                           String supersedesQuotationId, Instant createdAt, Instant updatedAt) {
        this.quotationId = quotationId;
        this.customerId = customerId;
        this.opportunityId = opportunityId;
        this.owner = owner;
        this.aggregateVersion = aggregateVersion;
        this.currentVersion = currentVersion;
        this.currentConfirmableVersion = currentConfirmableVersion;
        this.confirmedOrderId = confirmedOrderId;
        this.supersedesQuotationId = supersedesQuotationId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * 实体转领域事实。
     *
     * @return 报价聚合领域事实
     */
    public Quotation toDomain() {
        return new Quotation(quotationId, customerId, opportunityId, owner, aggregateVersion, currentVersion,
                currentConfirmableVersion, confirmedOrderId, supersedesQuotationId, createdAt, updatedAt);
    }

    /**
     * 领域事实转实体。
     *
     * @param quotation 报价聚合领域事实
     * @return JDBC 映射实体
     */
    public static SimpleCrmServerQuotationEntity from(Quotation quotation) {
        return new SimpleCrmServerQuotationEntity(quotation.getQuotationId(), quotation.getCustomerId(), quotation.getOpportunityId(),
                quotation.getOwner(), quotation.getAggregateVersion(), quotation.getCurrentVersion(),
                quotation.getCurrentConfirmableVersion(), quotation.getConfirmedOrderId(),
                quotation.getSupersedesQuotationId(), quotation.getCreatedAt(), quotation.getUpdatedAt());
    }
}
