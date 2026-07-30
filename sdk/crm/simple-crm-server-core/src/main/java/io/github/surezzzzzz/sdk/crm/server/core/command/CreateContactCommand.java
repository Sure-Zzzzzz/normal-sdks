package io.github.surezzzzzz.sdk.crm.server.core.command;

import io.github.surezzzzzz.sdk.crm.server.core.support.CrmValidationHelper;
import lombok.Getter;

/**
 * 创建 Contact 命令。
 *
 * @author surezzzzzz
 */
@Getter
public final class CreateContactCommand {

    private final String customerId;
    private final String displayName;
    private final String title;

    /**
     * 创建CreateContactCommand。
     *
     * @param customerId  客户唯一标识
     * @param displayName 展示名称
     * @param title       职务名称
     *
     */
    public CreateContactCommand(String customerId, String displayName, String title) {
        this.customerId = CrmValidationHelper.required(customerId, "customerId");
        this.displayName = CrmValidationHelper.required(displayName, "displayName");
        this.title = CrmValidationHelper.optional(title);
    }


}
