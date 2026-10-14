package io.github.surezzzzzz.sdk.crm.server.core.command;

import io.github.surezzzzzz.sdk.crm.server.core.domain.identity.CrmOwner;
import io.github.surezzzzzz.sdk.crm.server.core.support.CrmValidationHelper;
import lombok.Getter;

/**
 * 客户归属转移命令：owner 改指新归属主体（已确认历史事实不追改）。
 *
 * @author surezzzzzz
 */
@Getter
public final class TransferCustomerCommand {

    private final String customerId;
    private final String newOwner;

    /**
     * 创建TransferCustomerCommand。
     *
     * @param customerId 目标资源唯一标识
     * @param newOwner   新归属主体（iam:/aksk: 前缀，经 CrmOwner.of 校验）
     */
    public TransferCustomerCommand(String customerId, String newOwner) {
        this.customerId = CrmValidationHelper.required(customerId, "customerId");
        CrmOwner.of(newOwner);
        this.newOwner = newOwner;
    }
}
