package io.github.surezzzzzz.sdk.crm.server.core.command;

import io.github.surezzzzzz.sdk.crm.server.core.support.CrmValidationHelper;
import lombok.Getter;

/**
 * 撤回已签发报价命令。
 *
 * @author surezzzzzz
 */
@Getter
public final class WithdrawQuotationCommand {

    private final String quotationId;

    /**
     * 创建WithdrawQuotationCommand。
     *
     * @param quotationId 目标资源唯一标识
     */
    public WithdrawQuotationCommand(String quotationId) {
        this.quotationId = CrmValidationHelper.required(quotationId, "quotationId");
    }
}
