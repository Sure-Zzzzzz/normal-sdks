package io.github.surezzzzzz.sdk.crm.server.core.test.cases;

import io.github.surezzzzzz.sdk.crm.server.core.command.CrmCommandMetadata;
import io.github.surezzzzzz.sdk.crm.server.core.domain.commercial.FixedPriceCommercialTermsSnapshot;
import io.github.surezzzzzz.sdk.crm.server.core.domain.commercial.FulfillmentObligationTemplate;
import io.github.surezzzzzz.sdk.crm.server.core.domain.common.Money;
import io.github.surezzzzzz.sdk.crm.server.core.domain.fulfillment.FulfillmentItem;
import io.github.surezzzzzz.sdk.crm.server.core.domain.identity.CrmOwner;
import io.github.surezzzzzz.sdk.crm.server.core.domain.quotation.*;
import io.github.surezzzzzz.sdk.crm.server.core.domain.type.CrmCommandType;
import io.github.surezzzzzz.sdk.crm.server.core.domain.type.CrmResourceType;
import io.github.surezzzzzz.sdk.crm.server.core.error.CrmErrorCode;
import io.github.surezzzzzz.sdk.crm.server.core.error.CrmException;
import io.github.surezzzzzz.sdk.crm.server.core.port.idempotency.CrmReplayableIdempotencyPort;
import io.github.surezzzzzz.sdk.crm.server.core.port.repository.FulfillmentItemRepository;
import io.github.surezzzzzz.sdk.crm.server.core.port.repository.ReplayableFulfillmentItemRepository;
import io.github.surezzzzzz.sdk.crm.server.core.port.system.CrmIdGenerator;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 已知目标资源幂等端口契约测试：owner 作用域（owner、命令类型、资源类型、目标资源 ID、幂等键）。
 *
 * @author surezzzzzz
 */
@Slf4j
class CrmReplayableIdempotencyPortContractTest {

    private static final String REQUEST_DIGEST_A = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String REQUEST_DIGEST_B = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

    @Test
    void replaysQuotationIssuanceWithoutExecutingFirstCallbackAgain() {
        InMemoryReplayableIdempotencyPort port = new InMemoryReplayableIdempotencyPort();
        QuotationLifecycleService service = new QuotationLifecycleService();
        AtomicInteger firstCallbackCount = new AtomicInteger();
        CrmOwner actor = owner();

        QuotationIssuance first = port.execute(actor, metadata(), CrmCommandType.ISSUE_QUOTATION,
                CrmResourceType.QUOTATION, "quotation-1", REQUEST_DIGEST_A,
                () -> firstIssuance(service, firstCallbackCount),
                () -> replayIssuance(service));
        QuotationIssuance replay = port.execute(actor, metadata(), CrmCommandType.ISSUE_QUOTATION,
                CrmResourceType.QUOTATION, "quotation-1", REQUEST_DIGEST_A,
                () -> firstIssuance(service, firstCallbackCount),
                () -> replayIssuance(service));

        log.info("签发幂等重放结果：首次签发版本态={}，重放版本态={}，首次回调次数={}",
                first.getQuotationVersion().getState(), replay.getQuotationVersion().getState(),
                firstCallbackCount.get());
        assertEquals(QuotationState.ISSUED, replay.getQuotationVersion().getState(), "重放必须重建已提交签发事实");
        assertEquals(1, firstCallbackCount.get(), "重放不得再次执行首次回调");
    }

    @Test
    void replaysQuotationConfirmationWithoutExecutingOrderCreationAgain() {
        InMemoryReplayableIdempotencyPort port = new InMemoryReplayableIdempotencyPort();
        QuotationLifecycleService service = new QuotationLifecycleService();
        InMemoryFulfillmentItemRepository repository = new InMemoryFulfillmentItemRepository();
        AtomicInteger firstCallbackCount = new AtomicInteger();
        CrmOwner actor = owner();

        QuotationConfirmation first = confirmOnce(port, service, repository, firstCallbackCount, actor,
                REQUEST_DIGEST_A);
        for (FulfillmentItem item : first.getFulfillmentItems()) {
            repository.insert(item);
        }
        QuotationConfirmation replay = port.execute(actor, metadata(), CrmCommandType.CONFIRM_QUOTATION,
                CrmResourceType.QUOTATION, "quotation-1", REQUEST_DIGEST_A,
                () -> confirmOnce(service, repository, firstCallbackCount),
                () -> replayConfirmation(service, repository));

        log.info("确认幂等重放结果：重放订单ID={}，履约项数={}，首次回调次数={}",
                replay.getOrder().getOrderId(), replay.getFulfillmentItems().size(), firstCallbackCount.get());
        assertEquals(first.getOrder().getOrderId(), replay.getOrder().getOrderId(), "重放必须重建同一订单事实");
        assertEquals(first.getFulfillmentItems().size(), replay.getFulfillmentItems().size(),
                "重放必须按订单完整重建履约事实集合");
        assertEquals(1, firstCallbackCount.get(), "重放不得再次执行订单创建回调");
    }

