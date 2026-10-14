package io.github.surezzzzzz.sdk.crm.server.core.command;

import io.github.surezzzzzz.sdk.crm.server.core.support.CrmValidationHelper;
import lombok.Getter;

/**
 * 人工重置失败履约项命令。
 *
 * @author surezzzzzz
 */
@Getter
public final class ResetFulfillmentItemCommand {

    private final String fulfillmentId;

    /**
     * 创建ResetFulfillmentItemCommand。
     *
     * @param fulfillmentId 目标资源唯一标识
     */
    public ResetFulfillmentItemCommand(String fulfillmentId) {
        this.fulfillmentId = CrmValidationHelper.required(fulfillmentId, "fulfillmentId");
    }
}
