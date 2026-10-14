package io.github.surezzzzzz.sdk.crm.server.core.test.cases;

import io.github.surezzzzzz.sdk.crm.server.core.command.CreateQuotationCommand;
import io.github.surezzzzzz.sdk.crm.server.core.command.EditQuotationCommand;
import io.github.surezzzzzz.sdk.crm.server.core.command.QuotationLineCommand;
import io.github.surezzzzzz.sdk.crm.server.core.domain.commercial.CommercialCapabilityRegistry;
import io.github.surezzzzzz.sdk.crm.server.core.domain.commercial.FixedPriceFulfillmentCapability;
import io.github.surezzzzzz.sdk.crm.server.core.domain.common.Money;
import io.github.surezzzzzz.sdk.crm.server.core.domain.customer.Customer;
import io.github.surezzzzzz.sdk.crm.server.core.domain.customer.CustomerState;
import io.github.surezzzzzz.sdk.crm.server.core.domain.identity.CrmOwner;
import io.github.surezzzzzz.sdk.crm.server.core.domain.offering.Offering;
import io.github.surezzzzzz.sdk.crm.server.core.domain.offering.OfferingState;
import io.github.surezzzzzz.sdk.crm.server.core.domain.quotation.*;
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

/**
 * 报价草稿构造与编辑服务测试。
 *
 * @author surezzzzzz
 */
@Slf4j
class QuotationDraftServiceTest {

    private final Instant now = Instant.parse("2026-07-29T00:00:00Z");
    private final QuotationDraftService service = new QuotationDraftService(
            new CommercialCapabilityRegistry(Collections.singletonList(new FixedPriceFulfillmentCapability())));

    @Test
    void createsDraftFromOwnerAndPublishedOfferingFacts() {
        QuotationDraft draft = service.create(actor(), activeCustomer(), command(),
                Collections.singletonList(publishedOffering("offering-1", "CNY")), now, idGenerator());

        log.info("报价草稿构造结果：报价ID={}，owner={}，金额={} {}",
                draft.getQuotation().getQuotationId(), draft.getQuotation().getOwner(),
                draft.getQuotationVersion().getTotalAmount().getAmount(),
                draft.getQuotationVersion().getSettlementCurrency());
        assertEquals("quotation-1", draft.getQuotation().getQuotationId());
        assertEquals("iam:actor-1", draft.getQuotation().getOwner());
        assertEquals(QuotationState.DRAFT, draft.getQuotationVersion().getState());
        assertEquals("CNY", draft.getQuotationVersion().getSettlementCurrency());
        assertEquals(new BigDecimal("59.70"), draft.getQuotationVersion().getTotalAmount().getAmount());
        assertEquals("offering-reference-1", draft.getQuotationVersion().getLines().get(0).getOfferingReference());
    }

    @Test
    void rejectsInactiveCustomerAndNonPublishedOffering() {
        Customer inactiveCustomer = new Customer("customer-1", "Customer", CustomerState.INACTIVE,
                "iam:actor-1", null, 1L, now, now);
        assertError(CrmErrorCode.INVALID_STATE_TRANSITION, () -> service.create(actor(), inactiveCustomer, command(),
                Collections.singletonList(publishedOffering("offering-1", "CNY")), now, idGenerator()));

        Offering draftOffering = new Offering("offering-1", "offering-reference-1",
                "Offering", OfferingState.DRAFT, io.github.surezzzzzz.sdk.crm.server.core.domain.commercial
                .CommercialCapabilityType.FIXED_PRICE_FULFILLMENT_V1,
                "capability-1", "iam:actor-1", 1L, now, now);
        assertError(CrmErrorCode.INVALID_STATE_TRANSITION, () -> service.create(actor(), activeCustomer(), command(),
                Collections.singletonList(draftOffering), now, idGenerator()));
    }

