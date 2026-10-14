package io.github.surezzzzzz.sdk.crm.server.core.command;

import io.github.surezzzzzz.sdk.crm.server.core.support.CrmValidationHelper;
import lombok.Getter;

/**
 * 复启客户命令。
 *
 * @author surezzzzzz
 */
@Getter
public final class ActivateCustomerCommand {

    private final String customerId;

    /**
     * 创建ActivateCustomerCommand。
     *
     * @param customerId 目标资源唯一标识
     */
    public ActivateCustomerCommand(String customerId) {
        this.customerId = CrmValidationHelper.required(customerId, "customerId");
    }
}
