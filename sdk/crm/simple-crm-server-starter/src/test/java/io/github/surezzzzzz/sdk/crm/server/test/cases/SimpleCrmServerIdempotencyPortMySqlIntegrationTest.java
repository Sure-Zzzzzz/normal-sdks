package io.github.surezzzzzz.sdk.crm.server.test.cases;

import io.github.surezzzzzz.sdk.crm.server.core.command.CrmCommandMetadata;
import io.github.surezzzzzz.sdk.crm.server.core.domain.identity.CrmOwner;
import io.github.surezzzzzz.sdk.crm.server.core.domain.type.CrmCommandType;
import io.github.surezzzzzz.sdk.crm.server.core.domain.type.CrmResourceType;
import io.github.surezzzzzz.sdk.crm.server.core.error.CrmErrorCode;
import io.github.surezzzzzz.sdk.crm.server.core.error.CrmException;
import io.github.surezzzzzz.sdk.crm.server.entity.SimpleCrmServerIdempotencyEntity;
import io.github.surezzzzzz.sdk.crm.server.repository.SimpleCrmServerJdbcIdempotencyRepository;
import io.github.surezzzzzz.sdk.crm.server.service.SimpleCrmServerJdbcIdempotencyPort;
import io.github.surezzzzzz.sdk.crm.server.test.SimpleCrmServerTestApplication;
import io.github.surezzzzzz.sdk.crm.server.test.support.SimpleCrmServerTestSchema;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 真实 MySQL 幂等回归：独立 Port 对象和事务通过唯一键收敛并发首次请求。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleCrmServerTestApplication.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SimpleCrmServerIdempotencyPortMySqlIntegrationTest {

    private static final String FIRST_DIGEST = String.format("%064d", 1);
    private static final String SECOND_DIGEST = String.format("%064d", 2);

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private JdbcTemplate jdbc;

    @BeforeAll
    void ensureSchema() throws Exception {
        SimpleCrmServerTestSchema.ensureExecuted(dataSource);
        jdbc = new JdbcTemplate(dataSource);
    }

    /**
     * 两个 Port 在不同事务中同时创建时只执行一次回调，并返回同一个首资源 ID。
     *
     * @throws Exception 并发执行失败
     */
    @Test
    void concurrentCreateReturnsOneCommittedResource() throws Exception {
        CrmOwner owner = CrmOwner.human("idempotency-create-owner");
        CrmCommandMetadata metadata = metadata();
        String resourceId = "customer-" + UUID.randomUUID();
        AtomicInteger callbackCount = new AtomicInteger();
        CountDownLatch callbackStarted = new CountDownLatch(1);
        CountDownLatch releaseCallback = new CountDownLatch(1);

        List<String> results = executeContended(port -> port.execute(owner, metadata,
                CrmCommandType.CREATE_CUSTOMER, CrmResourceType.CUSTOMER, FIRST_DIGEST, () -> {
                    callbackCount.incrementAndGet();
                    callbackStarted.countDown();
                    await(releaseCallback);
                    return resourceId;
                }), callbackStarted, releaseCallback);

        assertEquals(Arrays.asList(resourceId, resourceId), results);
        assertEquals(1, callbackCount.get());
        assertEquals(1L, rowCount(scope(owner, metadata, CrmCommandType.CREATE_CUSTOMER,
                CrmResourceType.CUSTOMER, null)));
    }

    /**
     * 已知目标的空首结果保存为 JSON null；并发重放不得再运行任一回调。
     *
     * @throws Exception 并发执行失败
     */
    @Test
    void concurrentKnownTargetPreservesNullFirstResult() throws Exception {
        CrmOwner owner = CrmOwner.service("idempotency-consumer-owner");
        CrmCommandMetadata metadata = metadata();
        String targetId = "consumer-" + UUID.randomUUID();
        AtomicInteger callbackCount = new AtomicInteger();
        AtomicInteger replayCallbackCount = new AtomicInteger();
        CountDownLatch callbackStarted = new CountDownLatch(1);
        CountDownLatch releaseCallback = new CountDownLatch(1);

        List<Void> results = executeContended(port -> port.execute(owner, metadata,
                CrmCommandType.UPSERT_FULFILLMENT_CONSUMER, CrmResourceType.FULFILLMENT_CONSUMER,
                targetId, FIRST_DIGEST, () -> {
                    callbackCount.incrementAndGet();
                    callbackStarted.countDown();
                    await(releaseCallback);
                    return null;
                }, () -> {
                    replayCallbackCount.incrementAndGet();
                    return null;
                }), callbackStarted, releaseCallback);

        assertNull(results.get(0));
        assertNull(results.get(1));
        assertEquals(1, callbackCount.get());
        assertEquals(0, replayCallbackCount.get());
        String scopeKey = scope(owner, metadata, CrmCommandType.UPSERT_FULFILLMENT_CONSUMER,
                CrmResourceType.FULFILLMENT_CONSUMER, targetId);
        assertEquals(1L, rowCount(scopeKey));
        assertEquals("null", jdbc.queryForObject("SELECT first_result_json FROM crm_idempotency"
                + " WHERE scope_key = ?", String.class, scopeKey));
    }

    /**
     * 同一范围键换请求摘要必须拒绝，且不执行第二次回调。
     */
    @Test
    void differentDigestCannotReuseCommittedCreateKey() {
        CrmOwner owner = CrmOwner.human("idempotency-digest-owner");
        CrmCommandMetadata metadata = metadata();
        SimpleCrmServerJdbcIdempotencyPort first = newPort(null);
        SimpleCrmServerJdbcIdempotencyPort second = newPort(null);
        AtomicInteger callbackCount = new AtomicInteger();
        String resourceId = "customer-" + UUID.randomUUID();
        assertEquals(resourceId, transaction(() -> first.execute(owner, metadata,
                CrmCommandType.CREATE_CUSTOMER, CrmResourceType.CUSTOMER, FIRST_DIGEST, () -> {
                    callbackCount.incrementAndGet();
                    return resourceId;
                })));

        CrmException failure = assertThrows(CrmException.class, () -> transaction(() -> second.execute(
                owner, metadata, CrmCommandType.CREATE_CUSTOMER, CrmResourceType.CUSTOMER,
                SECOND_DIGEST, () -> {
                    callbackCount.incrementAndGet();
                    return "unexpected";
                })));
        assertEquals(CrmErrorCode.IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_PAYLOAD,
                failure.getErrorCode());
        assertEquals(1, callbackCount.get());
        assertEquals(1L, rowCount(scope(owner, metadata, CrmCommandType.CREATE_CUSTOMER,
                CrmResourceType.CUSTOMER, null)));
    }

    /**
     * 首回调失败后事务连占位一起回滚；同键不同摘要可以重新成为首次成功。
     */
    @Test
    void callbackFailureRollsBackClaim() {
        CrmOwner owner = CrmOwner.human("idempotency-rollback-owner");
        CrmCommandMetadata metadata = metadata();
        String scopeKey = scope(owner, metadata, CrmCommandType.CREATE_CUSTOMER,
                CrmResourceType.CUSTOMER, null);
        SimpleCrmServerJdbcIdempotencyPort port = newPort(null);
        assertThrows(IllegalStateException.class, () -> transaction(() -> port.execute(owner,
                metadata, CrmCommandType.CREATE_CUSTOMER, CrmResourceType.CUSTOMER,
                FIRST_DIGEST, () -> {
                    throw new IllegalStateException("模拟业务回调失败");
                })));
        assertEquals(0L, rowCount(scopeKey));

        String resourceId = "customer-" + UUID.randomUUID();
        assertEquals(resourceId, transaction(() -> newPort(null).execute(owner, metadata,
                CrmCommandType.CREATE_CUSTOMER, CrmResourceType.CUSTOMER, SECOND_DIGEST,
                () -> resourceId)));
        assertEquals(1L, rowCount(scopeKey));
    }

    /**
     * 创建与已知目标命令均须拒绝错误的命令/资源类型配对。
     */
    @Test
    void rejectsWrongCommandResourcePairs() {
        CrmOwner owner = CrmOwner.human("idempotency-pair-owner");
        CrmCommandMetadata metadata = metadata();
        SimpleCrmServerJdbcIdempotencyPort port = newPort(null);
        CrmException createFailure = assertThrows(CrmException.class, () -> transaction(
                () -> port.execute(owner, metadata, CrmCommandType.CREATE_CUSTOMER,
                        CrmResourceType.QUOTATION, FIRST_DIGEST, () -> "unexpected")));
        CrmException knownTargetFailure = assertThrows(CrmException.class, () -> transaction(
                () -> port.execute(owner, metadata, CrmCommandType.PUBLISH_OFFERING,
                        CrmResourceType.CUSTOMER, "target", FIRST_DIGEST,
                        () -> null, () -> null)));
        assertEquals(CrmErrorCode.VALIDATION_FAILED, createFailure.getErrorCode());
        assertEquals(CrmErrorCode.VALIDATION_FAILED, knownTargetFailure.getErrorCode());
        assertEquals(0L, rowCount(scope(owner, metadata, CrmCommandType.CREATE_CUSTOMER,
                CrmResourceType.QUOTATION, null)));
    }

    private <T> List<T> executeContended(PortInvocation<T> invocation,
            CountDownLatch callbackStarted, CountDownLatch releaseCallback) throws Exception {
        log.debug("开始两个独立幂等 Port 的并发测试");
        CountDownLatch claimAttempts = new CountDownLatch(2);
        SimpleCrmServerJdbcIdempotencyPort first = newPort(claimAttempts);
        SimpleCrmServerJdbcIdempotencyPort second = newPort(claimAttempts);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<T> firstResult = executor.submit(transactionTask(first, invocation, ready, start));
            Future<T> secondResult = executor.submit(transactionTask(second, invocation, ready, start));
            assertTrue(ready.await(20, TimeUnit.SECONDS), "两个事务必须已就绪");
            start.countDown();
            assertTrue(callbackStarted.await(20, TimeUnit.SECONDS), "首次回调必须开始");
            assertTrue(claimAttempts.await(20, TimeUnit.SECONDS), "两个 Port 必须争同一范围键");
            assertFalse(firstResult.isDone(), "首事务提交前不得提前完成");
            assertFalse(secondResult.isDone(), "竞争事务须等待首次结果");
            releaseCallback.countDown();
            return Arrays.asList(firstResult.get(30, TimeUnit.SECONDS),
                    secondResult.get(30, TimeUnit.SECONDS));
        } finally {
            start.countDown();
            releaseCallback.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS), "测试工作线程必须退出");
        }
    }

    private <T> Callable<T> transactionTask(SimpleCrmServerJdbcIdempotencyPort port,
            PortInvocation<T> invocation, CountDownLatch ready, CountDownLatch start) {
        return () -> {
            ready.countDown();
            await(start);
            TransactionTemplate transaction = new TransactionTemplate(transactionManager);
            transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
            return transaction.execute(status -> invocation.execute(port));
        };
    }

    private SimpleCrmServerJdbcIdempotencyPort newPort(CountDownLatch claimAttempts) {
        NamedParameterJdbcTemplate template = new NamedParameterJdbcTemplate(dataSource);
        SimpleCrmServerJdbcIdempotencyRepository repository = new SimpleCrmServerJdbcIdempotencyRepository(template) {
            @Override
            public boolean claim(SimpleCrmServerIdempotencyEntity entity) {
                if (claimAttempts != null) {
                    claimAttempts.countDown();
                    await(claimAttempts);
                }
                return super.claim(entity);
            }
        };
        return new SimpleCrmServerJdbcIdempotencyPort(repository);
    }

    private <T> T transaction(Callable<T> action) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        return transaction.execute(status -> {
            try {
                return action.call();
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
    }

    private long rowCount(String scopeKey) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM crm_idempotency WHERE scope_key = ?",
                Long.class, scopeKey);
    }

    private CrmCommandMetadata metadata() {
        String suffix = UUID.randomUUID().toString();
        return new CrmCommandMetadata("port-" + suffix, "idem-" + suffix);
    }

    private String scope(CrmOwner owner, CrmCommandMetadata metadata, CrmCommandType commandType,
            CrmResourceType resourceType, String targetId) {
        String key = owner.getOwner() + "|" + commandType.getCode() + "|" + resourceType.getCode();
        return key + (targetId == null ? "" : "|" + targetId) + "|" + metadata.getIdempotencyKey();
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(20, TimeUnit.SECONDS)) {
                throw new IllegalStateException("并发测试会合超时");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("并发测试已中断", e);
        }
    }

    private interface PortInvocation<T> {
        T execute(SimpleCrmServerJdbcIdempotencyPort port);
    }
}
