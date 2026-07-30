package io.github.surezzzzzz.sdk.crm.server.core.test.cases;

import io.github.surezzzzzz.sdk.crm.server.core.domain.commercial.FixedPriceCommercialTermsSnapshot;
import io.github.surezzzzzz.sdk.crm.server.core.domain.commercial.FulfillmentObligationTemplate;
import io.github.surezzzzzz.sdk.crm.server.core.domain.common.Money;
import io.github.surezzzzzz.sdk.crm.server.core.domain.fulfillment.FulfillmentConsumer;
import io.github.surezzzzzz.sdk.crm.server.core.domain.fulfillment.FulfillmentItem;
import io.github.surezzzzzz.sdk.crm.server.core.domain.fulfillment.FulfillmentState;
import io.github.surezzzzzz.sdk.crm.server.core.domain.order.Order;
import io.github.surezzzzzz.sdk.crm.server.core.domain.order.OrderDisplayState;
import io.github.surezzzzzz.sdk.crm.server.core.domain.order.OrderLine;
import io.github.surezzzzzz.sdk.crm.server.core.domain.quotation.*;
import io.github.surezzzzzz.sdk.crm.server.core.error.CrmErrorCode;
import io.github.surezzzzzz.sdk.crm.server.core.error.CrmException;
import io.github.surezzzzzz.sdk.crm.server.core.port.system.CrmIdGenerator;
import io.github.surezzzzzz.sdk.crm.server.core.port.system.FulfillmentConsumerSelector;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
class QuotationLifecycleServiceTest {

    private final QuotationLifecycleService service = new QuotationLifecycleService();
    private final Instant createdAt = Instant.parse("2026-07-29T00:00:00Z");

    @Test
    void issuesDraftQuotationAndFreezesConfirmedCommercialFacts() {
        QuotationIssuance issuance = service.issue(draftQuotation(), draftVersion(), createdAt, "actor-1");

        assertEquals(QuotationState.ISSUED, issuance.getQuotationVersion().getState());
        assertEquals(1, issuance.getQuotation().getCurrentConfirmableVersion().intValue());
        assertEquals(2L, issuance.getQuotation().getAggregateVersion());

        QuotationConfirmation confirmation = service.confirm(issuance.getQuotation(), issuance.getQuotationVersion(),
                Instant.parse("2026-07-30T00:00:00Z"), "actor-2", sequenceIdGenerator(), consumerSelector());

        log.info("报价确认结果：报价ID={}，订单ID={}，履约项数={}，消费者={}",
                confirmation.getQuotation().getQuotationId(), confirmation.getOrder().getOrderId(),
                confirmation.getFulfillmentItems().size(), confirmation.getFulfillmentItems().get(0).getConsumerId());
        assertEquals("order-1", confirmation.getOrder().getOrderId());
        assertEquals("quotation-1", confirmation.getOrder().getSourceQuotationId());
        assertEquals(1, confirmation.getOrder().getSourceQuotationVersion());
        assertEquals(new BigDecimal("59.70"), confirmation.getOrder().getTotalAmount().getAmount());
        assertEquals(1, confirmation.getOrder().getLines().size());
        assertEquals("quotation-line-1", confirmation.getOrder().getLines().get(0).getSourceQuotationLineId());
        assertEquals("offering-reference-1", confirmation.getOrder().getLines().get(0).getOfferingReference());
        assertEquals(QuotationState.CONFIRMED, confirmation.getQuotationVersion().getState());
        assertEquals("order-1", confirmation.getQuotation().getConfirmedOrderId());
        assertNull(confirmation.getQuotation().getCurrentConfirmableVersion());
        assertEquals(3L, confirmation.getQuotation().getAggregateVersion());
        assertEquals(1, confirmation.getFulfillmentItems().size());
        assertEquals("fulfillment-item-3", confirmation.getFulfillmentItems().get(0).getFulfillmentId());
        assertEquals("order-1", confirmation.getFulfillmentItems().get(0).getOrderId());
        assertEquals("order-line-2", confirmation.getFulfillmentItems().get(0).getOrderLineId());
        assertEquals("consumer-1", confirmation.getFulfillmentItems().get(0).getConsumerId());
        assertEquals(2, confirmation.getFulfillmentItems().get(0).getConsumerProtocolVersion());
        assertEquals(FulfillmentState.PENDING_DISPATCH, confirmation.getFulfillmentItems().get(0).getState());
    }

