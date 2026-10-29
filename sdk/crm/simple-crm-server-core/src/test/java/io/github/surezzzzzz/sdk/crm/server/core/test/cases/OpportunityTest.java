package io.github.surezzzzzz.sdk.crm.server.core.test.cases;

import io.github.surezzzzzz.sdk.crm.server.core.domain.opportunity.Opportunity;
import io.github.surezzzzzz.sdk.crm.server.core.domain.opportunity.OpportunityStage;
import io.github.surezzzzzz.sdk.crm.server.core.error.CrmErrorCode;
import io.github.surezzzzzz.sdk.crm.server.core.error.CrmException;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 商机阶段与归属状态机测试。 */
@Slf4j
class OpportunityTest {

    private static final Instant NOW = Instant.parse("2026-10-17T00:00:00Z");

    @Test
    void progressesOnlyForwardAndAllowsLostFromAnyOpenStage() {
        Opportunity opportunity = opportunity(OpportunityStage.NEW, null);
        opportunity = opportunity.progressedTo(OpportunityStage.QUALIFIED, null, NOW);
        opportunity = opportunity.progressedTo(OpportunityStage.PROPOSAL, null, NOW.plusSeconds(1));
        opportunity = opportunity.progressedTo(OpportunityStage.NEGOTIATION, null, NOW.plusSeconds(2));
        Opportunity lost = opportunity.progressedTo(OpportunityStage.LOST, "预算冻结", NOW.plusSeconds(3));

        log.info("商机阶段推进：stage={}，version={}", lost.getStage(), lost.getAggregateVersion());
        assertEquals(OpportunityStage.LOST, lost.getStage());
        assertEquals("预算冻结", lost.getLostReason());
    }

    @Test
    void rejectsBackwardAndManualWonTransitions() {
        Opportunity qualified = opportunity(OpportunityStage.QUALIFIED, null);

        CrmException backward = assertThrows(CrmException.class,
                () -> qualified.progressedTo(OpportunityStage.NEW, null, NOW));
        assertEquals(CrmErrorCode.INVALID_STATE_TRANSITION, backward.getErrorCode());

        CrmException won = assertThrows(CrmException.class,
                () -> qualified.progressedTo(OpportunityStage.WON, null, NOW));
        assertEquals(CrmErrorCode.INVALID_STATE_TRANSITION, won.getErrorCode());
    }

    @Test
    void requiresLostReasonAndWonIsDerivedOnly() {
        Opportunity opportunity = opportunity(OpportunityStage.PROPOSAL, null);
        CrmException missingReason = assertThrows(CrmException.class,
                () -> opportunity.progressedTo(OpportunityStage.LOST, " ", NOW));
        assertEquals(CrmErrorCode.VALIDATION_FAILED, missingReason.getErrorCode());

        Opportunity won = opportunity.won(NOW);
        assertEquals(OpportunityStage.WON, won.getStage());
        assertEquals(OpportunityStage.WON, won.won(NOW.plusSeconds(1)).getStage());
    }

    private Opportunity opportunity(OpportunityStage stage, String lostReason) {
        return new Opportunity("opportunity-1", "customer-1", "iam:owner-1", "业务机会",
                null, null, null, null, null, stage, lostReason, 1L, NOW, NOW);
    }
}
