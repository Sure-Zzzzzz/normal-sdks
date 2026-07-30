package io.github.surezzzzzz.sdk.crm.server.core.command;

import io.github.surezzzzzz.sdk.crm.server.core.error.CrmException;
import io.github.surezzzzzz.sdk.crm.server.core.support.CrmValidationHelper;
import lombok.Getter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 创建报价草稿命令。
 *
 * @author surezzzzzz
 */
@Getter
public final class CreateQuotationCommand {

    private final String customerId;
    private final Instant validUntil;
    private final List<QuotationLineCommand> lines;

    /**
     * 创建CreateQuotationCommand。
     *
     * @param customerId 客户唯一标识
     * @param validUntil 报价有效截止时间
     * @param lines      冻结行事实集合
     *
     */
    public CreateQuotationCommand(String customerId, Instant validUntil,
                                  List<QuotationLineCommand> lines) {
        this.customerId = CrmValidationHelper.required(customerId, "customerId");
        this.validUntil = CrmValidationHelper.requiredObject(validUntil, "validUntil");
        if (lines == null || lines.isEmpty() || lines.contains(null)) {
            throw CrmException.validation("lines");
        }
        this.lines = Collections.unmodifiableList(new ArrayList<QuotationLineCommand>(lines));
    }


}
