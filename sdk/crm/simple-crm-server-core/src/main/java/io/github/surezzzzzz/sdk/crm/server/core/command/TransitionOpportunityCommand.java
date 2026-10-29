package io.github.surezzzzzz.sdk.crm.server.core.command;

import io.github.surezzzzzz.sdk.crm.server.core.domain.opportunity.OpportunityStage;
import io.github.surezzzzzz.sdk.crm.server.core.support.CrmValidationHelper;
import lombok.Getter;

/** 推进或输掉商机命令。 */
@Getter
public final class TransitionOpportunityCommand {
    private final String opportunityId;
    private final OpportunityStage stage;
    private final String lostReason;

    public TransitionOpportunityCommand(String opportunityId, OpportunityStage stage, String lostReason) {
        this.opportunityId = CrmValidationHelper.required(opportunityId, "opportunityId");
        this.stage = CrmValidationHelper.requiredObject(stage, "stage");
        this.lostReason = CrmValidationHelper.optional(lostReason);
    }
}
