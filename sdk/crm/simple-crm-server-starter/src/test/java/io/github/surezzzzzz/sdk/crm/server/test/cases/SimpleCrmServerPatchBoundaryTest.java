package io.github.surezzzzzz.sdk.crm.server.test.cases;

import io.github.surezzzzzz.sdk.crm.server.controller.SimpleCrmServerArchiveController;
import io.github.surezzzzzz.sdk.crm.server.controller.SimpleCrmServerExceptionHandler;
import io.github.surezzzzzz.sdk.crm.server.controller.SimpleCrmServerFulfillmentController;
import io.github.surezzzzzz.sdk.crm.server.controller.SimpleCrmServerOpportunityController;
import io.github.surezzzzzz.sdk.crm.server.controller.SimpleCrmServerQuotationController;
import io.github.surezzzzzz.sdk.crm.server.core.domain.identity.CrmOwner;
import io.github.surezzzzzz.sdk.crm.server.service.SimpleCrmServerCommandService;
import io.github.surezzzzzz.sdk.crm.server.service.SimpleCrmServerOwnerResolverBridge;
import io.github.surezzzzzz.sdk.crm.server.service.SimpleCrmServerQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PATCH 分派边界负向契约：分派判别参数互斥（state×owner、sequence×state、stage×owner），
 * 混用必须 400；缺判别参数不匹配任何路由为 404。与分派正向契约测试互补。
 *
 * @author surezzzzzz
 */
class SimpleCrmServerPatchBoundaryTest {

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        SimpleCrmServerCommandService commands = mock(SimpleCrmServerCommandService.class);
        SimpleCrmServerQueryService queries = mock(SimpleCrmServerQueryService.class);
        SimpleCrmServerOwnerResolverBridge ownerResolver =
                mock(SimpleCrmServerOwnerResolverBridge.class);
        when(ownerResolver.resolve()).thenReturn(CrmOwner.human("boundary-subject"));
        mvc = MockMvcBuilders.standaloneSetup(
                        new SimpleCrmServerArchiveController(commands, queries, ownerResolver),
                        new SimpleCrmServerOpportunityController(commands, queries, ownerResolver),
                        new SimpleCrmServerQuotationController(commands, queries, ownerResolver),
                        new SimpleCrmServerFulfillmentController(commands, queries, ownerResolver))
                .setControllerAdvice(new SimpleCrmServerExceptionHandler())
                .build();
    }

    private void assertBadRequest(String path) throws Exception {
        mvc.perform(patch(path).header("Idempotency-Key", "boundary-key")
                        .contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
    }

    private void assertNotFound(String path) throws Exception {
        mvc.perform(patch(path).header("Idempotency-Key", "boundary-key")
                        .contentType("application/json").content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void customerStateAndOwnerMutuallyExclusive() throws Exception {
        assertBadRequest("/api/crm/customers/customer-1?state=inactive&owner=iam:other");
    }

    @Test
    void quotationStateAndOwnerMutuallyExclusive() throws Exception {
        assertBadRequest("/api/crm/quotations/quotation-1?state=issued&owner=iam:other");
    }

    @Test
    void opportunityStageAndOwnerMutuallyExclusive() throws Exception {
        assertBadRequest("/api/crm/opportunities/opportunity-1?stage=WON&owner=iam:other");
    }

    @Test
    void offeringStateAndOwnerMutuallyExclusive() throws Exception {
        assertBadRequest("/api/crm/offerings/offering-1?state=published&owner=iam:other");
    }

    @Test
    void fulfillmentSequenceAndStateMutuallyExclusive() throws Exception {
        assertBadRequest("/api/crm/fulfillment-items/item-1?sequence=3&state=PENDING_DISPATCH");
        assertBadRequest("/api/crm/fulfillment-items/item-1?sequence=3&state=IN_PROGRESS");
    }

    @Test
    void missingDiscriminatorMatchesNoRoute() throws Exception {
        assertNotFound("/api/crm/customers/customer-1");
        assertNotFound("/api/crm/quotations/quotation-1");
        assertNotFound("/api/crm/offerings/offering-1");
        assertNotFound("/api/crm/opportunities/opportunity-1");
        assertNotFound("/api/crm/fulfillment-items/item-1");
    }
}