    @Test
    void rejectsExpiredQuotationBeforeIssuanceAndConfirmation() {
        CrmException issueException = assertThrows(CrmException.class, () -> service.issue(draftQuotation(),
                draftVersion(Instant.parse("2026-07-29T00:00:00Z")), createdAt, "actor-1"));
        log.info("过期报价签发拒绝结果：错误码={}", issueException.getErrorCode());
        assertEquals(CrmErrorCode.QUOTATION_EXPIRED, issueException.getErrorCode());

        CrmException confirmException = assertThrows(CrmException.class, () -> service.confirm(issuedQuotation(),
                issuedVersion(Instant.parse("2026-07-30T00:00:00Z")),
                Instant.parse("2026-07-30T00:00:00Z"), "actor-2", sequenceIdGenerator(), consumerSelector()));
        log.info("过期报价确认拒绝结果：错误码={}", confirmException.getErrorCode());
        assertEquals(CrmErrorCode.QUOTATION_EXPIRED, confirmException.getErrorCode());
    }

    @Test
    void rejectsAlreadyConfirmedQuotationAndInvalidConsumer() {
        CrmException confirmedException = assertThrows(CrmException.class, () -> service.confirm(
                confirmedQuotation(), issuedVersion(), Instant.parse("2026-07-30T00:00:00Z"), "actor-2",
                sequenceIdGenerator(), consumerSelector()));
        log.info("已确认报价拒绝结果：错误码={}", confirmedException.getErrorCode());
        assertEquals(CrmErrorCode.QUOTATION_ALREADY_CONFIRMED, confirmedException.getErrorCode());

        CrmException consumerException = assertThrows(CrmException.class, () -> service.confirm(issuedQuotation(),
                issuedVersion(), Instant.parse("2026-07-30T00:00:00Z"), "actor-2", sequenceIdGenerator(),
                (tenantId, template) -> new FulfillmentConsumer("consumer-2", tenantId, "other-capability", 1)));
        log.info("不匹配消费者拒绝结果：错误码={}", consumerException.getErrorCode());
        assertEquals(CrmErrorCode.INVALID_STATE_TRANSITION, consumerException.getErrorCode());
    }

    @Test
    void rejectsCrossTenantOrderAndDuplicateFulfillmentFacts() {
        QuotationConfirmation confirmation = confirmedConfirmation();
        Order crossTenantOrder = new Order(confirmation.getOrder().getOrderId(), "tenant-2", "quotation-1", 1,
                "customer-1", "CNY", confirmation.getOrder().getTotalAmount(), "actor-2", confirmation.getOrder()
                .getConfirmedAt(), 1L, OrderDisplayState.PENDING_FULFILLMENT, confirmation.getOrder().getLines());
        CrmException crossTenantException = assertThrows(CrmException.class, () -> new QuotationConfirmation(
                confirmation.getQuotation(), confirmation.getQuotationVersion(), crossTenantOrder,
                confirmation.getFulfillmentItems()));
        log.info("跨租户订单确认拒绝结果：错误码={}", crossTenantException.getErrorCode());
        assertEquals(CrmErrorCode.VALIDATION_FAILED, crossTenantException.getErrorCode());

        QuotationConfirmation twoLineConfirmation = twoLineConfirmedConfirmation();
        FulfillmentItem firstItem = twoLineConfirmation.getFulfillmentItems().get(0);
        FulfillmentItem secondItem = twoLineConfirmation.getFulfillmentItems().get(1);
        FulfillmentItem duplicateIdItem = new FulfillmentItem(firstItem.getFulfillmentId(), "tenant-1",
                twoLineConfirmation.getOrder().getOrderId(), secondItem.getOrderLineId(), 1,
                FulfillmentState.PENDING_DISPATCH, secondItem.getObligationTemplate(), "consumer-1", 2);
        CrmException duplicateIdException = assertThrows(CrmException.class, () -> new QuotationConfirmation(
                twoLineConfirmation.getQuotation(), twoLineConfirmation.getQuotationVersion(),
                twoLineConfirmation.getOrder(), Arrays.asList(firstItem, duplicateIdItem)));
        log.info("重复履约ID确认拒绝结果：错误码={}", duplicateIdException.getErrorCode());
        assertEquals(CrmErrorCode.VALIDATION_FAILED, duplicateIdException.getErrorCode());
    }

