package io.github.surezzzzzz.sdk.crm.server.core.domain.quotation;

import io.github.surezzzzzz.sdk.crm.server.core.domain.common.Money;
import io.github.surezzzzzz.sdk.crm.server.core.error.CrmErrorCode;
import io.github.surezzzzzz.sdk.crm.server.core.error.CrmException;
import io.github.surezzzzzz.sdk.crm.server.core.support.CrmValidationHelper;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/**
 * 不可变的报价商业版本事实。
 *
 * @author surezzzzzz
 */
@Getter
public final class QuotationVersion {

    private final String quotationId;
    private final int version;
    private final QuotationState state;
    private final String settlementCurrency;
    private final Instant validUntil;
    private final List<QuotationLine> lines;
    private final Money totalAmount;
    private final Instant issuedAt;
    private final String issuedBy;
    private final Instant terminalAt;
    private final String terminalBy;

    /**
     * 创建QuotationVersion。
     *
     * @param quotationId        报价唯一标识
     * @param version            报价版本事实或版本号
     * @param state              业务状态
     * @param settlementCurrency 结算货币代码
     * @param validUntil         报价有效截止时间
     * @param lines              冻结行事实集合
     * @param totalAmount        totalAmount参数。
     * @param issuedAt           报价签发时间
     * @param issuedBy           签发人 owner（iam:/aksk: 前缀）
     * @param terminalAt         终态时间（确认/撤回/取代共用槽位）
     * @param terminalBy         终态操作人 owner（确认/撤回/取代共用槽位）
     *
     */
    public QuotationVersion(String quotationId, int version, QuotationState state, String settlementCurrency,
                            Instant validUntil, List<QuotationLine> lines, Money totalAmount,
                            Instant issuedAt, String issuedBy, Instant terminalAt,
                            String terminalBy) {
        this.quotationId = CrmValidationHelper.required(quotationId, "quotationId");
        this.version = CrmValidationHelper.positiveVersion(version, "quotationVersion");
        this.state = CrmValidationHelper.requiredObject(state, "state");
        this.settlementCurrency = CrmValidationHelper.currency(settlementCurrency, "settlementCurrency");
        this.validUntil = CrmValidationHelper.requiredObject(validUntil, "validUntil");
        if (lines == null || lines.isEmpty() || lines.contains(null) || totalAmount == null) {
            throw CrmException.validation("lines/totalAmount");
        }
        this.lines = Collections.unmodifiableList(new ArrayList<QuotationLine>(lines));
        Money calculatedTotal = new Money(BigDecimal.ZERO, this.settlementCurrency);
        Set<String> quotationLineIds = new HashSet<String>();
        for (QuotationLine line : this.lines) {
            if (!quotationLineIds.add(line.getQuotationLineId())
                    || !this.settlementCurrency.equals(line.getLineTotal().getCurrency())) {
                throw CrmException.validation("quotationLines");
            }
            calculatedTotal = calculatedTotal.add(line.getLineTotal());
        }
        if (!this.settlementCurrency.equals(totalAmount.getCurrency())
                || calculatedTotal.getAmount().compareTo(totalAmount.getAmount()) != 0) {
            throw CrmException.validation("totalAmount");
        }
        this.totalAmount = totalAmount;
        this.issuedAt = issuedAt;
        this.issuedBy = issuedBy == null ? null
                : CrmValidationHelper.required(issuedBy, "issuedBy");
        this.terminalAt = terminalAt;
        this.terminalBy = terminalBy == null ? null
                : CrmValidationHelper.required(terminalBy, "terminalBy");
        validateLifecycleFields();
    }


