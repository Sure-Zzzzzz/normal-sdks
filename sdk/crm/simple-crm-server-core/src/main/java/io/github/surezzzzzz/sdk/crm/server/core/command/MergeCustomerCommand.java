package io.github.surezzzzzz.sdk.crm.server.core.command;

import io.github.surezzzzzz.sdk.crm.server.core.error.CrmErrorCode;
import io.github.surezzzzzz.sdk.crm.server.core.error.CrmException;
import io.github.surezzzzzz.sdk.crm.server.core.support.CrmValidationHelper;
import lombok.Getter;

/**
 * 合并重复客户命令：源客户并入目标客户，源置 MERGED 终态。
 *
 * @author surezzzzzz
 */
@Getter
public final class MergeCustomerCommand {

    private final String sourceCustomerId;
    private final String targetCustomerId;

    /**
     * 创建MergeCustomerCommand。
     *
     * @param sourceCustomerId 源客户唯一标识
     * @param targetCustomerId 目标客户唯一标识
     */
    public MergeCustomerCommand(String sourceCustomerId, String targetCustomerId) {
        this.sourceCustomerId = CrmValidationHelper.required(sourceCustomerId, "sourceCustomerId");
        this.targetCustomerId = CrmValidationHelper.required(targetCustomerId, "targetCustomerId");
        if (this.sourceCustomerId.equals(this.targetCustomerId)) {
            throw new CrmException(CrmErrorCode.VALIDATION_FAILED, "source and target customer must differ");
        }
    }
}
