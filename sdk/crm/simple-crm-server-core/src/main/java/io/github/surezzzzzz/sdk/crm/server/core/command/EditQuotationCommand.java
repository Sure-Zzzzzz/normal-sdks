package io.github.surezzzzzz.sdk.crm.server.core.command;

import io.github.surezzzzzz.sdk.crm.server.core.support.CrmValidationHelper;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

/**
 * 编辑报价草稿命令：整单替换行项与有效截止时间，仅草稿态可用。
 *
 * @author surezzzzzz
 */
@Getter
public final class EditQuotationCommand {

    private final String quotationId;
    private final Instant validUntil;
    private final List<QuotationLineCommand> lines;

    /**
     * 创建EditQuotationCommand。
     *
     * @param quotationId 报价唯一标识
     * @param validUntil  报价有效截止时间
     * @param lines       整单替换的报价行命令集合
     */
    public EditQuotationCommand(String quotationId, Instant validUntil, List<QuotationLineCommand> lines) {
        this.quotationId = CrmValidationHelper.required(quotationId, "quotationId");
        this.validUntil = CrmValidationHelper.requiredObject(validUntil, "validUntil");
        this.lines = CrmValidationHelper.requiredObject(lines, "lines");
    }
}
