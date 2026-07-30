package io.github.surezzzzzz.sdk.crm.server.core.test.cases;

import io.github.surezzzzzz.sdk.crm.server.core.command.CrmCommandMetadata;
import io.github.surezzzzzz.sdk.crm.server.core.domain.identity.CrmActor;
import io.github.surezzzzzz.sdk.crm.server.core.domain.type.CrmCommandType;
import io.github.surezzzzzz.sdk.crm.server.core.domain.type.CrmResourceType;
import io.github.surezzzzzz.sdk.crm.server.core.error.CrmErrorCode;
import io.github.surezzzzzz.sdk.crm.server.core.error.CrmException;
import io.github.surezzzzzz.sdk.crm.server.core.port.idempotency.CrmCreateIdempotencyPort;
import io.github.surezzzzzz.sdk.crm.server.core.port.idempotency.CrmIdempotencyPort;
import io.github.surezzzzzz.sdk.crm.server.core.support.CrmValidationHelper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 创建命令幂等端口契约测试。
 *
 * @author surezzzzzz
 */
@Slf4j
class CrmCreateIdempotencyPortContractTest {

    private static final String REQUEST_DIGEST_A = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String REQUEST_DIGEST_B = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

    @Test
    void replaysFirstResourceIdWithoutExecutingCallbackAgain() {
        InMemoryCreateIdempotencyPort port = new InMemoryCreateIdempotencyPort();
        AtomicInteger callbackCount = new AtomicInteger();
        AtomicInteger idGeneratorCount = new AtomicInteger();

        String firstResourceId = port.execute(actor("tenant-1", "actor-1"), metadata("key-1"),
                CrmCommandType.CREATE_CUSTOMER, CrmResourceType.CUSTOMER, REQUEST_DIGEST_A,
                () -> generatedId(callbackCount, idGeneratorCount, "customer"));
        String replayedResourceId = port.execute(actor("tenant-1", "actor-1"), metadata("key-1"),
                CrmCommandType.CREATE_CUSTOMER, CrmResourceType.CUSTOMER, REQUEST_DIGEST_A,
                () -> generatedId(callbackCount, idGeneratorCount, "customer"));

        log.info("创建幂等重放结果：首次资源ID={}，重放资源ID={}，回调次数={}，ID生成次数={}",
                firstResourceId, replayedResourceId, callbackCount.get(), idGeneratorCount.get());
        assertEquals("customer-1", firstResourceId, "首次创建必须返回已持久化顶级资源ID");
        assertEquals(firstResourceId, replayedResourceId, "同一请求重放必须返回首个资源ID");
        assertEquals(1, callbackCount.get(), "重放不得再次执行创建回调");
        assertEquals(1, idGeneratorCount.get(), "重放不得再次生成资源ID");
    }

