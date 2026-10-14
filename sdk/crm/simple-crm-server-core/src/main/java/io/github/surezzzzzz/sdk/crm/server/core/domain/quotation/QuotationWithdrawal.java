package io.github.surezzzzzz.sdk.crm.server.core.domain.quotation;

import io.github.surezzzzzz.sdk.crm.server.core.support.CrmValidationHelper;
import lombok.Getter;

/**
 * 撤回报价后应在同一事务内持久化的事实对。
 *
 * @author surezzzzzz
 */
@Getter
public final class QuotationWithdrawal {

    private final Quotation quotation;
    private final QuotationVersion quotationVersion;

    /**
     * 创建QuotationWithdrawal。
     *
     * @param quotation        撤回后的报价聚合事实
     * @param quotationVersion 撤回后的报价版本事实
     */
    public QuotationWithdrawal(Quotation quotation, QuotationVersion quotationVersion) {
        this.quotation = CrmValidationHelper.requiredObject(quotation, "quotation");
        this.quotationVersion = CrmValidationHelper.requiredObject(quotationVersion, "quotationVersion");
        if (!this.quotation.getQuotationId().equals(this.quotationVersion.getQuotationId())
                || this.quotationVersion.getState() != QuotationState.WITHDRAWN
                || this.quotation.getCurrentConfirmableVersion() != null) {
            throw new IllegalArgumentException("quotationWithdrawal");
        }
    }
}
