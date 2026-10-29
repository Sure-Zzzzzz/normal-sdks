package io.github.surezzzzzz.sdk.crm.server.core.test.cases;

import io.github.surezzzzzz.sdk.crm.server.core.domain.commercial.FixedPriceCommercialTermsSnapshot;
import io.github.surezzzzzz.sdk.crm.server.core.domain.commercial.FulfillmentObligationTemplate;
import io.github.surezzzzzz.sdk.crm.server.core.domain.common.Money;
import io.github.surezzzzzz.sdk.crm.server.core.domain.fulfillment.FulfillmentState;
import io.github.surezzzzzz.sdk.crm.server.core.domain.quotation.*;
import io.github.surezzzzzz.sdk.crm.server.core.error.CrmErrorCode;
import io.github.surezzzzzz.sdk.crm.server.core.error.CrmException;
import io.github.surezzzzzz.sdk.crm.server.core.port.system.CrmIdGenerator;
import io.github.surezzzzzz.sdk.crm.server.core.port.system.FulfillmentConsumerSelector;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 报价签发、确认、撤回、重议与取代生命周期测试。
 *
 * @author surezzzzzz
 */
@Slf4j
class QuotationLifecycleServiceTest {

    private final QuotationLifecycleService service = new QuotationLifecycleService();
    private final Instant now = Instant.parse("2026-07-30T00:00:00Z");

    @Test
    void issuesDraftAndFreezesIssuanceFacts() {
        QuotationIssuance issuance = service.issue(draftQuotation(), draftVersion(), now, "iam:actor-1");

        log.info("签发结果：聚合可确认版本={}，版本态={}",
                issuance.getQuotation().getCurrentConfirmableVersion(),
                issuance.getQuotationVersion().getState());
        assertEquals(Integer.valueOf(1), issuance.getQuotation().getCurrentConfirmableVersion(),
                "签发后当前版本可确认");
        assertEquals(QuotationState.ISSUED, issuance.getQuotationVersion().getState());
        assertEquals("iam:actor-1", issuance.getQuotationVersion().getIssuedBy());
    }

    @Test
    void confirmCreatesOrderAndFulfillmentItemsInheritingOwner() {
        QuotationConfirmation confirmation = service.confirm(issuedQuotation(), issuedVersion(), now,
                "iam:actor-1", idGenerator(), consumerSelector());

        log.info("确认结果：订单ID={}，订单行数={}，履约项数={}，履约owner={}，消费者={}",
                confirmation.getOrder().getOrderId(), confirmation.getOrder().getLines().size(),
                confirmation.getFulfillmentItems().size(),
                confirmation.getFulfillmentItems().get(0).getOwner(),
                confirmation.getFulfillmentItems().get(0).getConsumerId());
        assertEquals(1, confirmation.getOrder().getLines().size());
        assertEquals(1, confirmation.getFulfillmentItems().size());
        assertEquals(FulfillmentState.PENDING_DISPATCH, confirmation.getFulfillmentItems().get(0).getState());
        assertEquals("iam:actor-1", confirmation.getFulfillmentItems().get(0).getOwner(),
                "履约项必须继承报价 owner（人员查询维度）");
        assertEquals(0, confirmation.getFulfillmentItems().get(0).getReportSequence(), "初始回写水位为 0");
        assertNull(confirmation.getSupersededQuotation(), "非重议链确认不得携带取代事实");
    }

    @Test
    void confirmRejectsExpiredIssuedQuotationLazily() {
        Quotation expiredQuotation = issuedQuotation();
        QuotationVersion expiredVersion = draftVersion();
        Instant late = Instant.parse("2026-08-02T00:00:00Z");

        CrmException exception = assertThrows(CrmException.class, () -> service.confirm(expiredQuotation,
                expiredVersion, late, "iam:actor-1", idGenerator(), consumerSelector()));
        log.info("过期确认拦截结果：错误码={}", exception.getErrorCode());
        assertEquals(CrmErrorCode.QUOTATION_EXPIRED, exception.getErrorCode(), "过期报价确认必须被拒绝");

        QuotationVersion issued = expiredVersion.issue(now, "iam:actor-1");
        assertTrue(issued.isExpired(late), "惰性过期谓词按版本有效截止时间判定");
        assertFalse(issued.isExpired(Instant.parse("2026-07-31T00:00:00Z")));
    }