    @Test
    void readsCommittedFulfillmentItemsByOrderForConfirmationReplay() {
        QuotationLifecycleService service = new QuotationLifecycleService();
        InMemoryFulfillmentItemRepository repository = new InMemoryFulfillmentItemRepository();
        QuotationConfirmation confirmation = service.confirm(issuedQuotation(), issuedVersion(), confirmedAt(),
                "iam:actor-1", idGenerator(), consumerSelector());
        for (FulfillmentItem item : confirmation.getFulfillmentItems()) {
            repository.insert(item);
        }

        List<FulfillmentItem> reloaded = repository.findByOrderId(confirmation.getOrder().getOrderId());
        List<FulfillmentItem> otherOrder = repository.findByOrderId("order-other");

        log.info("履约事实按订单读取结果：本订单数={}，其他订单数={}", reloaded.size(), otherOrder.size());
        assertEquals(confirmation.getFulfillmentItems().size(), reloaded.size(), "按订单必须完整读取履约事实");
        assertEquals(0, otherOrder.size(), "不得返回其他订单的履约项");
    }

    @Test
    void rejectsDifferentDigestWithoutExecutingAnyCallback() {
        InMemoryReplayableIdempotencyPort port = new InMemoryReplayableIdempotencyPort();
        AtomicInteger callbackCount = new AtomicInteger();
        port.execute(owner(), metadata(), CrmCommandType.ISSUE_QUOTATION, CrmResourceType.QUOTATION,
                "quotation-1", REQUEST_DIGEST_A, () -> firstResult(callbackCount, "issuance-1"));

        CrmException exception = assertThrows(CrmException.class, () -> port.execute(owner(), metadata(),
                CrmCommandType.ISSUE_QUOTATION, CrmResourceType.QUOTATION, "quotation-1", REQUEST_DIGEST_B,
                () -> firstResult(callbackCount, "issuance-2")));

        log.info("摘要冲突拒绝结果：错误码={}，回调次数={}", exception.getErrorCode(), callbackCount.get());
        assertEquals(CrmErrorCode.IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_PAYLOAD, exception.getErrorCode(),
                "同一稳定范围复用不同摘要必须拒绝");
        assertEquals(1, callbackCount.get(), "摘要冲突不得执行任何回调");
    }

    @Test
    void rejectsInvalidKnownTargetCommandResourcePair() {
        InMemoryReplayableIdempotencyPort port = new InMemoryReplayableIdempotencyPort();

        CrmException commandException = assertThrows(CrmException.class, () -> port.execute(owner(), metadata(),
                CrmCommandType.CREATE_CUSTOMER, CrmResourceType.QUOTATION, "quotation-1", REQUEST_DIGEST_A,
                () -> "result"));
        CrmException resourceException = assertThrows(CrmException.class, () -> port.execute(owner(), metadata(),
                CrmCommandType.ISSUE_QUOTATION, CrmResourceType.CUSTOMER, "customer-1", REQUEST_DIGEST_A,
                () -> "result"));

        log.info("已知目标命令资源配对校验结果：命令错误码={}，资源错误码={}", commandException.getErrorCode(),
                resourceException.getErrorCode());
        assertEquals(CrmErrorCode.VALIDATION_FAILED, commandException.getErrorCode(),
                "签发/确认外的命令不得使用已知目标幂等端口");
        assertEquals(CrmErrorCode.VALIDATION_FAILED, resourceException.getErrorCode(),
                "已知目标幂等端口只接受报价资源");
    }

    @Test
    void doesNotPersistSuccessWhenFirstCallbackFails() {
        InMemoryReplayableIdempotencyPort port = new InMemoryReplayableIdempotencyPort();
        AtomicInteger callbackCount = new AtomicInteger();

        assertThrows(IllegalStateException.class, () -> port.execute(owner(), metadata(),
                CrmCommandType.ISSUE_QUOTATION, CrmResourceType.QUOTATION, "quotation-1", REQUEST_DIGEST_A,
                () -> {
                    callbackCount.incrementAndGet();
                    throw new IllegalStateException("first attempt failed");
                }));
        String result = port.execute(owner(), metadata(), CrmCommandType.ISSUE_QUOTATION,
                CrmResourceType.QUOTATION, "quotation-1", REQUEST_DIGEST_A,
                () -> firstResult(callbackCount, "issuance-1"));

        log.info("首次失败后重试结果：{}，回调次数={}", result, callbackCount.get());
        assertEquals("issuance-1", result, "失败尝试不得留下可重放成功记录");
        assertEquals(2, callbackCount.get(), "失败后重试必须重新执行首次回调");
    }

