package io.github.surezzzzzz.sdk.crm.server.core.test.cases;

import io.github.surezzzzzz.sdk.crm.server.core.command.CrmCommandMetadata;
import io.github.surezzzzzz.sdk.crm.server.core.domain.commercial.FixedPriceCommercialTermsSnapshot;
import io.github.surezzzzzz.sdk.crm.server.core.domain.commercial.FulfillmentObligationTemplate;
import io.github.surezzzzzz.sdk.crm.server.core.domain.common.Money;
import io.github.surezzzzzz.sdk.crm.server.core.domain.fulfillment.FulfillmentConsumer;
import io.github.surezzzzzz.sdk.crm.server.core.domain.fulfillment.FulfillmentItem;
import io.github.surezzzzzz.sdk.crm.server.core.domain.identity.CrmActor;
import io.github.surezzzzzz.sdk.crm.server.core.domain.order.Order;
import io.github.surezzzzzz.sdk.crm.server.core.domain.order.OrderLine;
import io.github.surezzzzzz.sdk.crm.server.core.domain.quotation.*;
import io.github.surezzzzzz.sdk.crm.server.core.domain.type.CrmCommandType;
import io.github.surezzzzzz.sdk.crm.server.core.domain.type.CrmResourceType;
import io.github.surezzzzzz.sdk.crm.server.core.error.CrmErrorCode;
import io.github.surezzzzzz.sdk.crm.server.core.error.CrmException;
import io.github.surezzzzzz.sdk.crm.server.core.port.idempotency.CrmReplayableIdempotencyPort;
import io.github.surezzzzzz.sdk.crm.server.core.port.repository.ReplayableFulfillmentItemRepository;
import io.github.surezzzzzz.sdk.crm.server.core.port.system.FulfillmentConsumerSelector;
import io.github.surezzzzzz.sdk.crm.server.core.support.CrmValidationHelper;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 已知目标命令可重放幂等端口契约测试。
 *
 * @author surezzzzzz
 */
@Slf4j
class CrmReplayableIdempotencyPortContractTest {

    private static final String REQUEST_DIGEST_A = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String REQUEST_DIGEST_B = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

    private static FulfillmentObligationTemplate copyObligationTemplate(FulfillmentObligationTemplate template) {
        return new FulfillmentObligationTemplate(template.getSubjectReference(), template.getFulfillmentScope(),
                template.getRequiredConsumerCapability());
    }

    @Test
    void replaysQuotationIssuanceWithoutExecutingFirstCallbackAgain() {
        InMemoryReplayableIdempotencyPort port = new InMemoryReplayableIdempotencyPort();
        QuotationLifecycleService service = new QuotationLifecycleService();
        AtomicInteger firstCallbackCount = new AtomicInteger();
        AtomicInteger replayCallbackCount = new AtomicInteger();
        AtomicReference<QuotationIssuance> committedIssuance = new AtomicReference<QuotationIssuance>();

        QuotationIssuance first = port.execute(actor(), metadata(), CrmCommandType.ISSUE_QUOTATION,
                CrmResourceType.QUOTATION, "quotation-1", REQUEST_DIGEST_A, () -> {
                    firstCallbackCount.incrementAndGet();
                    QuotationIssuance issuance = service.issue(draftQuotation(), draftVersion(), createdAt(), "actor-1");
                    committedIssuance.set(issuance);
                    return issuance;
                }, () -> rehydrateIssuance(committedIssuance.get(), replayCallbackCount));
        QuotationIssuance replayed = port.execute(actor(), metadata(), CrmCommandType.ISSUE_QUOTATION,
                CrmResourceType.QUOTATION, "quotation-1", REQUEST_DIGEST_A,
                () -> {
                    firstCallbackCount.incrementAndGet();
                    throw new AssertionError("重放不得执行报价签发首次回调");
                }, () -> rehydrateIssuance(committedIssuance.get(), replayCallbackCount));

        log.info("报价签发幂等重放：报价ID={}，版本={}，首次回调次数={}，重放回调次数={}",
                replayed.getQuotation().getQuotationId(), replayed.getQuotationVersion().getVersion(),
                firstCallbackCount.get(), replayCallbackCount.get());
        assertEquals(first.getQuotation().getQuotationId(), replayed.getQuotation().getQuotationId(),
                "重放必须按已提交报价事实重建同一签发结果");
        assertEquals(QuotationState.ISSUED, replayed.getQuotationVersion().getState(), "重放结果必须是已签发版本");
        assertNotSame(first.getQuotation(), replayed.getQuotation(), "重放必须重新加载报价权威事实");
        assertNotSame(first.getQuotationVersion(), replayed.getQuotationVersion(), "重放必须重新加载报价版本事实");
        assertEquals(1, firstCallbackCount.get(), "重放报价签发不得再次执行状态迁移回调");
        assertEquals(1, replayCallbackCount.get(), "重放必须执行声明结果重建回调");
    }

