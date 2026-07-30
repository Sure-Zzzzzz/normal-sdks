package io.github.surezzzzzz.sdk.crm.server.core.command;

import io.github.surezzzzzz.sdk.crm.server.core.support.CrmValidationHelper;
import lombok.Getter;

/**
 * 确认报价版本命令。
 *
 * @author surezzzzzz
 */
@Getter
public final class ConfirmQuotationCommand {

    private final String quotationId;
    private final int quotationVersion;
    private final long expectedAggregateVersion;

    /**
     * 创建ConfirmQuotationCommand。
     *
     * @param quotationId              报价唯一标识
     * @param quotationVersion         报价版本事实
     * @param expectedAggregateVersion 调用方预期的聚合版本
     *
     */
    public ConfirmQuotationCommand(String quotationId, int quotationVersion, long expectedAggregateVersion) {
        this.quotationId = CrmValidationHelper.required(quotationId, "quotationId");
        this.quotationVersion = CrmValidationHelper.positiveVersion(quotationVersion, "quotationVersion");
        this.expectedAggregateVersion = CrmValidationHelper.positiveVersion(expectedAggregateVersion,
                "expectedAggregateVersion");
    }


}
