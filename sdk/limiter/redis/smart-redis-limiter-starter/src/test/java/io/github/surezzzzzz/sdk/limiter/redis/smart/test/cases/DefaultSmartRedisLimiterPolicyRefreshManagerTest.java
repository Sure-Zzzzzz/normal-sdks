package io.github.surezzzzzz.sdk.limiter.redis.smart.test.cases;

import io.github.surezzzzzz.sdk.limiter.redis.smart.configuration.SmartRedisLimiterProperties;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterTimeUnit;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.starter.ErrorCode;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.SmartRedisLimiterLimit;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.SmartRedisLimiterPolicy;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.SmartRedisLimiterPolicyKey;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.SmartRedisLimiterPolicySnapshot;
import io.github.surezzzzzz.sdk.limiter.redis.smart.policy.AtomicSmartRedisLimiterPolicySnapshotStore;
import io.github.surezzzzzz.sdk.limiter.redis.smart.policy.DefaultSmartRedisLimiterPolicyRefreshManager;
import io.github.surezzzzzz.sdk.limiter.redis.smart.policy.DefaultSmartRedisLimiterPolicySnapshotValidator;
import io.github.surezzzzzz.sdk.limiter.redis.smart.policy.client.SmartRedisLimiterPolicyClient;
import io.github.surezzzzzz.sdk.limiter.redis.smart.policy.client.SmartRedisLimiterPolicyFetchResult;
import io.github.surezzzzzz.sdk.limiter.redis.smart.policy.model.SmartRedisLimiterAcceptedPolicySnapshot;
import io.github.surezzzzzz.sdk.limiter.redis.smart.policy.model.SmartRedisLimiterPolicyRefreshState;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Collections;
import java.util.concurrent.ScheduledFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 远程策略刷新管理器契约测试
 *
 * @author surezzzzzz
 */
public class DefaultSmartRedisLimiterPolicyRefreshManagerTest {

    @Test
    public void testAcceptedSnapshotAndNotModifiedKeepLastKnownGood() {
        QueuePolicyClient client = new QueuePolicyClient(
                SmartRedisLimiterPolicyFetchResult.fetched("\"v1\"", snapshot(1L, 10L)),
                SmartRedisLimiterPolicyFetchResult.notModified());
        AtomicSmartRedisLimiterPolicySnapshotStore store = new AtomicSmartRedisLimiterPolicySnapshotStore();
        DefaultSmartRedisLimiterPolicyRefreshManager manager = manager(client, store);

        try {
            assertTrue(manager.refresh(), "首次 200 刷新必须执行");
            SmartRedisLimiterAcceptedPolicySnapshot accepted = store.getCurrent();
            SmartRedisLimiterPolicyRefreshState firstState = manager.getRefreshState();
            assertNotNull(accepted, "首次 200 必须接受快照");
            assertEquals(1L, accepted.getRevision(), "必须保存服务端 revision");
            assertEquals("\"v1\"", accepted.getEtag(), "必须保存服务端 ETag");
            assertTrue(firstState.isLastAttemptSuccessful(), "首次 200 必须记录成功状态");
            assertEquals(1L, firstState.getAcceptedRevision(), "状态必须记录已接受 revision");
            assertEquals("\"v1\"", firstState.getAcceptedEtag(), "状态必须记录已接受 ETag");
            assertNotNull(firstState.getLastAttemptAt(), "状态必须记录尝试时间");
            assertNotNull(firstState.getLastSuccessAt(), "状态必须记录成功时间");

            assertTrue(manager.refresh(), "已有快照后的 304 刷新必须执行");
            SmartRedisLimiterPolicyRefreshState secondState = manager.getRefreshState();
            assertEquals("\"v1\"", client.getLastEtag(), "304 请求必须携带已接受 ETag");
            assertSame(accepted, store.getCurrent(), "304 不得替换 last-known-good 快照");
            assertTrue(secondState.isLastAttemptSuccessful(), "304 必须作为成功刷新记录");
            assertEquals(1L, secondState.getAcceptedRevision(), "304 必须保留已接受 revision");
            assertEquals("\"v1\"", secondState.getAcceptedEtag(), "304 必须保留已接受 ETag");
        } finally {
            manager.destroy();
        }
    }

