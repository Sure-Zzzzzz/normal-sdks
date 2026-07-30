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
    private final String issuedByActorId;
    private final Instant confirmedAt;
    private final String confirmedByActorId;

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
     * @param issuedByActorId    issuedByActorId参数。
     * @param confirmedAt        报价确认时间
     * @param confirmedByActorId confirmedByActorId参数。
     *
     */
    public QuotationVersion(String quotationId, int version, QuotationState state, String settlementCurrency,
                            Instant validUntil, List<QuotationLine> lines, Money totalAmount,
                            Instant issuedAt, String issuedByActorId, Instant confirmedAt,
                            String confirmedByActorId) {
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
        this.issuedByActorId = issuedByActorId == null ? null
                : CrmValidationHelper.required(issuedByActorId, "issuedByActorId");
        this.confirmedAt = confirmedAt;
        this.confirmedByActorId = confirmedByActorId == null ? null
                : CrmValidationHelper.required(confirmedByActorId, "confirmedByActorId");
        validateLifecycleFields();
    }


    /**
     * 签发报价版本并冻结签发事实。
     *
     * @param issuedAt 报价签发时间
     * @param actorId  执行操作的操作者标识
     * @return 处理后的领域事实或校验结果。
     *
     */
    public QuotationVersion issue(Instant issuedAt, String actorId) {
        if (state != QuotationState.DRAFT) {
            throw new CrmException(CrmErrorCode.INVALID_STATE_TRANSITION,
                    "quotation version is not draft");
        }
        Instant requiredIssuedAt = CrmValidationHelper.requiredObject(issuedAt, "issuedAt");
        if (!requiredIssuedAt.isBefore(validUntil)) {
            throw new CrmException(CrmErrorCode.QUOTATION_EXPIRED, "quotation has expired");
        }
        return new QuotationVersion(quotationId, version, QuotationState.ISSUED, settlementCurrency, validUntil,
                lines, totalAmount, requiredIssuedAt, actorId, null, null);
    }

    /**
     * 确认报价版本并冻结确认事实。
     *
     * @param confirmedAt 报价确认时间
     * @param actorId     执行操作的操作者标识
     * @return 处理后的领域事实或校验结果。
     *
     */
    public QuotationVersion confirm(Instant confirmedAt, String actorId) {
        if (state != QuotationState.ISSUED) {
            throw new CrmException(CrmErrorCode.INVALID_STATE_TRANSITION,
                    "quotation version is not issued");
        }
        Instant requiredConfirmedAt = CrmValidationHelper.requiredObject(confirmedAt, "confirmedAt");
        if (!requiredConfirmedAt.isBefore(validUntil)) {
            throw new CrmException(CrmErrorCode.QUOTATION_EXPIRED, "quotation has expired");
        }
        return new QuotationVersion(quotationId, version, QuotationState.CONFIRMED, settlementCurrency, validUntil,
                lines, totalAmount, issuedAt, issuedByActorId, requiredConfirmedAt, actorId);
    }

    private void validateLifecycleFields() {
        if (state == QuotationState.DRAFT
                && (issuedAt != null || issuedByActorId != null || confirmedAt != null || confirmedByActorId != null)) {
            throw CrmException.validation("quotationVersion.lifecycle");
        }
        if (state == QuotationState.ISSUED
                && (issuedAt == null || issuedByActorId == null || confirmedAt != null || confirmedByActorId != null)) {
            throw CrmException.validation("quotationVersion.lifecycle");
        }
        if (state == QuotationState.CONFIRMED
                && (issuedAt == null || issuedByActorId == null || confirmedAt == null || confirmedByActorId == null
                || confirmedAt.isBefore(issuedAt))) {
            throw CrmException.validation("quotationVersion.lifecycle");
        }
        if (state != QuotationState.DRAFT && state != QuotationState.ISSUED
                && state != QuotationState.CONFIRMED) {
            throw new CrmException(CrmErrorCode.INVALID_STATE_TRANSITION,
                    "quotation version state is not supported");
        }
    }
}