    @Test
    void replaysQuotationConfirmationWithoutExecutingOrderCreationAgain() {
        InMemoryReplayableIdempotencyPort port = new InMemoryReplayableIdempotencyPort();
        QuotationLifecycleService service = new QuotationLifecycleService();
        QuotationIssuance issuance = service.issue(draftQuotation(), draftVersion(), createdAt(), "actor-1");
        AtomicInteger firstCallbackCount = new AtomicInteger();
        AtomicInteger replayCallbackCount = new AtomicInteger();
        AtomicReference<QuotationConfirmation> committedConfirmation = new AtomicReference<QuotationConfirmation>();

        QuotationConfirmation first = port.execute(actor(), metadata(), CrmCommandType.CONFIRM_QUOTATION,
                CrmResourceType.QUOTATION, "quotation-1", REQUEST_DIGEST_A, () -> {
                    firstCallbackCount.incrementAndGet();
                    QuotationConfirmation confirmation = service.confirm(issuance.getQuotation(),
                            issuance.getQuotationVersion(), confirmedAt(), "actor-2", resourceType -> resourceType.getCode()
                                    + "-1", consumerSelector());
                    committedConfirmation.set(confirmation);
                    return confirmation;
                }, () -> rehydrateConfirmation(committedConfirmation.get(), replayCallbackCount));
        QuotationConfirmation replayed = port.execute(actor(), metadata(), CrmCommandType.CONFIRM_QUOTATION,
                CrmResourceType.QUOTATION, "quotation-1", REQUEST_DIGEST_A,
                () -> {
                    firstCallbackCount.incrementAndGet();
                    throw new AssertionError("重放不得执行报价确认首次回调");
                }, () -> rehydrateConfirmation(committedConfirmation.get(), replayCallbackCount));

        log.info("报价确认幂等重放：订单ID={}，履约项数={}，首次回调次数={}，重放回调次数={}",
                replayed.getOrder().getOrderId(), replayed.getFulfillmentItems().size(), firstCallbackCount.get(),
                replayCallbackCount.get());
        assertEquals(first.getOrder().getOrderId(), replayed.getOrder().getOrderId(),
                "重放必须按已提交订单与履约事实重建同一确认结果");
        assertEquals(first.getFulfillmentItems().size(), replayed.getFulfillmentItems().size(),
                "重放必须恢复完整履约事实集合");
        assertNotSame(first.getQuotation(), replayed.getQuotation(), "重放必须重新加载已确认报价事实");
        assertNotSame(first.getQuotationVersion(), replayed.getQuotationVersion(), "重放必须重新加载已确认报价版本");
        assertNotSame(first.getOrder(), replayed.getOrder(), "重放必须重新加载订单事实");
        assertNotSame(first.getFulfillmentItems().get(0), replayed.getFulfillmentItems().get(0),
                "重放必须重新加载履约项事实");
        assertEquals(1, firstCallbackCount.get(), "重放报价确认不得再次创建订单或履约项");
        assertEquals(1, replayCallbackCount.get(), "重放必须执行确认结果重建回调");
    }