    @Test
    void rejectsIssuingIssuedVersionAndInconsistentVersionFacts() {
        CrmException issueException = assertThrows(CrmException.class, () -> service.issue(issuedQuotation(),
                issuedVersion(), createdAt, "actor-1"));
        log.info("重复签发拒绝结果：错误码={}", issueException.getErrorCode());
        assertEquals(CrmErrorCode.INVALID_STATE_TRANSITION, issueException.getErrorCode());

        CrmException totalException = assertThrows(CrmException.class, () -> new QuotationVersion("quotation-1", 1,
                QuotationState.DRAFT, "CNY", Instant.parse("2026-08-01T00:00:00Z"),
                Collections.singletonList(line()), new Money(new BigDecimal("59.71"), "CNY"),
                null, null, null, null));
        log.info("报价总额不一致拒绝结果：错误码={}", totalException.getErrorCode());
        assertEquals(CrmErrorCode.VALIDATION_FAILED, totalException.getErrorCode());
    }

    private QuotationConfirmation confirmedConfirmation() {
        return service.confirm(issuedQuotation(), issuedVersion(), Instant.parse("2026-07-30T00:00:00Z"), "actor-2",
                sequenceIdGenerator(), consumerSelector());
    }

    private QuotationConfirmation twoLineConfirmedConfirmation() {
        Quotation quotation = new Quotation("quotation-1", "tenant-1", "customer-1", "actor-1", 3L, 1,
                null, "order-1", createdAt, createdAt);
        QuotationLine firstLine = line();
        QuotationLine secondLine = new QuotationLine("quotation-line-2", "offering-2", "offering-reference-2",
                new BigDecimal("3"), "SET", new Money(new BigDecimal("19.90"), "CNY"),
                new Money(new BigDecimal("59.70"), "CNY"), new FixedPriceCommercialTermsSnapshot(
                new BigDecimal("3"), "SET", new Money(new BigDecimal("19.90"), "CNY")),
                new FulfillmentObligationTemplate("subject-2", "scope-2", "capability-1"));
        QuotationVersion version = new QuotationVersion("quotation-1", 1, QuotationState.CONFIRMED, "CNY",
                Instant.parse("2026-08-01T00:00:00Z"), Arrays.asList(firstLine, secondLine),
                new Money(new BigDecimal("119.40"), "CNY"), createdAt, "actor-1",
                Instant.parse("2026-07-30T00:00:00Z"), "actor-2");
        OrderLine firstOrderLine = new OrderLine("order-line-1", "quotation-line-1", "offering-reference-1",
                new BigDecimal("3"), "SET", new Money(new BigDecimal("19.90"), "CNY"),
                new Money(new BigDecimal("59.70"), "CNY"), new FixedPriceCommercialTermsSnapshot(
                new BigDecimal("3"), "SET", new Money(new BigDecimal("19.90"), "CNY")),
                firstLine.getFulfillmentObligationTemplate());
        OrderLine secondOrderLine = new OrderLine("order-line-2", "quotation-line-2", "offering-reference-2",
                new BigDecimal("3"), "SET", new Money(new BigDecimal("19.90"), "CNY"),
                new Money(new BigDecimal("59.70"), "CNY"), new FixedPriceCommercialTermsSnapshot(
                new BigDecimal("3"), "SET", new Money(new BigDecimal("19.90"), "CNY")),
                secondLine.getFulfillmentObligationTemplate());
        Order order = new Order("order-1", "tenant-1", "quotation-1", 1, "customer-1", "CNY",
                new Money(new BigDecimal("119.40"), "CNY"), "actor-2", Instant.parse("2026-07-30T00:00:00Z"),
                1L, OrderDisplayState.PENDING_FULFILLMENT, Arrays.asList(firstOrderLine, secondOrderLine));
        FulfillmentItem firstItem = new FulfillmentItem("fulfillment-1", "tenant-1", "order-1", "order-line-1", 1,
                FulfillmentState.PENDING_DISPATCH, firstLine.getFulfillmentObligationTemplate(), "consumer-1", 2);
        FulfillmentItem secondItem = new FulfillmentItem("fulfillment-2", "tenant-1", "order-1", "order-line-2", 1,
                FulfillmentState.PENDING_DISPATCH, secondLine.getFulfillmentObligationTemplate(), "consumer-1", 2);
        return new QuotationConfirmation(quotation, version, order, Arrays.asList(firstItem, secondItem));
    }

