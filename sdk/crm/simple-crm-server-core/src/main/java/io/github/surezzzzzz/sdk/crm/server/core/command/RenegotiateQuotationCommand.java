package io.github.surezzzzzz.sdk.crm.server.core.command;

import io.github.surezzzzzz.sdk.crm.server.core.support.CrmValidationHelper;
import lombok.Getter;

/**
 * 重议派生新草稿命令（自已签发报价复制快照）。
 *
 * @author surezzzzzz
 */
@Getter
public final class RenegotiateQuotationCommand {

    private final String sourceQuotationId;

    /**
     * 创建RenegotiateQuotationCommand。
     *
     * @param sourceQuotationId 目标资源唯一标识
     */
    public RenegotiateQuotationCommand(String sourceQuotationId) {
        this.sourceQuotationId = CrmValidationHelper.required(sourceQuotationId, "sourceQuotationId");
    }
}