    @Test
    void readsCommittedFulfillmentItemsByTenantAndOrderForConfirmationReplay() {
        QuotationLifecycleService service = new QuotationLifecycleService();
        QuotationIssuance issuance = service.issue(draftQuotation(), draftVersion(), createdAt(), "actor-1");
        QuotationConfirmation confirmation = service.confirm(issuance.getQuotation(), issuance.getQuotationVersion(),
                confirmedAt(), "actor-2", resourceType -> resourceType.getCode() + "-1", consumerSelector());
        InMemoryFulfillmentItemRepository repository = new InMemoryFulfillmentItemRepository();
        for (FulfillmentItem fulfillmentItem : confirmation.getFulfillmentItems()) {
            repository.insert(fulfillmentItem.getTenantId(), fulfillmentItem);
        }

        List<FulfillmentItem> reloaded = repository.findByOrderId("tenant-1", confirmation.getOrder().getOrderId());
        List<FulfillmentItem> crossTenant = repository.findByOrderId("tenant-2", confirmation.getOrder().getOrderId());

        log.info("报价确认重放履约读取：订单ID={}，履约项数={}", confirmation.getOrder().getOrderId(), reloaded.size());
        assertEquals(confirmation.getFulfillmentItems().size(), reloaded.size(), "重放必须读取订单的完整履约事实集合");
        assertNotSame(confirmation.getFulfillmentItems().get(0), reloaded.get(0), "重放必须重新加载履约项权威事实");
        assertEquals(Collections.emptyList(), crossTenant, "履约项读取不得跨租户泄漏");
    }

    @Test
    void rejectsDifferentDigestWithoutExecutingAnyCallback() {
        InMemoryReplayableIdempotencyPort port = new InMemoryReplayableIdempotencyPort();
        AtomicInteger callbackCount = new AtomicInteger();
        port.execute(actor(), metadata(), CrmCommandType.ISSUE_QUOTATION, CrmResourceType.QUOTATION,
                "quotation-1", REQUEST_DIGEST_A, () -> firstResult(callbackCount, "issued-version-1"),
                () -> replayResult(callbackCount, "issued-version-1"));

        CrmException exception = assertThrows(CrmException.class, () -> port.execute(actor(), metadata(),
                CrmCommandType.ISSUE_QUOTATION, CrmResourceType.QUOTATION, "quotation-1", REQUEST_DIGEST_B,
                () -> firstResult(callbackCount, "wrong"), () -> replayResult(callbackCount, "wrong")));

        log.info("已知目标幂等摘要冲突：错误码={}，回调次数={}", exception.getErrorCode(), callbackCount.get());
        assertEquals(CrmErrorCode.IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_PAYLOAD, exception.getErrorCode(),
                "同一稳定范围使用不同摘要必须拒绝");
        assertEquals(1, callbackCount.get(), "摘要冲突不得执行首次或重放回调");
    }

    @Test
    void rejectsInvalidKnownTargetCommandResourcePair() {
        InMemoryReplayableIdempotencyPort port = new InMemoryReplayableIdempotencyPort();

        CrmException createException = assertThrows(CrmException.class, () -> port.execute(actor(), metadata(),
                CrmCommandType.CREATE_QUOTATION, CrmResourceType.QUOTATION, "quotation-1", REQUEST_DIGEST_A,
                () -> "wrong", () -> "wrong"));
        CrmException resourceException = assertThrows(CrmException.class, () -> port.execute(actor(), metadata(),
                CrmCommandType.ISSUE_QUOTATION, CrmResourceType.ORDER, "order-1", REQUEST_DIGEST_A,
                () -> "wrong", () -> "wrong"));

        log.info("已知目标命令资源配对校验：创建命令错误码={}，资源错误码={}", createException.getErrorCode(),
                resourceException.getErrorCode());
        assertEquals(CrmErrorCode.VALIDATION_FAILED, createException.getErrorCode(),
                "创建命令不得使用已知目标可重放幂等端口");
        assertEquals(CrmErrorCode.VALIDATION_FAILED, resourceException.getErrorCode(),
                "报价签发只能使用报价目标资源类型");
    }

