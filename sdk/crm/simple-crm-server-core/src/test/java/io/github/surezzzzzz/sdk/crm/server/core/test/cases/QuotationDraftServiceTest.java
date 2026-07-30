package io.github.surezzzzzz.sdk.crm.server.core.test.cases;

import io.github.surezzzzzz.sdk.crm.server.core.command.CreateQuotationCommand;
import io.github.surezzzzzz.sdk.crm.server.core.command.QuotationLineCommand;
import io.github.surezzzzzz.sdk.crm.server.core.domain.commercial.CommercialCapabilityRegistry;
import io.github.surezzzzzz.sdk.crm.server.core.domain.commercial.CommercialCapabilityType;
import io.github.surezzzzzz.sdk.crm.server.core.domain.commercial.FixedPriceFulfillmentCapability;
import io.github.surezzzzzz.sdk.crm.server.core.domain.common.Money;
import io.github.surezzzzzz.sdk.crm.server.core.domain.customer.Customer;
import io.github.surezzzzzz.sdk.crm.server.core.domain.customer.CustomerState;
import io.github.surezzzzzz.sdk.crm.server.core.domain.identity.CrmActor;
import io.github.surezzzzzz.sdk.crm.server.core.domain.offering.Offering;
import io.github.surezzzzzz.sdk.crm.server.core.domain.offering.OfferingState;
import io.github.surezzzzzz.sdk.crm.server.core.domain.quotation.QuotationDraft;
import io.github.surezzzzzz.sdk.crm.server.core.domain.quotation.QuotationDraftService;
import io.github.surezzzzzz.sdk.crm.server.core.domain.quotation.QuotationState;
import io.github.surezzzzzz.sdk.crm.server.core.error.CrmErrorCode;
import io.github.surezzzzzz.sdk.crm.server.core.error.CrmException;
import io.github.surezzzzzz.sdk.crm.server.core.port.system.CrmIdGenerator;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Slf4j
class QuotationDraftServiceTest {

    private final Instant now = Instant.parse("2026-07-29T00:00:00Z");
    private final QuotationDraftService service = new QuotationDraftService(
            new CommercialCapabilityRegistry(Collections.singletonList(new FixedPriceFulfillmentCapability())));

    @Test
    void createsDraftFromAuthenticatedActorAndFrozenOfferingFacts() {
        QuotationDraft draft = service.create(actor(), activeCustomer(), command(),
                Collections.singletonList(activeOffering("offering-1", "CNY")), now, idGenerator());

        log.info("报价草稿构造结果：报价ID={}，租户={}，Owner={}，金额={} {}",
                draft.getQuotation().getQuotationId(), draft.getQuotation().getTenantId(),
                draft.getQuotation().getOwnerActorId(), draft.getQuotationVersion().getTotalAmount().getAmount(),
                draft.getQuotationVersion().getSettlementCurrency());
        assertEquals("quotation-1", draft.getQuotation().getQuotationId());
        assertEquals("tenant-1", draft.getQuotation().getTenantId());
        assertEquals("actor-1", draft.getQuotation().getOwnerActorId());
        assertEquals(QuotationState.DRAFT, draft.getQuotationVersion().getState());
        assertEquals("CNY", draft.getQuotationVersion().getSettlementCurrency());
        assertEquals(new BigDecimal("59.70"), draft.getQuotationVersion().getTotalAmount().getAmount());
        assertEquals("offering-reference-1", draft.getQuotationVersion().getLines().get(0).getOfferingReference());
    }

    @Test
    void rejectsInactiveOrCrossTenantCommercialFacts() {
        Customer inactiveCustomer = new Customer("customer-1", "tenant-1", "Customer", CustomerState.INACTIVE,
                "actor-1", 1L, now, now);
        assertError(CrmErrorCode.INVALID_STATE_TRANSITION, () -> service.create(actor(), inactiveCustomer, command(),
                Collections.singletonList(activeOffering("offering-1", "CNY")), now, idGenerator()));

        assertError(CrmErrorCode.TENANT_MISMATCH, () -> service.create(actor(), activeCustomer(), command(),
                Collections.singletonList(new Offering("offering-1", "tenant-2", "offering-reference-1",
                        "Offering", OfferingState.ACTIVE, CommercialCapabilityType.FIXED_PRICE_FULFILLMENT_V1,
                        "capability-1", 1L, now, now)), now, idGenerator()));
    }

    @Test
    void rejectsMissingCapabilityAndMixedSettlementCurrencies() {
        QuotationDraftService noCapabilityService = new QuotationDraftService(
                new CommercialCapabilityRegistry(Collections.emptyList()));
        assertError(CrmErrorCode.COMMERCIAL_CAPABILITY_UNAVAILABLE, () -> noCapabilityService.create(actor(),
                activeCustomer(), command(), Collections.singletonList(activeOffering("offering-1", "CNY")),
                now, idGenerator()));

        CreateQuotationCommand mixedCurrencyCommand = new CreateQuotationCommand("customer-1",
                Instant.parse("2026-08-01T00:00:00Z"), Arrays.asList(
                line("offering-1", "CNY"), line("offering-2", "USD")));
        assertError(CrmErrorCode.VALIDATION_FAILED, () -> service.create(actor(), activeCustomer(),
                mixedCurrencyCommand, Arrays.asList(activeOffering("offering-1", "CNY"),
                        activeOffering("offering-2", "USD")), now, idGenerator()));
    }

    private CrmActor actor() {
        return new CrmActor("tenant-1", "actor-1", "Actor");
    }

    private Customer activeCustomer() {
        return new Customer("customer-1", "tenant-1", "Customer", CustomerState.ACTIVE,
                "actor-1", 1L, now, now);
    }

    private Offering activeOffering(String offeringId, String currency) {
        return new Offering(offeringId, "tenant-1", "offering-reference-" + offeringId.substring(9), "Offering",
                OfferingState.ACTIVE, CommercialCapabilityType.FIXED_PRICE_FULFILLMENT_V1,
                "capability-1", 1L, now, now);
    }

    private CreateQuotationCommand command() {
        return new CreateQuotationCommand("customer-1", Instant.parse("2026-08-01T00:00:00Z"),
                Collections.singletonList(line("offering-1", "CNY")));
    }

    private QuotationLineCommand line(String offeringId, String currency) {
        return new QuotationLineCommand(offeringId, new BigDecimal("3"), "SET",
                new Money(new BigDecimal("19.90"), currency), "subject-1", "scope-1");
    }

    private CrmIdGenerator idGenerator() {
        AtomicLong sequence = new AtomicLong();
        return resourceType -> resourceType.name().toLowerCase().replace('_', '-')
                + "-" + sequence.incrementAndGet();
    }

    private void assertError(CrmErrorCode errorCode, ThrowingRunnable runnable) {
        CrmException exception = assertThrows(CrmException.class, runnable::run);
        log.info("报价草稿拒绝结果：期望错误码={}，实际错误码={}", errorCode, exception.getErrorCode());
        assertEquals(errorCode, exception.getErrorCode());
    }

    private interface ThrowingRunnable {
        void run();
    }
}
