package io.github.surezzzzzz.sdk.crm.server.core.command;

import io.github.surezzzzzz.sdk.crm.server.core.support.CrmValidationHelper;
import lombok.Getter;

/**
 * 停用客户命令。
 *
 * @author surezzzzzz
 */
@Getter
public final class DeactivateCustomerCommand {

    private final String customerId;

    /**
     * 创建DeactivateCustomerCommand。
     *
     * @param customerId 目标资源唯一标识
     */
    public DeactivateCustomerCommand(String customerId) {
        this.customerId = CrmValidationHelper.required(customerId, "customerId");
    }
}
