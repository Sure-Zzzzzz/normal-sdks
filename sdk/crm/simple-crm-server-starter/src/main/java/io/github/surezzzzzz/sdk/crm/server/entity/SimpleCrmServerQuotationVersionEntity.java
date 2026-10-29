package io.github.surezzzzzz.sdk.crm.server.entity;

import io.github.surezzzzzz.sdk.crm.server.core.domain.quotation.QuotationState;
import io.github.surezzzzzz.sdk.crm.server.core.domain.quotation.QuotationVersion;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 报价版本 JDBC 映射实体（行项在 crm_quotation_line 表，由仓储装配）。
 *
 * @author surezzzzzz
 */
@Getter
public final class SimpleCrmServerQuotationVersionEntity {

    private final String quotationId;
    private final int version;
    private final String stateCode;
    private final String settlementCurrency;
    private final Instant validUntil;
    private final BigDecimal totalAmount;
    private final Instant issuedAt;
    private final String issuedBy;
    private final Instant terminalAt;
    private final String terminalBy;
    private final long aggregateVersion;
    private final Instant createdAt;
    private final Instant updatedAt;

    /**
     * 创建SimpleCrmServerQuotationVersionEntity。
     *
     * @param quotationId        报价唯一标识
     * @param version            版本号
     * @param stateCode          状态稳定 code
     * @param settlementCurrency 结算币种
     * @param validUntil         有效截止时间
     * @param totalAmount        总价
     * @param issuedAt           签发时间（可空）
     * @param issuedBy           签发人 owner（可空）
     * @param terminalAt         终态时间（确认/撤回/取代共用槽位，可空）
     * @param terminalBy         终态操作人 owner（可空）
     * @param aggregateVersion   聚合版本
     * @param createdAt          创建时间
     * @param updatedAt          变更时间
     */
    public SimpleCrmServerQuotationVersionEntity(String quotationId, int version, String stateCode, String settlementCurrency,
                                  Instant validUntil, BigDecimal totalAmount, Instant issuedAt, String issuedBy,
                                  Instant terminalAt, String terminalBy, long aggregateVersion, Instant createdAt,
                                  Instant updatedAt) {
        this.quotationId = quotationId;
        this.version = version;
        this.stateCode = stateCode;
        this.settlementCurrency = settlementCurrency;
        this.validUntil = validUntil;
        this.totalAmount = totalAmount;
        this.issuedAt = issuedAt;
        this.issuedBy = issuedBy;
        this.terminalAt = terminalAt;
        this.terminalBy = terminalBy;
        this.aggregateVersion = aggregateVersion;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * 实体状态稳定 code。
     *
     * @return 状态 code
     */
    public QuotationState state() {
        return QuotationState.fromCode(stateCode);
    }
}
