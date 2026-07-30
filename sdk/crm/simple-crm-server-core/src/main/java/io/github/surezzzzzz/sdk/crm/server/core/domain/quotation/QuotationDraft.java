package io.github.surezzzzzz.sdk.crm.server.core.domain.quotation;

import io.github.surezzzzzz.sdk.crm.server.core.error.CrmException;
import io.github.surezzzzzz.sdk.crm.server.core.support.CrmValidationHelper;
import lombok.Getter;

/**
 * 新建报价时应在同一事务内持久化的聚合与首个草稿版本。
 *
 * @author surezzzzzz
 */
@Getter
public final class QuotationDraft {

    private final Quotation quotation;
    private final QuotationVersion quotationVersion;

    /**
     * 创建QuotationDraft。
     *
     * @param quotation        报价聚合当前事实
     * @param quotationVersion 报价版本事实
     *
     */
    public QuotationDraft(Quotation quotation, QuotationVersion quotationVersion) {
        Quotation requiredQuotation = CrmValidationHelper.requiredObject(quotation, "quotation");
        QuotationVersion requiredQuotationVersion = CrmValidationHelper.requiredObject(quotationVersion,
                "quotationVersion");
        if (!requiredQuotation.getQuotationId().equals(requiredQuotationVersion.getQuotationId())
                || requiredQuotation.getCurrentVersion() != requiredQuotationVersion.getVersion()
                || requiredQuotation.getCurrentConfirmableVersion() != null
                || requiredQuotation.getConfirmedOrderId() != null
                || requiredQuotationVersion.getState() != QuotationState.DRAFT) {
            throw CrmException.validation("quotationDraft");
        }
        this.quotation = requiredQuotation;
        this.quotationVersion = requiredQuotationVersion;
    }


}