    @Test
    void withdrawsIssuedQuotationToTerminalWithdrawnState() {
        QuotationWithdrawal withdrawal = service.withdraw(issuedQuotation(), issuedVersion(), now, "iam:actor-1");

        log.info("撤回结果：聚合可确认版本={}，版本态={}",
                withdrawal.getQuotation().getCurrentConfirmableVersion(),
                withdrawal.getQuotationVersion().getState());
        assertNull(withdrawal.getQuotation().getCurrentConfirmableVersion(), "撤回后不可再确认");
        assertEquals(QuotationState.WITHDRAWN, withdrawal.getQuotationVersion().getState());
        assertEquals("iam:actor-1", withdrawal.getQuotationVersion().getTerminalBy());
    }

    @Test
    void rejectsWithdrawingDraftOrConfirmedQuotation() {
        assertLifecycleError(CrmErrorCode.INVALID_STATE_TRANSITION, () -> service.withdraw(draftQuotation(),
                draftVersion(), now, "iam:actor-1"));
        QuotationConfirmation confirmation = service.confirm(issuedQuotation(), issuedVersion(), now,
                "iam:actor-1", idGenerator(), consumerSelector());
        assertLifecycleError(CrmErrorCode.INVALID_STATE_TRANSITION, () -> service.withdraw(
                confirmation.getQuotation(), confirmation.getQuotationVersion(), now, "iam:actor-1"));
    }

    @Test
    void expiredIssuedQuotationCannotBeWithdrawn() {
        Instant late = Instant.parse("2026-08-02T00:00:00Z");
        CrmException exception = assertThrows(CrmException.class, () -> service.withdraw(
                issuedQuotation(), issuedVersion(), late, "iam:actor-1"));
        assertEquals(CrmErrorCode.QUOTATION_EXPIRED, exception.getErrorCode());
    }

    @Test
    void renegotiateDerivesDraftFromIssuedSnapshotWithSupersedeLink() {
        QuotationDraft derived = service.renegotiate(issuedQuotation(), issuedVersion(), now, idGenerator());

        log.info("重议派生结果：新报价ID={}，取代来源={}，版本态={}，行数={}",
                derived.getQuotation().getQuotationId(),
                derived.getQuotation().getSupersedesQuotationId(),
                derived.getQuotationVersion().getState(), derived.getQuotationVersion().getLines().size());
        assertTrue(derived.getQuotation().getQuotationId().startsWith("quotation-")
                        && !derived.getQuotation().getQuotationId().equals("quotation-1"),
                "派生报价必须获得全新标识");
        assertEquals("quotation-1", derived.getQuotation().getSupersedesQuotationId(),
                "派生草稿必须记录取代来源");
        assertEquals(QuotationState.DRAFT, derived.getQuotationVersion().getState());
        assertEquals(1, derived.getQuotationVersion().getLines().size(), "行快照必须完整复制");
        assertEquals("iam:actor-1", derived.getQuotation().getOwner());
    }

    @Test
    void expiredIssuedQuotationCannotBeRenegotiated() {
        Instant late = Instant.parse("2026-08-02T00:00:00Z");
        CrmException exception = assertThrows(CrmException.class, () -> service.renegotiate(
                issuedQuotation(), issuedVersion(), late, idGenerator()));
        assertEquals(CrmErrorCode.QUOTATION_EXPIRED, exception.getErrorCode());
    }

    @Test
    void renegotiateCarriesOpportunityLinkIntoDerivedQuotation() {
        Quotation source = new Quotation("quotation-opportunity-1", "customer-1", "opportunity-1",
                "iam:actor-1", 1L, 1, 1, null, null, now.minusSeconds(1), now.minusSeconds(1));
        QuotationVersion sourceVersion = new QuotationVersion("quotation-opportunity-1", 1,
                QuotationState.DRAFT, "CNY", now.plusSeconds(172800L), draftVersion().getLines(),
                draftVersion().getTotalAmount(), null, null, null, null).issue(now, "iam:actor-1");

        QuotationDraft derived = service.renegotiate(source, sourceVersion, now, idGenerator());

        log.info("重议商机归属：sourceOpportunityId={}，derivedOpportunityId={}",
                source.getOpportunityId(), derived.getQuotation().getOpportunityId());
        assertEquals("opportunity-1", derived.getQuotation().getOpportunityId(),
                "重议报价必须沿用来源报价的商机归属");
    }