    @Test
    void keepsCommittedSuccessWhenReplayCallbackFails() {
        InMemoryReplayableIdempotencyPort port = new InMemoryReplayableIdempotencyPort();
        AtomicInteger firstCallbackCount = new AtomicInteger();
        AtomicInteger replayCallbackCount = new AtomicInteger();
        port.execute(actor(), metadata(), CrmCommandType.ISSUE_QUOTATION, CrmResourceType.QUOTATION,
                "quotation-1", REQUEST_DIGEST_A, () -> firstResult(firstCallbackCount, "issued-version-1"),
                () -> replayResult(replayCallbackCount, "issued-version-1"));

        assertThrows(IllegalStateException.class, () -> port.execute(actor(), metadata(), CrmCommandType.ISSUE_QUOTATION,
                CrmResourceType.QUOTATION, "quotation-1", REQUEST_DIGEST_A,
                () -> firstResult(firstCallbackCount, "wrong"), () -> {
                    replayCallbackCount.incrementAndGet();
                    throw new IllegalStateException("重放结果加载失败");
                }));
        String replayed = port.execute(actor(), metadata(), CrmCommandType.ISSUE_QUOTATION,
                CrmResourceType.QUOTATION, "quotation-1", REQUEST_DIGEST_A,
                () -> firstResult(firstCallbackCount, "wrong"),
                () -> replayResult(replayCallbackCount, "issued-version-1"));

        log.info("重放失败后再次重放结果={}，首次回调次数={}，重放回调次数={}", replayed,
                firstCallbackCount.get(), replayCallbackCount.get());
        assertEquals("issued-version-1", replayed, "重放失败不得删除或改写既有成功记录");
        assertEquals(1, firstCallbackCount.get(), "重放失败后不得重新执行首次回调");
        assertEquals(2, replayCallbackCount.get(), "后续请求必须仍按既有成功记录进入重放回调");
    }

    @Test
    void isolatesTenantActorCommandTargetAndIdempotencyKey() {
        InMemoryReplayableIdempotencyPort port = new InMemoryReplayableIdempotencyPort();
        AtomicInteger firstCallbackCount = new AtomicInteger();

        execute(port, actor("tenant-1", "actor-1"), metadata("key-1"), CrmCommandType.ISSUE_QUOTATION,
                "quotation-1", firstCallbackCount);
        execute(port, actor("tenant-2", "actor-1"), metadata("key-1"), CrmCommandType.ISSUE_QUOTATION,
                "quotation-1", firstCallbackCount);
        execute(port, actor("tenant-1", "actor-2"), metadata("key-1"), CrmCommandType.ISSUE_QUOTATION,
                "quotation-1", firstCallbackCount);
        execute(port, actor("tenant-1", "actor-1"), metadata("key-1"), CrmCommandType.CONFIRM_QUOTATION,
                "quotation-1", firstCallbackCount);
        execute(port, actor("tenant-1", "actor-1"), metadata("key-1"), CrmCommandType.ISSUE_QUOTATION,
                "quotation-2", firstCallbackCount);
        execute(port, actor("tenant-1", "actor-1"), metadata("key-2"), CrmCommandType.ISSUE_QUOTATION,
                "quotation-1", firstCallbackCount);

        log.info("已知目标幂等稳定范围隔离：独立首次回调次数={}", firstCallbackCount.get());
        assertEquals(6, firstCallbackCount.get(), "稳定范围任一维度不同都不得重放其他已知目标记录");
    }

    @Test
    void doesNotPersistSuccessWhenFirstCallbackFails() {
        InMemoryReplayableIdempotencyPort port = new InMemoryReplayableIdempotencyPort();
        AtomicInteger callbackCount = new AtomicInteger();
        assertThrows(IllegalStateException.class, () -> port.execute(actor(), metadata(),
                CrmCommandType.CONFIRM_QUOTATION, CrmResourceType.QUOTATION, "quotation-1", REQUEST_DIGEST_A,
                () -> {
                    callbackCount.incrementAndGet();
                    throw new IllegalStateException("首次确认失败");
                }, () -> replayResult(callbackCount, "wrong")));

        String result = port.execute(actor(), metadata(), CrmCommandType.CONFIRM_QUOTATION,
                CrmResourceType.QUOTATION, "quotation-1", REQUEST_DIGEST_A,
                () -> firstResult(callbackCount, "order-1|fulfillment-1"),
                () -> replayResult(callbackCount, "order-1|fulfillment-1"));
        log.info("首次失败后重试结果={}，首次回调次数={}", result, callbackCount.get());
        assertEquals("order-1|fulfillment-1", result, "首次失败不得留下可重放成功结果");
        assertEquals(2, callbackCount.get(), "失败后重试必须重新执行首次回调");
    }

