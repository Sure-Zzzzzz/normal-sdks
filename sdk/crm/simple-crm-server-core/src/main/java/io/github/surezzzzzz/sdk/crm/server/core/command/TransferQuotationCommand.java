package io.github.surezzzzzz.sdk.crm.server.core.command;

import io.github.surezzzzzz.sdk.crm.server.core.domain.identity.CrmOwner;
import io.github.surezzzzzz.sdk.crm.server.core.support.CrmValidationHelper;
import lombok.Getter;

/**
 * 未完结报价归属转移命令：owner 改指新归属主体（已确认历史事实不追改）。
 *
 * @author surezzzzzz
 */
@Getter
public final class TransferQuotationCommand {

    private final String quotationId;
    private final String newOwner;

    /**
     * 创建TransferQuotationCommand。
     *
     * @param quotationId 目标资源唯一标识
     * @param newOwner    新归属主体（iam:/aksk: 前缀，经 CrmOwner.of 校验）
     */
    public TransferQuotationCommand(String quotationId, String newOwner) {
        this.quotationId = CrmValidationHelper.required(quotationId, "quotationId");
        CrmOwner.of(newOwner);
        this.newOwner = newOwner;
    }
}