    @Test
    void confirmingDerivedDraftSupersedesSourceInSameResult() {
        Quotation source = issuedQuotation();
        QuotationVersion sourceVersion = issuedVersion();
        QuotationDraft derived = service.renegotiate(source, sourceVersion, now, idGenerator());
        Quotation derivedIssued = derived.getQuotation().issued(1, now);
        QuotationVersion derivedIssuedVersion = derived.getQuotationVersion().issue(now, "iam:actor-1");

        QuotationConfirmation confirmation = service.confirm(derivedIssued, derivedIssuedVersion, now,
                "iam:actor-1", idGenerator(), consumerSelector(), source, sourceVersion);

        log.info("取代确认结果：新订单ID={}，被取代报价={}，被取代版本态={}",
                confirmation.getOrder().getOrderId(),
                confirmation.getSupersededQuotation().getQuotationId(),
                confirmation.getSupersededQuotationVersion().getState());
        assertEquals(QuotationState.SUPERSEDED, confirmation.getSupersededQuotationVersion().getState(),
                "来源版本必须同结果置 SUPERSEDED");
        assertNull(confirmation.getSupersededQuotation().getCurrentConfirmableVersion(),
                "来源聚合撤回可确认性");
    }

    @Test
    void rejectsConfirmingDerivedDraftWithoutSupersedeTarget() {
        QuotationDraft derived = service.renegotiate(issuedQuotation(), issuedVersion(), now, idGenerator());
        Quotation derivedIssued = derived.getQuotation().issued(1, now);
        QuotationVersion derivedIssuedVersion = derived.getQuotationVersion().issue(now, "iam:actor-1");

        CrmException exception = assertThrows(CrmException.class, () -> service.confirm(derivedIssued,
                derivedIssuedVersion, now, "iam:actor-1", idGenerator(), consumerSelector()));
        log.info("缺取代目标确认拦截结果：错误码={}", exception.getErrorCode());
        assertEquals(CrmErrorCode.INVALID_STATE_TRANSITION, exception.getErrorCode(),
                "重议链确认必须携带被取代旧报价事实");
    }

    @Test
    void supersedeTargetMustBeIssued() {
        QuotationDraft derived = service.renegotiate(issuedQuotation(), issuedVersion(), now, idGenerator());
        Quotation derivedIssued = derived.getQuotation().issued(1, now);
        QuotationVersion derivedIssuedVersion = derived.getQuotationVersion().issue(now, "iam:actor-1");

        CrmException exception = assertThrows(CrmException.class, () -> service.confirm(derivedIssued,
                derivedIssuedVersion, now, "iam:actor-1", idGenerator(), consumerSelector(),
                issuedQuotation(), draftVersion()));
        log.info("非签发取代目标拦截结果：错误码={}", exception.getErrorCode());
        assertEquals(CrmErrorCode.INVALID_STATE_TRANSITION, exception.getErrorCode());
    }

    private Quotation draftQuotation() {
        return new Quotation("quotation-1", "customer-1", "iam:actor-1", 1L, 1,
                null, null, null, Instant.parse("2026-07-29T00:00:00Z"),
                Instant.parse("2026-07-29T00:00:00Z"));
    }

    private Quotation issuedQuotation() {
        return draftQuotation().issued(1, now);
    }

    private QuotationVersion draftVersion() {
        Money unitPrice = new Money(new BigDecimal("19.90"), "CNY");
        QuotationLine line = new QuotationLine("quotation-line-1", "offering-1", "offering-reference-1",
                new BigDecimal("3"), "SET", unitPrice, new Money(new BigDecimal("59.70"), "CNY"),
                new FixedPriceCommercialTermsSnapshot(new BigDecimal("3"), "SET", unitPrice),
                new FulfillmentObligationTemplate("subject-1", "scope-1", "capability-1"));
        return new QuotationVersion("quotation-1", 1, QuotationState.DRAFT, "CNY",
                Instant.parse("2026-08-01T00:00:00Z"), Collections.singletonList(line),
                new Money(new BigDecimal("59.70"), "CNY"), null, null, null, null);
    }

    private QuotationVersion issuedVersion() {
        return draftVersion().issue(now, "iam:actor-1");
    }

    private CrmIdGenerator idGenerator() {
        AtomicLong sequence = new AtomicLong();
        return resourceType -> resourceType.name().toLowerCase().replace('_', '-')
                + "-" + resourceType.ordinal() + "-" + sequence.incrementAndGet();
    }

    private FulfillmentConsumerSelector consumerSelector() {
        return template -> new io.github.surezzzzzz.sdk.crm.server.core.domain.fulfillment.FulfillmentConsumer(
                "consumer-1", template.getRequiredConsumerCapability(), 2);
    }

    private void assertLifecycleError(CrmErrorCode errorCode, ThrowingRunnable runnable) {
        CrmException exception = assertThrows(CrmException.class, runnable::run);
        log.info("生命周期拒绝结果：期望错误码={}，实际错误码={}", errorCode, exception.getErrorCode());
        assertEquals(errorCode, exception.getErrorCode());
    }

    private interface ThrowingRunnable {
        void run();
    }
}
