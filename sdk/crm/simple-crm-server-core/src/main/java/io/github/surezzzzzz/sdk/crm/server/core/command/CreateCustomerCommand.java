package io.github.surezzzzzz.sdk.crm.server.core.command;

import io.github.surezzzzzz.sdk.crm.server.core.support.CrmValidationHelper;
import lombok.Getter;

/**
 * 创建 Customer 命令。
 *
 * @author surezzzzzz
 */
@Getter
public final class CreateCustomerCommand {

    private final String displayName;

    /**
     * 创建CreateCustomerCommand。
     *
     * @param displayName 展示名称
     *
     */
    public CreateCustomerCommand(String displayName) {
        this.displayName = CrmValidationHelper.required(displayName, "displayName");
    }

}
