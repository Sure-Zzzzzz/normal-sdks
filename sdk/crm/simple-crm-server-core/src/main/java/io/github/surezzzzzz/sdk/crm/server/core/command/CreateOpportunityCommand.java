package io.github.surezzzzzz.sdk.crm.server.core.command;

import io.github.surezzzzzz.sdk.crm.server.core.support.CrmValidationHelper;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 创建商机命令。 */
@Getter
public final class CreateOpportunityCommand {
    private final String customerId;
    private final String title;
    private final String primaryOfferingId;
    private final String contactId;
    private final LocalDate expectedCloseDate;
    private final BigDecimal estimatedAmount;
    private final String currency;

    public CreateOpportunityCommand(String customerId, String title, String primaryOfferingId, String contactId,
                                    LocalDate expectedCloseDate, BigDecimal estimatedAmount, String currency) {
        this.customerId = CrmValidationHelper.required(customerId, "customerId");
        this.title = CrmValidationHelper.required(title, "title");
        this.primaryOfferingId = CrmValidationHelper.optional(primaryOfferingId);
        this.contactId = CrmValidationHelper.optional(contactId);
        this.expectedCloseDate = expectedCloseDate;
        this.estimatedAmount = estimatedAmount;
        this.currency = currency == null ? null : CrmValidationHelper.required(currency, "currency");
    }
}