    @Test
    public void testFailureAndInitialNotModifiedKeepStoreSafe() {
        QueuePolicyClient client = new QueuePolicyClient(
                SmartRedisLimiterPolicyFetchResult.fetched("\"v1\"", snapshot(1L, 10L)),
                new IllegalStateException("management unavailable"));
        AtomicSmartRedisLimiterPolicySnapshotStore store = new AtomicSmartRedisLimiterPolicySnapshotStore();
        DefaultSmartRedisLimiterPolicyRefreshManager manager = manager(client, store);

        try {
            assertTrue(manager.refresh(), "首次快照刷新必须执行");
            SmartRedisLimiterAcceptedPolicySnapshot accepted = store.getCurrent();

            assertTrue(manager.refresh(), "失败刷新必须完成状态记录");
            SmartRedisLimiterPolicyRefreshState failureState = manager.getRefreshState();
            assertSame(accepted, store.getCurrent(), "失败刷新不得丢弃 last-known-good 快照");
            assertFalse(failureState.isLastAttemptSuccessful(), "失败刷新必须记录失败状态");
            assertEquals("IllegalStateException", failureState.getLastFailureReason(), "失败原因必须稳定分类");
            assertEquals(1L, failureState.getAcceptedRevision(), "失败后必须保留已接受 revision");
            assertEquals("\"v1\"", failureState.getAcceptedEtag(), "失败后必须保留已接受 ETag");

            QueuePolicyClient initialNotModifiedClient = new QueuePolicyClient(
                    SmartRedisLimiterPolicyFetchResult.notModified());
            AtomicSmartRedisLimiterPolicySnapshotStore emptyStore = new AtomicSmartRedisLimiterPolicySnapshotStore();
            DefaultSmartRedisLimiterPolicyRefreshManager initialNotModifiedManager =
                    manager(initialNotModifiedClient, emptyStore);
            try {
                assertTrue(initialNotModifiedManager.refresh(), "首次 304 也必须完成状态记录");
                SmartRedisLimiterPolicyRefreshState initialState = initialNotModifiedManager.getRefreshState();
                assertNull(emptyStore.getCurrent(), "首次 304 不得生成空的已接受快照");
                assertFalse(initialState.isLastAttemptSuccessful(), "首次 304 必须记录失败");
                assertEquals(ErrorCode.POLICY_RESPONSE_INVALID, initialState.getLastFailureReason(),
                        "首次 304 必须使用稳定错误分类");
            } finally {
                initialNotModifiedManager.destroy();
            }
        } finally {
            manager.destroy();
        }
    }

    @Test
    public void testReadyEventRegistersOnceAndDestroyStopsRefresh() {
        QueuePolicyClient client = new QueuePolicyClient(
                SmartRedisLimiterPolicyFetchResult.fetched("\"v1\"", snapshot(1L, 10L)));
        AtomicSmartRedisLimiterPolicySnapshotStore store = new AtomicSmartRedisLimiterPolicySnapshotStore();
        DefaultSmartRedisLimiterPolicyRefreshManager manager = manager(client, store);
        managerProperties(manager).getRemotePolicy().setInitialRefresh(false);
        managerProperties(manager).getRemotePolicy().setRefreshIntervalMillis(60000L);

        manager.onApplicationEvent(null);
        ScheduledFuture<?> firstFuture = (ScheduledFuture<?>) ReflectionTestUtils.getField(manager, "scheduledFuture");
        manager.onApplicationEvent(null);

        try {
            assertNotNull(firstFuture, "应用就绪后必须注册刷新任务");
            assertSame(firstFuture, ReflectionTestUtils.getField(manager, "scheduledFuture"),
                    "重复就绪事件不得注册第二个刷新任务");
            manager.destroy();
            assertFalse(manager.refresh(), "关闭后不得再执行刷新");
            assertEquals(0, client.getFetchCount(), "关闭后的刷新不得访问远程客户端");
        } finally {
            manager.destroy();
        }
    }

    private DefaultSmartRedisLimiterPolicyRefreshManager manager(
            SmartRedisLimiterPolicyClient client,
            AtomicSmartRedisLimiterPolicySnapshotStore store) {
        SmartRedisLimiterProperties properties = properties();
        return new DefaultSmartRedisLimiterPolicyRefreshManager(
                properties,
                client,
                new DefaultSmartRedisLimiterPolicySnapshotValidator(properties),
                store);
    }

    private SmartRedisLimiterProperties managerProperties(DefaultSmartRedisLimiterPolicyRefreshManager manager) {
        return (SmartRedisLimiterProperties) ReflectionTestUtils.getField(manager, "properties");
    }

    private SmartRedisLimiterProperties properties() {
        SmartRedisLimiterProperties properties = new SmartRedisLimiterProperties();
        properties.setMe("test-service");
        properties.getRemotePolicy().setEnable(true);
        properties.getRemotePolicy().setSnapshotUrl("http://management.internal/api/v1/policy/snapshot");
        return properties;
    }

    private SmartRedisLimiterPolicySnapshot snapshot(long revision, long count) {
        SmartRedisLimiterPolicyKey key = new SmartRedisLimiterPolicyKey(
                "test-service", "test-resource", "test-subject");
        SmartRedisLimiterPolicy policy = new SmartRedisLimiterPolicy(
                key,
                Collections.singletonList(new SmartRedisLimiterLimit(
                        count, 1L, SmartRedisLimiterTimeUnit.SECONDS)));
        return new SmartRedisLimiterPolicySnapshot(
                SmartRedisLimiterConstant.POLICY_SCHEMA_VERSION,
                "test-service",
                revision,
                Instant.parse("2026-07-22T00:00:00Z"),
                Collections.singletonList(policy));
    }

    private static final class QueuePolicyClient implements SmartRedisLimiterPolicyClient {

        private final Object[] results;
        private int index;
        private int fetchCount;
        private String lastEtag;

        private QueuePolicyClient(Object... results) {
            this.results = results;
        }

        @Override
        public SmartRedisLimiterPolicyFetchResult fetch(String serviceCode, String currentEtag) {
            fetchCount++;
            lastEtag = currentEtag;
            Object result = results[Math.min(index++, results.length - 1)];
            if (result instanceof RuntimeException) {
                throw (RuntimeException) result;
            }
            return (SmartRedisLimiterPolicyFetchResult) result;
        }

        private int getFetchCount() {
            return fetchCount;
        }

        private String getLastEtag() {
            return lastEtag;
        }
    }
}