    @Test
    void isolatesOwnerCommandTargetAndIdempotencyKey() {
        InMemoryReplayableIdempotencyPort port = new InMemoryReplayableIdempotencyPort();
        AtomicInteger callbackCount = new AtomicInteger();

        execute(port, "iam:actor-1", "key-1", CrmCommandType.ISSUE_QUOTATION, "quotation-1", callbackCount);
        execute(port, "aksk:client-1", "key-1", CrmCommandType.ISSUE_QUOTATION, "quotation-1", callbackCount);
        execute(port, "iam:actor-1", "key-1", CrmCommandType.CONFIRM_QUOTATION, "quotation-1", callbackCount);
        execute(port, "iam:actor-1", "key-1", CrmCommandType.ISSUE_QUOTATION, "quotation-2", callbackCount);
        execute(port, "iam:actor-1", "key-2", CrmCommandType.ISSUE_QUOTATION, "quotation-1", callbackCount);

        log.info("已知目标幂等隔离结果：独立执行次数={}", callbackCount.get());
        assertEquals(5, callbackCount.get(), "稳定范围任一维度不同都不得重放其他成功记录");
    }

    @Test
    void convergesConcurrentFirstConfirmationToOneResult() throws Exception {
        InMemoryReplayableIdempotencyPort port = new InMemoryReplayableIdempotencyPort();
        QuotationLifecycleService service = new QuotationLifecycleService();
        InMemoryFulfillmentItemRepository repository = new InMemoryFulfillmentItemRepository();
        AtomicInteger firstCallbackCount = new AtomicInteger();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<QuotationConfirmation> first = executor.submit(() -> concurrentConfirm(
                    port, service, repository, firstCallbackCount, ready, start));
            Future<QuotationConfirmation> second = executor.submit(() -> concurrentConfirm(
                    port, service, repository, firstCallbackCount, ready, start));
            ready.await();
            start.countDown();

            QuotationConfirmation firstResult = first.get();
            QuotationConfirmation secondResult = second.get();
            log.info("并发确认收敛结果：首次订单ID={}，第二订单ID={}，首次回调次数={}",
                    firstResult.getOrder().getOrderId(), secondResult.getOrder().getOrderId(),
                    firstCallbackCount.get());
            assertEquals(firstResult.getOrder().getOrderId(), secondResult.getOrder().getOrderId(),
                    "同一稳定范围并发确认必须收敛到同一订单");
            assertEquals(1, firstCallbackCount.get(), "同一稳定范围并发确认只允许执行一次首次回调");
        } finally {
            executor.shutdownNow();
        }
    }

    private QuotationIssuance firstIssuance(QuotationLifecycleService service, AtomicInteger callbackCount) {
        callbackCount.incrementAndGet();
        return service.issue(draftQuotation(), draftVersion(), confirmedAt(), "iam:actor-1");
    }

    private QuotationIssuance replayIssuance(QuotationLifecycleService service) {
        return service.issue(draftQuotation(), draftVersion(), confirmedAt(), "iam:actor-1");
    }

    private QuotationConfirmation confirmOnce(InMemoryReplayableIdempotencyPort port,
                                              QuotationLifecycleService service,
                                              InMemoryFulfillmentItemRepository repository,
                                              AtomicInteger callbackCount, CrmOwner actor, String digest) {
        return port.execute(actor, metadata(), CrmCommandType.CONFIRM_QUOTATION, CrmResourceType.QUOTATION,
                "quotation-1", digest, () -> confirmOnce(service, repository, callbackCount),
                () -> replayConfirmation(service, repository));
    }

    private QuotationConfirmation confirmOnce(QuotationLifecycleService service,
                                              InMemoryFulfillmentItemRepository repository,
                                              AtomicInteger callbackCount) {
        callbackCount.incrementAndGet();
        return service.confirm(issuedQuotation(), issuedVersion(), confirmedAt(), "iam:actor-1",
                idGenerator(), consumerSelector());
    }

    private QuotationConfirmation replayConfirmation(QuotationLifecycleService service,
                                                     InMemoryFulfillmentItemRepository repository) {
        return service.confirm(issuedQuotation(), issuedVersion(), confirmedAt(), "iam:actor-1",
                idGenerator(), consumerSelector());
    }

    private QuotationConfirmation concurrentConfirm(InMemoryReplayableIdempotencyPort port,
                                                    QuotationLifecycleService service,
                                                    InMemoryFulfillmentItemRepository repository,
                                                    AtomicInteger callbackCount, CountDownLatch ready,
                                                    CountDownLatch start) throws Exception {
        ready.countDown();
        start.await();
        return port.execute(owner(), metadata(), CrmCommandType.CONFIRM_QUOTATION, CrmResourceType.QUOTATION,
                "quotation-1", REQUEST_DIGEST_A,
                () -> confirmOnce(service, repository, callbackCount),
                () -> replayConfirmation(service, repository));
    }

    private void execute(InMemoryReplayableIdempotencyPort port, String owner, String idempotencyKey,
                         CrmCommandType commandType, String targetResourceId, AtomicInteger callbackCount) {
        port.execute(CrmOwner.of(owner), metadata(idempotencyKey), commandType, CrmResourceType.QUOTATION,
                targetResourceId, REQUEST_DIGEST_A, () -> firstResult(callbackCount, "result"));
    }

    private String firstResult(AtomicInteger callbackCount, String result) {
        callbackCount.incrementAndGet();
        return result;
    }

    private CrmOwner owner() {
        return CrmOwner.of("iam:actor-1");
    }

    private CrmCommandMetadata metadata() {
        return metadata("idempotency-key-1");
    }

    private CrmCommandMetadata metadata(String idempotencyKey) {
        return new CrmCommandMetadata("correlation-1", idempotencyKey);
    }

    private Instant createdAt() {
        return Instant.parse("2026-07-29T00:00:00Z");
    }

    private Instant confirmedAt() {
        return Instant.parse("2026-07-30T00:00:00Z");
    }

    private CrmIdGenerator idGenerator() {
        return resourceType -> resourceType.name().toLowerCase().replace('_', '-')
                + "-" + resourceType.ordinal() + "-1";
    }

    private FulfillmentConsumerSelector consumerSelector() {
        return template -> new io.github.surezzzzzz.sdk.crm.server.core.domain.fulfillment.FulfillmentConsumer(
                "consumer-1", template.getRequiredConsumerCapability(), 2);
    }

    private Quotation issuedQuotation() {
        return draftQuotation().issued(1, confirmedAt());
    }

    private Quotation draftQuotation() {
        return new Quotation("quotation-1", "customer-1", "iam:actor-1", 1L, 1,
                null, null, null, createdAt(), createdAt());
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
        return draftVersion().issue(confirmedAt(), "iam:actor-1");
    }

    private static final class InMemoryFulfillmentItemRepository
            implements ReplayableFulfillmentItemRepository, FulfillmentItemRepository {

        private final List<FulfillmentItem> items = new ArrayList<FulfillmentItem>();

        @Override
        public List<FulfillmentItem> findByOrderId(String orderId) {
            List<FulfillmentItem> result = new ArrayList<FulfillmentItem>();
            for (FulfillmentItem item : items) {
                if (orderId.equals(item.getOrderId())) {
                    result.add(item);
                }
            }
            return result;
        }

        @Override
        public FulfillmentItem insert(FulfillmentItem fulfillmentItem) {
            CrmValidationHelper.requiredObject(fulfillmentItem, "fulfillmentItem");
            items.add(fulfillmentItem);
            return fulfillmentItem;
        }
    }

    private static final class InMemoryReplayableIdempotencyPort implements CrmReplayableIdempotencyPort {

        private final Map<String, Record> records = new HashMap<String, Record>();

        @Override
        public <T> T execute(CrmOwner actor, CrmCommandMetadata metadata, CrmCommandType commandType,
                             CrmResourceType targetResourceType, String targetResourceId, String requestDigest,
                             CrmIdempotentCallback<T> callback) {
            return execute(actor, metadata, commandType, targetResourceType, targetResourceId, requestDigest,
                    callback, () -> callback.execute());
        }

        @Override
        public synchronized <T> T execute(CrmOwner actor, CrmCommandMetadata metadata, CrmCommandType commandType,
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
            String key = actor.getOwner() + "|" + commandType.getCode() + "|"
                    + targetResourceType.getCode() + "|" + targetResourceId + "|" + metadata.getIdempotencyKey();
            Record record = records.get(key);
            if (record != null) {
                if (!record.getRequestDigest().equals(requestDigest)) {
                    throw new CrmException(CrmErrorCode.IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_PAYLOAD,
                            "requestDigest");
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

        Record(String requestDigest) {
            this.requestDigest = requestDigest;
        }
    }
}