    @Test
    void convergesConcurrentFirstConfirmationToOneResult() throws Exception {
        InMemoryReplayableIdempotencyPort port = new InMemoryReplayableIdempotencyPort();
        AtomicInteger firstCallbackCount = new AtomicInteger();
        AtomicInteger replayCallbackCount = new AtomicInteger();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<String> first = executor.submit(() -> executeConcurrent(port, firstCallbackCount, replayCallbackCount,
                    ready, start));
            Future<String> second = executor.submit(() -> executeConcurrent(port, firstCallbackCount, replayCallbackCount,
                    ready, start));
            ready.await();
            start.countDown();
            String firstResult = first.get();
            String secondResult = second.get();
            log.info("并发报价确认收敛：首次结果={}，第二结果={}，首次回调次数={}，重放回调次数={}", firstResult,
                    secondResult, firstCallbackCount.get(), replayCallbackCount.get());
            assertEquals(firstResult, secondResult, "并发首次确认必须收敛到同一成功结果");
            assertEquals(1, firstCallbackCount.get(), "并发首次确认只允许执行一次首次回调");
            assertEquals(1, replayCallbackCount.get(), "竞争失败请求必须仅通过重放回调返回成功结果");
        } finally {
            executor.shutdownNow();
        }
    }

    private String executeConcurrent(InMemoryReplayableIdempotencyPort port, AtomicInteger firstCallbackCount,
                                     AtomicInteger replayCallbackCount, CountDownLatch ready, CountDownLatch start)
            throws Exception {
        ready.countDown();
        start.await();
        return port.execute(actor(), metadata(), CrmCommandType.CONFIRM_QUOTATION, CrmResourceType.QUOTATION,
                "quotation-1", REQUEST_DIGEST_A,
                () -> firstResult(firstCallbackCount, "order-1|fulfillment-1"),
                () -> replayResult(replayCallbackCount, "order-1|fulfillment-1"));
    }

    private QuotationIssuance rehydrateIssuance(QuotationIssuance issuance, AtomicInteger callbackCount) {
        callbackCount.incrementAndGet();
        return new QuotationIssuance(copyQuotation(issuance.getQuotation()),
                copyQuotationVersion(issuance.getQuotationVersion()));
    }

    private QuotationConfirmation rehydrateConfirmation(QuotationConfirmation confirmation,
                                                        AtomicInteger callbackCount) {
        callbackCount.incrementAndGet();
        return new QuotationConfirmation(copyQuotation(confirmation.getQuotation()),
                copyQuotationVersion(confirmation.getQuotationVersion()), copyOrder(confirmation.getOrder()),
                copyFulfillmentItems(confirmation.getFulfillmentItems()));
    }

    private Quotation copyQuotation(Quotation quotation) {
        return new Quotation(quotation.getQuotationId(), quotation.getTenantId(), quotation.getCustomerId(),
                quotation.getOwnerActorId(), quotation.getAggregateVersion(), quotation.getCurrentVersion(),
                quotation.getCurrentConfirmableVersion(), quotation.getConfirmedOrderId(), quotation.getCreatedAt(),
                quotation.getUpdatedAt());
    }

    private QuotationVersion copyQuotationVersion(QuotationVersion version) {
        List<QuotationLine> lines = new ArrayList<QuotationLine>();
        for (QuotationLine line : version.getLines()) {
            lines.add(copyQuotationLine(line));
        }
        return new QuotationVersion(version.getQuotationId(), version.getVersion(), version.getState(),
                version.getSettlementCurrency(), version.getValidUntil(), lines, copyMoney(version.getTotalAmount()),
                version.getIssuedAt(), version.getIssuedByActorId(), version.getConfirmedAt(),
                version.getConfirmedByActorId());
    }

    private QuotationLine copyQuotationLine(QuotationLine line) {
        return new QuotationLine(line.getQuotationLineId(), line.getOfferingId(), line.getOfferingReference(),
                line.getQuantity(), line.getUnit(), copyMoney(line.getUnitPrice()), copyMoney(line.getLineTotal()),
                new FixedPriceCommercialTermsSnapshot(line.getQuantity(), line.getUnit(), copyMoney(line.getUnitPrice())),
                copyObligationTemplate(line.getFulfillmentObligationTemplate()));
    }

    private Order copyOrder(Order order) {
        List<OrderLine> lines = new ArrayList<OrderLine>();
        for (OrderLine line : order.getLines()) {
            lines.add(copyOrderLine(line));
        }
        return new Order(order.getOrderId(), order.getTenantId(), order.getSourceQuotationId(),
                order.getSourceQuotationVersion(), order.getCustomerId(), order.getSettlementCurrency(),
                copyMoney(order.getTotalAmount()), order.getConfirmedByActorId(), order.getConfirmedAt(),
                order.getOrderVersion(), order.getDisplayState(), lines);
    }

    private OrderLine copyOrderLine(OrderLine line) {
        return new OrderLine(line.getOrderLineId(), line.getSourceQuotationLineId(), line.getOfferingReference(),
                line.getQuantity(), line.getUnit(), copyMoney(line.getUnitPrice()), copyMoney(line.getLineTotal()),
                new FixedPriceCommercialTermsSnapshot(line.getQuantity(), line.getUnit(), copyMoney(line.getUnitPrice())),
                copyObligationTemplate(line.getFulfillmentObligationTemplate()));
    }

    private List<FulfillmentItem> copyFulfillmentItems(List<FulfillmentItem> fulfillmentItems) {
        List<FulfillmentItem> copies = new ArrayList<FulfillmentItem>();
        for (FulfillmentItem fulfillmentItem : fulfillmentItems) {
            copies.add(new FulfillmentItem(fulfillmentItem.getFulfillmentId(), fulfillmentItem.getTenantId(),
                    fulfillmentItem.getOrderId(), fulfillmentItem.getOrderLineId(), fulfillmentItem.getVersion(),
                    fulfillmentItem.getState(), copyObligationTemplate(fulfillmentItem.getObligationTemplate()),
                    fulfillmentItem.getConsumerId(), fulfillmentItem.getConsumerProtocolVersion()));
        }
        return copies;
    }

    private Money copyMoney(Money money) {
        return new Money(money.getAmount(), money.getCurrency());
    }

    private void execute(InMemoryReplayableIdempotencyPort port, CrmActor actor, CrmCommandMetadata metadata,
                         CrmCommandType commandType, String targetResourceId, AtomicInteger callbackCount) {
        port.execute(actor, metadata, commandType, CrmResourceType.QUOTATION, targetResourceId, REQUEST_DIGEST_A,
                () -> firstResult(callbackCount, targetResourceId), () -> targetResourceId);
    }

    private String firstResult(AtomicInteger callbackCount, String result) {
        callbackCount.incrementAndGet();
        return result;
    }

    private String replayResult(AtomicInteger callbackCount, String result) {
        callbackCount.incrementAndGet();
        return result;
    }

    private Instant createdAt() {
        return Instant.parse("2026-07-29T00:00:00Z");
    }

    private Instant confirmedAt() {
        return Instant.parse("2026-07-30T00:00:00Z");
    }

    private Quotation draftQuotation() {
        return new Quotation("quotation-1", "tenant-1", "customer-1", "actor-1", 1L, 1,
                null, null, createdAt(), createdAt());
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

    private FulfillmentConsumerSelector consumerSelector() {
        return (tenantId, template) -> new FulfillmentConsumer("consumer-1", tenantId,
                template.getRequiredConsumerCapability(), 2);
    }

    private CrmActor actor() {
        return actor("tenant-1", "actor-1");
    }

    private CrmActor actor(String tenantId, String actorId) {
        return new CrmActor(tenantId, actorId, "操作者");
    }

    private CrmCommandMetadata metadata() {
        return metadata("idempotency-key-1");
    }

    private CrmCommandMetadata metadata(String idempotencyKey) {
        return new CrmCommandMetadata("correlation-1", idempotencyKey);
    }

    private static final class InMemoryFulfillmentItemRepository implements ReplayableFulfillmentItemRepository {

        private final List<FulfillmentItem> items = new ArrayList<FulfillmentItem>();

        @Override
        public List<FulfillmentItem> findByOrderId(String tenantId, String orderId) {
            List<FulfillmentItem> result = new ArrayList<FulfillmentItem>();
            for (FulfillmentItem item : items) {
                if (tenantId.equals(item.getTenantId()) && orderId.equals(item.getOrderId())) {
                    result.add(new FulfillmentItem(item.getFulfillmentId(), item.getTenantId(), item.getOrderId(),
                            item.getOrderLineId(), item.getVersion(), item.getState(),
                            copyObligationTemplate(item.getObligationTemplate()), item.getConsumerId(),
                            item.getConsumerProtocolVersion()));
                }
            }
            return result;
        }

        @Override
        public FulfillmentItem insert(String tenantId, FulfillmentItem fulfillmentItem) {
            if (!tenantId.equals(fulfillmentItem.getTenantId())) {
                throw new CrmException(CrmErrorCode.TENANT_MISMATCH, "tenantId");
            }
            items.add(fulfillmentItem);
            return fulfillmentItem;
        }
    }

    private static final class InMemoryReplayableIdempotencyPort implements CrmReplayableIdempotencyPort {

        private final Map<String, Record> records = new HashMap<String, Record>();

        @Override
        public <T> T execute(CrmActor actor, CrmCommandMetadata metadata, CrmCommandType commandType,
                             CrmResourceType targetResourceType, String targetResourceId, String requestDigest,
                             CrmIdempotentCallback<T> callback) {
            return callback.execute();
        }

        @Override
        public synchronized <T> T execute(CrmActor actor, CrmCommandMetadata metadata, CrmCommandType commandType,
                                          CrmResourceType targetResourceType, String targetResourceId,
                                          String requestDigest, CrmIdempotentCallback<T> callback,
                                          CrmIdempotentReplayCallback<T> replayCallback) {
            validateKnownTargetPair(commandType, targetResourceType);
            CrmValidationHelper.requiredObject(actor, "actor");
            CrmValidationHelper.requiredObject(metadata, "metadata");
            CrmValidationHelper.requiredObject(commandType, "commandType");
            CrmValidationHelper.requiredObject(targetResourceType, "targetResourceType");
            CrmValidationHelper.required(targetResourceId, "targetResourceId");
            CrmValidationHelper.sha256(requestDigest, "requestDigest");
            CrmValidationHelper.requiredObject(callback, "callback");
            CrmValidationHelper.requiredObject(replayCallback, "replayCallback");
            String key = actor.getTenantId() + "|" + actor.getActorId() + "|" + commandType.getCode() + "|"
                    + targetResourceType.getCode() + "|" + targetResourceId + "|" + metadata.getIdempotencyKey();
            Record record = records.get(key);
            if (record != null) {
                if (!record.getRequestDigest().equals(requestDigest)) {
                    throw new CrmException(CrmErrorCode.IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_PAYLOAD, "requestDigest");
                }
                return replayCallback.execute();
            }
            T result = callback.execute();
            records.put(key, new Record(requestDigest));
            return result;
        }

        private void validateKnownTargetPair(CrmCommandType commandType, CrmResourceType targetResourceType) {
            if ((commandType != CrmCommandType.ISSUE_QUOTATION
                    && commandType != CrmCommandType.CONFIRM_QUOTATION)
                    || targetResourceType != CrmResourceType.QUOTATION) {
                throw CrmException.validation("commandType/targetResourceType");
            }
        }
    }

    /**
     * 已提交幂等成功记录。
     *
     * @author surezzzzzz
     */
    @Getter
    private static final class Record {

        /**
         * 请求摘要。
         */
        private final String requestDigest;

        /**
         * 创建已提交幂等成功记录。
         *
         * @param requestDigest 请求摘要
         */
        Record(String requestDigest) {
            this.requestDigest = requestDigest;
        }
    }
}
