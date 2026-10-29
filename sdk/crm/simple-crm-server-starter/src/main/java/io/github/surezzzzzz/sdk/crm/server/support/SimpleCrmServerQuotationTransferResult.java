package io.github.surezzzzzz.sdk.crm.server.support;

import io.github.surezzzzzz.sdk.crm.server.core.domain.quotation.Quotation;
import io.github.surezzzzzz.sdk.crm.server.core.support.CrmValidationHelper;
import lombok.Getter;

/**
 * 报价转移的声明结果：展示态与归属事实一同冻结，重放不读取后续版本或当前时钟。
 *
 * @author surezzzzzz
 */
@Getter
public final class SimpleCrmServerQuotationTransferResult {

    private final Quotation quotation;
    private final String displayState;

    /**
     * 创建报价转移结果。
     *
     * @param quotation 首次转移后的报价事实
     * @param displayState 首次转移时的展示态
     */
    public SimpleCrmServerQuotationTransferResult(Quotation quotation, String displayState) {
        this.quotation = CrmValidationHelper.requiredObject(quotation, "quotation");
        this.displayState = CrmValidationHelper.required(displayState, "displayState");
    }
}
