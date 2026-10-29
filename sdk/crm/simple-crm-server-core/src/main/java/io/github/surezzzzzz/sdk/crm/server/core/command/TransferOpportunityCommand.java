package io.github.surezzzzzz.sdk.crm.server.core.command;

import io.github.surezzzzzz.sdk.crm.server.core.domain.identity.CrmOwner;
import io.github.surezzzzzz.sdk.crm.server.core.support.CrmValidationHelper;
import lombok.Getter;

/** 转移商机归属命令。 */
@Getter
public final class TransferOpportunityCommand {
    private final String opportunityId;
    private final String newOwner;

    public TransferOpportunityCommand(String opportunityId, String newOwner) {
        this.opportunityId = CrmValidationHelper.required(opportunityId, "opportunityId");
        this.newOwner = CrmValidationHelper.required(newOwner, "newOwner");
        CrmOwner.of(this.newOwner);
    }
}