    @Test
    void convergesConcurrentFirstRequestsToOneResource() throws Exception {
        InMemoryCreateIdempotencyPort port = new InMemoryCreateIdempotencyPort();
        AtomicInteger callbackCount = new AtomicInteger();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<String> first = executor.submit(() -> concurrentCreate(port, callbackCount, ready, start));
            Future<String> second = executor.submit(() -> concurrentCreate(port, callbackCount, ready, start));
            ready.await();
            start.countDown();

            String firstResourceId = first.get();
            String secondResourceId = second.get();
            log.info("并发创建收敛结果：首次资源ID={}，第二资源ID={}，回调次数={}",
                    firstResourceId, secondResourceId, callbackCount.get());
            assertEquals(firstResourceId, secondResourceId, "同一稳定范围并发创建必须收敛到同一资源ID");
            assertEquals(1, callbackCount.get(), "同一稳定范围并发创建只允许执行一次回调");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void rejectsSameScopeWithDifferentDigestWithoutExecutingCallback() {
        InMemoryCreateIdempotencyPort port = new InMemoryCreateIdempotencyPort();
        AtomicInteger callbackCount = new AtomicInteger();
        port.execute(actor("tenant-1", "actor-1"), metadata("key-1"), CrmCommandType.CREATE_CONTACT,
                CrmResourceType.CONTACT, REQUEST_DIGEST_A, () -> generatedId(callbackCount, new AtomicInteger(), "contact"));

        CrmException exception = assertThrows(CrmException.class, () -> port.execute(actor("tenant-1", "actor-1"),
                metadata("key-1"), CrmCommandType.CREATE_CONTACT, CrmResourceType.CONTACT, REQUEST_DIGEST_B,
                () -> generatedId(callbackCount, new AtomicInteger(), "contact")));

        log.info("创建幂等摘要冲突结果：错误码={}，回调次数={}", exception.getErrorCode(), callbackCount.get());
        assertEquals(CrmErrorCode.IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_PAYLOAD, exception.getErrorCode(),
                "同一稳定范围复用不同摘要必须拒绝");
        assertEquals(1, callbackCount.get(), "摘要冲突不得执行创建回调");
    }

    @Test
    void isolatesTenantActorCommandResourceTypeAndIdempotencyKey() {
        InMemoryCreateIdempotencyPort port = new InMemoryCreateIdempotencyPort();
        AtomicInteger callbackCount = new AtomicInteger();

        execute(port, actor("tenant-1", "actor-1"), "key-1", CrmCommandType.CREATE_CUSTOMER,
                CrmResourceType.CUSTOMER, callbackCount, "customer");
        execute(port, actor("tenant-2", "actor-1"), "key-1", CrmCommandType.CREATE_CUSTOMER,
                CrmResourceType.CUSTOMER, callbackCount, "customer");
        execute(port, actor("tenant-1", "actor-2"), "key-1", CrmCommandType.CREATE_CUSTOMER,
                CrmResourceType.CUSTOMER, callbackCount, "customer");
        execute(port, actor("tenant-1", "actor-1"), "key-1", CrmCommandType.CREATE_CONTACT,
                CrmResourceType.CONTACT, callbackCount, "contact");
        execute(port, actor("tenant-1", "actor-1"), "key-1", CrmCommandType.CREATE_OFFERING,
                CrmResourceType.OFFERING, callbackCount, "offering");
        execute(port, actor("tenant-1", "actor-1"), "key-1", CrmCommandType.CREATE_QUOTATION,
                CrmResourceType.QUOTATION, callbackCount, "quotation");
        execute(port, actor("tenant-1", "actor-1"), "key-2", CrmCommandType.CREATE_CUSTOMER,
                CrmResourceType.CUSTOMER, callbackCount, "customer");

        log.info("创建幂等隔离结果：独立创建次数={}", callbackCount.get());
        assertEquals(7, callbackCount.get(), "稳定范围任一维度不同都不得重放其他创建记录");
    }

    @Test
    void rejectsInvalidCreateCommandResourcePair() {
        InMemoryCreateIdempotencyPort port = new InMemoryCreateIdempotencyPort();

        CrmException commandException = assertThrows(CrmException.class, () -> port.execute(actor("tenant-1", "actor-1"),
                metadata("key-1"), CrmCommandType.ISSUE_QUOTATION, CrmResourceType.QUOTATION, REQUEST_DIGEST_A,
                () -> "quotation-1"));
        CrmException resourceException = assertThrows(CrmException.class, () -> port.execute(actor("tenant-1", "actor-1"),
                metadata("key-1"), CrmCommandType.CREATE_CUSTOMER, CrmResourceType.CONTACT, REQUEST_DIGEST_A,
                () -> "customer-1"));

        log.info("创建命令资源配对校验结果：命令错误码={}，资源错误码={}", commandException.getErrorCode(),
                resourceException.getErrorCode());
        assertEquals(CrmErrorCode.VALIDATION_FAILED, commandException.getErrorCode(), "非创建命令不得使用创建幂等端口");
        assertEquals(CrmErrorCode.VALIDATION_FAILED, resourceException.getErrorCode(), "创建命令必须匹配顶级资源类型");
    }

    @Test
    void doesNotPersistSuccessfulRecordWhenCallbackFailsOrReturnsBlankId() {
        InMemoryCreateIdempotencyPort port = new InMemoryCreateIdempotencyPort();
        AtomicInteger callbackCount = new AtomicInteger();

        assertThrows(IllegalStateException.class, () -> port.execute(actor("tenant-1", "actor-1"), metadata("key-1"),
                CrmCommandType.CREATE_OFFERING, CrmResourceType.OFFERING, REQUEST_DIGEST_A, () -> {
                    callbackCount.incrementAndGet();
                    throw new IllegalStateException("first attempt failed");
                }));
        CrmException nullResourceException = assertThrows(CrmException.class, () -> port.execute(actor("tenant-1", "actor-1"),
                metadata("key-1"), CrmCommandType.CREATE_OFFERING, CrmResourceType.OFFERING, REQUEST_DIGEST_A,
                () -> null));
        CrmException blankResourceException = assertThrows(CrmException.class, () -> port.execute(actor("tenant-1", "actor-1"),
                metadata("key-1"), CrmCommandType.CREATE_OFFERING, CrmResourceType.OFFERING, REQUEST_DIGEST_A,
                () -> " "));
        log.info("创建回调资源ID校验结果：空值错误码={}，空白错误码={}", nullResourceException.getErrorCode(),
                blankResourceException.getErrorCode());
        assertEquals(CrmErrorCode.VALIDATION_FAILED, nullResourceException.getErrorCode(), "空资源ID必须拒绝");
        assertEquals(CrmErrorCode.VALIDATION_FAILED, blankResourceException.getErrorCode(), "空白资源ID必须拒绝");
        String resourceId = port.execute(actor("tenant-1", "actor-1"), metadata("key-1"),
                CrmCommandType.CREATE_OFFERING, CrmResourceType.OFFERING, REQUEST_DIGEST_A, () -> {
                    callbackCount.incrementAndGet();
                    return "offering-1";
                });

        log.info("创建失败后重试结果：资源ID={}，有效回调次数={}", resourceId, callbackCount.get());
        assertEquals("offering-1", resourceId, "失败尝试不得留下可重放成功记录");
        assertEquals(2, callbackCount.get(), "失败后重试必须重新执行首次创建回调");
    }

    @Test
    void keepsPublishedTargetKnownPortImplementationCompatible() {
        CrmIdempotencyPort port = new CrmIdempotencyPort() {
            @Override
            public <T> T execute(CrmActor actor, CrmCommandMetadata metadata, CrmCommandType commandType,
                                 CrmResourceType targetResourceType, String targetResourceId, String requestDigest,
                                 CrmIdempotentCallback<T> callback) {
                return callback.execute();
            }
        };

        String result = port.execute(actor("tenant-1", "actor-1"), metadata("key-1"),
                CrmCommandType.ISSUE_QUOTATION, CrmResourceType.QUOTATION, "quotation-1", REQUEST_DIGEST_A,
                () -> "issuance-1");

        log.info("已知目标资源幂等端口兼容结果：{}", result);
        assertEquals("issuance-1", result, "1.0.0 端口的既有实现必须保持可编译和可调用");
    }

    private String concurrentCreate(InMemoryCreateIdempotencyPort port, AtomicInteger callbackCount,
                                    CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        start.await();
        return port.execute(actor("tenant-1", "actor-1"), metadata("key-1"), CrmCommandType.CREATE_CUSTOMER,
                CrmResourceType.CUSTOMER, REQUEST_DIGEST_A,
                () -> "customer-" + callbackCount.incrementAndGet());
    }

    private void execute(InMemoryCreateIdempotencyPort port, CrmActor actor, String idempotencyKey,
                         CrmCommandType commandType, CrmResourceType resourceType, AtomicInteger callbackCount,
                         String resourcePrefix) {
        port.execute(actor, metadata(idempotencyKey), commandType, resourceType, REQUEST_DIGEST_A,
                () -> resourcePrefix + "-" + callbackCount.incrementAndGet());
    }

    private String generatedId(AtomicInteger callbackCount, AtomicInteger idGeneratorCount, String resourcePrefix) {
        callbackCount.incrementAndGet();
        return resourcePrefix + "-" + idGeneratorCount.incrementAndGet();
    }

    private CrmActor actor(String tenantId, String actorId) {
        return new CrmActor(tenantId, actorId, "Actor");
    }

    private CrmCommandMetadata metadata(String idempotencyKey) {
        return new CrmCommandMetadata("correlation-1", idempotencyKey);
    }

    private static final class InMemoryCreateIdempotencyPort implements CrmCreateIdempotencyPort {

        private final Map<String, Record> records = new HashMap<>();

        @Override
        public synchronized String execute(CrmActor actor, CrmCommandMetadata metadata, CrmCommandType commandType,
                                           CrmResourceType targetResourceType, String requestDigest,
                                           CrmCreateIdempotentCallback callback) {
            validateCreatePair(commandType, targetResourceType);
            CrmValidationHelper.requiredObject(actor, "actor");
            CrmValidationHelper.requiredObject(metadata, "metadata");
            CrmValidationHelper.sha256(requestDigest, "requestDigest");
            CrmValidationHelper.requiredObject(callback, "callback");

            String key = actor.getTenantId() + "|" + actor.getActorId() + "|" + commandType.getCode() + "|"
                    + targetResourceType.getCode() + "|" + metadata.getIdempotencyKey();
            Record record = records.get(key);
            if (record != null) {
                if (!record.requestDigest.equals(requestDigest)) {
                    throw new CrmException(CrmErrorCode.IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_PAYLOAD, "requestDigest");
                }
                return record.resourceId;
            }

            String resourceId = CrmValidationHelper.required(callback.execute(), "resourceId");
            records.put(key, new Record(requestDigest, resourceId));
            return resourceId;
        }

        private void validateCreatePair(CrmCommandType commandType, CrmResourceType resourceType) {
            CrmValidationHelper.requiredObject(commandType, "commandType");
            CrmValidationHelper.requiredObject(resourceType, "targetResourceType");
            if (!isValidCreatePair(commandType, resourceType)) {
                throw CrmException.validation("commandType");
            }
        }

        private boolean isValidCreatePair(CrmCommandType commandType, CrmResourceType resourceType) {
            return commandType == CrmCommandType.CREATE_CUSTOMER && resourceType == CrmResourceType.CUSTOMER
                    || commandType == CrmCommandType.CREATE_CONTACT && resourceType == CrmResourceType.CONTACT
                    || commandType == CrmCommandType.CREATE_OFFERING && resourceType == CrmResourceType.OFFERING
                    || commandType == CrmCommandType.CREATE_QUOTATION && resourceType == CrmResourceType.QUOTATION;
        }
    }

    private static final class Record {

        private final String requestDigest;
        private final String resourceId;

        private Record(String requestDigest, String resourceId) {
            this.requestDigest = requestDigest;
            this.resourceId = resourceId;
        }
    }
}