    @Test
    void rejectsMissingCapabilityAndMixedSettlementCurrencies() {
        QuotationDraftService noCapabilityService = new QuotationDraftService(
                new CommercialCapabilityRegistry(Collections.emptyList()));
        assertError(CrmErrorCode.COMMERCIAL_CAPABILITY_UNAVAILABLE, () -> noCapabilityService.create(actor(),
                activeCustomer(), command(), Collections.singletonList(publishedOffering("offering-1", "CNY")),
                now, idGenerator()));

        CreateQuotationCommand mixedCurrencyCommand = new CreateQuotationCommand("customer-1",
                Instant.parse("2026-08-01T00:00:00Z"), Arrays.asList(
                line("offering-1", "CNY"), line("offering-2", "USD")));
        assertError(CrmErrorCode.VALIDATION_FAILED, () -> service.create(actor(), activeCustomer(),
                mixedCurrencyCommand, Arrays.asList(publishedOffering("offering-1", "CNY"),
                        publishedOffering("offering-2", "USD")), now, idGenerator()));
    }

    @Test
    void editsDraftWithSameVersionNumberAndBumpedAggregateVersion() {
        QuotationDraft created = service.create(actor(), activeCustomer(), command(),
                Collections.singletonList(publishedOffering("offering-1", "CNY")), now, idGenerator());
        EditQuotationCommand editCommand = new EditQuotationCommand(created.getQuotation().getQuotationId(),
                Instant.parse("2026-08-15T00:00:00Z"),
                Collections.singletonList(line("offering-2", "CNY")));
        QuotationDraft edited = service.edit(created.getQuotation(), created.getQuotationVersion(), editCommand,
                activeCustomer(), Collections.singletonList(publishedOffering("offering-2", "CNY")),
                Instant.parse("2026-07-30T00:00:00Z"), idGenerator());

        log.info("草稿编辑结果：版本号={}，聚合版本={}→{}，行引用={}",
                edited.getQuotationVersion().getVersion(), created.getQuotation().getAggregateVersion(),
                edited.getQuotation().getAggregateVersion(),
                edited.getQuotationVersion().getLines().get(0).getOfferingId());
        assertEquals(created.getQuotationVersion().getVersion(), edited.getQuotationVersion().getVersion(),
                "编辑不得改变草稿版本号");
        assertEquals(created.getQuotation().getAggregateVersion() + 1L,
                edited.getQuotation().getAggregateVersion(), "编辑必须递增聚合版本");
        assertEquals("offering-2", edited.getQuotationVersion().getLines().get(0).getOfferingId(),
                "编辑必须整单替换行项");
        assertEquals(Instant.parse("2026-08-15T00:00:00Z"), edited.getQuotationVersion().getValidUntil(),
                "编辑必须更新有效截止时间");
    }

    @Test
    void rejectsEditingIssuedVersion() {
        QuotationDraft created = service.create(actor(), activeCustomer(), command(),
                Collections.singletonList(publishedOffering("offering-1", "CNY")), now, idGenerator());
        Quotation issuedQuotation = created.getQuotation().issued(1, now);
        QuotationVersion issuedVersion = created.getQuotationVersion().issue(now, "iam:actor-1");
        EditQuotationCommand editCommand = new EditQuotationCommand("quotation-1",
                Instant.parse("2026-08-15T00:00:00Z"),
                Collections.singletonList(line("offering-1", "CNY")));

        log.info("已签发版本态={}", issuedVersion.getState());
        assertError(CrmErrorCode.INVALID_STATE_TRANSITION, () -> service.edit(issuedQuotation,
                issuedVersion, editCommand, activeCustomer(),
                Collections.singletonList(publishedOffering("offering-1", "CNY")),
                Instant.parse("2026-07-30T00:00:00Z"), idGenerator()));
    }

    private CrmOwner actor() {
        return CrmOwner.human("actor-1");
    }

    private Customer activeCustomer() {
        return new Customer("customer-1", "Customer", CustomerState.ACTIVE,
                "iam:actor-1", null, 1L, now, now);
    }

    private Offering publishedOffering(String offeringId, String currency) {
        return new Offering(offeringId, "offering-reference-" + offeringId.substring(9), "Offering",
                OfferingState.PUBLISHED, io.github.surezzzzzz.sdk.crm.server.core.domain.commercial
                .CommercialCapabilityType.FIXED_PRICE_FULFILLMENT_V1,
                "capability-1", "iam:actor-1", 1L, now, now);
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