    /**
     * 签发报价版本并冻结签发事实。
     *
     * @param issuedAt 报价签发时间
     * @param owner    已验证的签发主体归属
     * @return 处理后的领域事实或校验结果。
     *
     */
    public QuotationVersion issue(Instant issuedAt, String owner) {
        if (state != QuotationState.DRAFT) {
            throw new CrmException(CrmErrorCode.INVALID_STATE_TRANSITION,
                    "quotation version is not draft");
        }
        Instant requiredIssuedAt = CrmValidationHelper.requiredObject(issuedAt, "issuedAt");
        if (!requiredIssuedAt.isBefore(validUntil)) {
            throw new CrmException(CrmErrorCode.QUOTATION_EXPIRED, "quotation has expired");
        }
        return new QuotationVersion(quotationId, version, QuotationState.ISSUED, settlementCurrency, validUntil,
                lines, totalAmount, requiredIssuedAt, owner, null, null);
    }

    /**
     * 惰性过期判定：已签发且当前时间不早于有效截止时间（不落库，由读取侧判定）。
     *
     * @param now 当前权威业务时间
     * @return 已签发且已过期返回 true
     */
    public boolean isExpired(Instant now) {
        return state == QuotationState.ISSUED && now != null && !now.isBefore(validUntil);
    }

    /**
     * 撤回已签发版本。
     *
     * @param withdrawnAt 撤回时间
     * @param owner       撤回人 owner
     * @return 撤回后的版本事实
     */
    public QuotationVersion withdrawn(Instant withdrawnAt, String owner) {
        if (state != QuotationState.ISSUED) {
            throw new CrmException(CrmErrorCode.INVALID_STATE_TRANSITION, "quotation version is not issued");
        }
        return new QuotationVersion(quotationId, version, QuotationState.WITHDRAWN, settlementCurrency, validUntil,
                lines, totalAmount, issuedAt, issuedBy, withdrawnAt, owner);
    }

    /**
     * 被重议新报价取代。
     *
     * @param supersededAt 取代时间
     * @return 被取代后的版本事实
     */
    public QuotationVersion superseded(Instant supersededAt) {
        if (state != QuotationState.ISSUED) {
            throw new CrmException(CrmErrorCode.INVALID_STATE_TRANSITION, "quotation version is not issued");
        }
        return new QuotationVersion(quotationId, version, QuotationState.SUPERSEDED, settlementCurrency, validUntil,
                lines, totalAmount, issuedAt, issuedBy, supersededAt, null);
    }

    /**
     * 确认报价版本并冻结确认事实。
     *
     * @param confirmedAt 确认时间
     * @param owner       已验证的确认主体归属
     * @return 处理后的领域事实或校验结果。
     *
     */
    public QuotationVersion confirm(Instant confirmedAt, String owner) {
        if (state != QuotationState.ISSUED) {
            throw new CrmException(CrmErrorCode.INVALID_STATE_TRANSITION,
                    "quotation version is not issued");
        }
        Instant requiredTerminalAt = CrmValidationHelper.requiredObject(confirmedAt, "confirmedAt");
        if (!requiredTerminalAt.isBefore(validUntil)) {
            throw new CrmException(CrmErrorCode.QUOTATION_EXPIRED, "quotation has expired");
        }
        return new QuotationVersion(quotationId, version, QuotationState.CONFIRMED, settlementCurrency, validUntil,
                lines, totalAmount, issuedAt, issuedBy, requiredTerminalAt, owner);
    }

    private void validateLifecycleFields() {
        if (state == QuotationState.DRAFT
                && (issuedAt != null || issuedBy != null || terminalAt != null || terminalBy != null)) {
            throw CrmException.validation("quotationVersion.lifecycle");
        }
        if (state == QuotationState.ISSUED
                && (issuedAt == null || issuedBy == null || terminalAt != null || terminalBy != null)) {
            throw CrmException.validation("quotationVersion.lifecycle");
        }
        if (state == QuotationState.CONFIRMED
                && (issuedAt == null || issuedBy == null || terminalAt == null || terminalBy == null
                || terminalAt.isBefore(issuedAt))) {
            throw CrmException.validation("quotationVersion.lifecycle");
        }
        if ((state == QuotationState.WITHDRAWN || state == QuotationState.SUPERSEDED)
                && (issuedAt == null || issuedBy == null || terminalAt == null)) {
            throw CrmException.validation("quotationVersion.lifecycle");
        }
    }
}