    private Quotation draftQuotation() {
        return new Quotation("quotation-1", "tenant-1", "customer-1", "actor-1", 1L, 1,
                null, null, createdAt, createdAt);
    }

    private Quotation issuedQuotation() {
        return new Quotation("quotation-1", "tenant-1", "customer-1", "actor-1", 2L, 1,
                1, null, createdAt, createdAt);
    }

    private Quotation confirmedQuotation() {
        return new Quotation("quotation-1", "tenant-1", "customer-1", "actor-1", 3L, 1,
                null, "order-1", createdAt, createdAt);
    }

    private QuotationVersion draftVersion() {
        return draftVersion(Instant.parse("2026-08-01T00:00:00Z"));
    }

    private QuotationVersion draftVersion(Instant validUntil) {
        return new QuotationVersion("quotation-1", 1, QuotationState.DRAFT, "CNY", validUntil,
                Collections.singletonList(line()), new Money(new BigDecimal("59.70"), "CNY"),
                null, null, null, null);
    }

    private QuotationVersion issuedVersion() {
        return issuedVersion(Instant.parse("2026-08-01T00:00:00Z"));
    }

    private QuotationVersion issuedVersion(Instant validUntil) {
        return new QuotationVersion("quotation-1", 1, QuotationState.ISSUED, "CNY", validUntil,
                Collections.singletonList(line()), new Money(new BigDecimal("59.70"), "CNY"),
                createdAt, "actor-1", null, null);
    }

    private QuotationLine line() {
        Money unitPrice = new Money(new BigDecimal("19.90"), "CNY");
        return new QuotationLine("quotation-line-1", "offering-1", "offering-reference-1",
                new BigDecimal("3"), "SET", unitPrice, new Money(new BigDecimal("59.70"), "CNY"),
                new FixedPriceCommercialTermsSnapshot(new BigDecimal("3"), "SET", unitPrice),
                new FulfillmentObligationTemplate("subject-1", "scope-1", "capability-1"));
    }

    private CrmIdGenerator sequenceIdGenerator() {
        AtomicLong sequence = new AtomicLong();
        return resourceType -> resourceType.name().toLowerCase().replace('_', '-')
                + "-" + sequence.incrementAndGet();
    }

    private FulfillmentConsumerSelector consumerSelector() {
        return (tenantId, template) -> new FulfillmentConsumer("consumer-1", tenantId,
                template.getRequiredConsumerCapability(), 2);
    }
}
