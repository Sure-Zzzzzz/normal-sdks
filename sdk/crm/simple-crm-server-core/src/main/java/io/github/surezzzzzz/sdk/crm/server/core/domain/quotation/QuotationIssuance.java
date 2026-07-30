package io.github.surezzzzzz.sdk.crm.server.core.domain.quotation;

import io.github.surezzzzzz.sdk.crm.server.core.error.CrmException;
import io.github.surezzzzzz.sdk.crm.server.core.support.CrmValidationHelper;
import lombok.Getter;

/**
 * 签发报价后应在同一事务内持久化的聚合与版本事实。
 *
 * @author surezzzzzz
 */
@Getter
public final class QuotationIssuance {

    private final Quotation quotation;
    private final QuotationVersion quotationVersion;

    /**
     * 创建QuotationIssuance。
     *
     * @param quotation        报价聚合当前事实
     * @param quotationVersion 报价版本事实
     *
     */
    public QuotationIssuance(Quotation quotation, QuotationVersion quotationVersion) {
        this.quotation = CrmValidationHelper.requiredObject(quotation, "quotation");
        this.quotationVersion = CrmValidationHelper.requiredObject(quotationVersion, "quotationVersion");
        if (!this.quotation.getQuotationId().equals(this.quotationVersion.getQuotationId())
                || this.quotation.getCurrentVersion() != this.quotationVersion.getVersion()
                || this.quotation.isConfirmed()
                || this.quotation.getCurrentConfirmableVersion() == null
                || this.quotation.getCurrentConfirmableVersion() != this.quotationVersion.getVersion()
                || this.quotationVersion.getState() != QuotationState.ISSUED) {
            throw CrmException.validation("quotationIssuance");
        }
    }


}
