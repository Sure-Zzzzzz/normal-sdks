package io.github.surezzzzzz.sdk.limiter.redis.smart.test.cases;

import io.github.surezzzzzz.sdk.limiter.redis.smart.configuration.SmartRedisLimiterProperties;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.client.SmartRedisLimiterManagementClient;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.client.model.SmartRedisLimiterPolicyFetchResult;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.client.model.SmartRedisLimiterTypedPolicyFetchResult;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.typed.SmartRedisLimiterTypedPolicySnapshot;
import io.github.surezzzzzz.sdk.limiter.redis.smart.typed.AtomicSmartRedisLimiterTypedPolicySnapshotStore;
import io.github.surezzzzzz.sdk.limiter.redis.smart.typed.DefaultSmartRedisLimiterTypedPolicyRefreshManager;
import io.github.surezzzzzz.sdk.limiter.redis.smart.typed.SmartRedisLimiterAcceptedTypedPolicySnapshot;
import io.github.surezzzzzz.sdk.limiter.redis.smart.typed.SmartRedisLimiterTypedPolicySnapshotStore;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 类型化刷新链测试：拉取消费、代次门禁、last-known-good 保留
 *
 * @author surezzzzzz
 */
@Slf4j
class SmartRedisLimiterTypedRefreshTest {

    private SmartRedisLimiterProperties properties;
    private SmartRedisLimiterTypedPolicySnapshotStore store;

    private static SmartRedisLimiterTypedPolicySnapshot snapshot(long epoch, long revision) {
        return new SmartRedisLimiterTypedPolicySnapshot(
                io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterConstant
                        .TYPED_POLICY_SCHEMA_VERSION,
                "mock-service", epoch, revision, Instant.now(),
                java.util.Collections.emptyList());
    }

    @BeforeEach
    public void setUp() {
        properties = new SmartRedisLimiterProperties();
        properties.setMe("mock-service");
        properties.getTyped().setEnabled(true);
        properties.getTyped().setExpectedPolicyEpoch(3L);
        store = new AtomicSmartRedisLimiterTypedPolicySnapshotStore();
    }

    @Test
    public void testRefreshAcceptsMatchingEpochAndStoresSnapshot() {
        AtomicInteger fetchCount = new AtomicInteger();
        DefaultSmartRedisLimiterTypedPolicyRefreshManager manager =
                new DefaultSmartRedisLimiterTypedPolicyRefreshManager(
                        properties,
                        new StubClient() {
                            @Override
                            public SmartRedisLimiterTypedPolicyFetchResult fetchTypedPolicy(
                                    String serviceCode, String currentEtag) {
                                fetchCount.incrementAndGet();
                                return SmartRedisLimiterTypedPolicyFetchResult.fetched(
                                        "\"e1\"", snapshot(3L, 7L));
                            }
                        },
                        store);
        assertTrue(manager.refresh(), "首刷应执行");
        SmartRedisLimiterAcceptedTypedPolicySnapshot accepted = store.getCurrent();
        assertNotNull(accepted, "代次匹配的快照应被接受");
        assertEquals(7L, accepted.getSnapshot().getRevision());
        assertEquals("\"e1\"", accepted.getEtag());
        assertNull(manager.getLastFailure(), "成功刷新不应记录失败");
        assertEquals(1, fetchCount.get());
        manager.destroy();
    }

    @Test
    public void testEpochMismatchKeepsLastKnownGood() {
        SmartRedisLimiterAcceptedTypedPolicySnapshot good =
                new SmartRedisLimiterAcceptedTypedPolicySnapshot(snapshot(3L, 7L), "\"e1\"", Instant.now());
        store.replace(good);
        DefaultSmartRedisLimiterTypedPolicyRefreshManager manager =
                new DefaultSmartRedisLimiterTypedPolicyRefreshManager(
                        properties,
                        new StubClient() {
                            @Override
                            public SmartRedisLimiterTypedPolicyFetchResult fetchTypedPolicy(
                                    String serviceCode, String currentEtag) {
                                return SmartRedisLimiterTypedPolicyFetchResult.fetched(
                                        "\"e2\"", snapshot(4L, 8L));
                            }
                        },
                        store);
        assertTrue(manager.refresh(), "刷新应执行");
        assertSame(good, store.getCurrent(), "代次不符的快照不得替换 last-known-good");
        assertNotNull(manager.getLastFailure(), "代次不符应记录失败");
        manager.destroy();
    }

    @Test
    public void testFetchFailureKeepsLastKnownGood() {
        SmartRedisLimiterAcceptedTypedPolicySnapshot good =
                new SmartRedisLimiterAcceptedTypedPolicySnapshot(snapshot(3L, 7L), "\"e1\"", Instant.now());
        store.replace(good);
        DefaultSmartRedisLimiterTypedPolicyRefreshManager manager =
                new DefaultSmartRedisLimiterTypedPolicyRefreshManager(
                        properties,
                        new StubClient() {
                            @Override
                            public SmartRedisLimiterTypedPolicyFetchResult fetchTypedPolicy(
                                    String serviceCode, String currentEtag) {
                                throw new IllegalStateException("connection refused");
                            }
                        },
                        store);
        assertTrue(manager.refresh(), "刷新应执行");
        assertSame(good, store.getCurrent(), "拉取失败不得覆盖 last-known-good");
        assertNotNull(manager.getLastFailure());
        manager.destroy();
    }

    @Test
    public void testNotModifiedKeepsCurrent() {
        SmartRedisLimiterAcceptedTypedPolicySnapshot good =
                new SmartRedisLimiterAcceptedTypedPolicySnapshot(snapshot(3L, 7L), "\"e1\"", Instant.now());
        store.replace(good);
        AtomicReference<String> lastEtag = new AtomicReference<>();
        DefaultSmartRedisLimiterTypedPolicyRefreshManager manager =
                new DefaultSmartRedisLimiterTypedPolicyRefreshManager(
                        properties,
                        new StubClient() {
                            @Override
                            public SmartRedisLimiterTypedPolicyFetchResult fetchTypedPolicy(
                                    String serviceCode, String currentEtag) {
                                lastEtag.set(currentEtag);
                                return SmartRedisLimiterTypedPolicyFetchResult.notModified();
                            }
                        },
                        store);
        assertTrue(manager.refresh());
        assertSame(good, store.getCurrent(), "304 不得替换已接受快照");
        assertEquals("\"e1\"", lastEtag.get(), "条件请求应携带当前 ETag");
        assertNull(manager.getLastFailure());
        manager.destroy();
    }

    /**
     * 桩客户端：默认不修改，子类覆写 fetchTypedPolicy
     */
    private static class StubClient implements SmartRedisLimiterManagementClient {
        @Override
        public SmartRedisLimiterPolicyFetchResult fetchPolicy(String serviceCode, String currentEtag) {
            throw new UnsupportedOperationException("typed 刷新链不使用 v1 拉取");
        }

        @Override
        public SmartRedisLimiterTypedPolicyFetchResult fetchTypedPolicy(String serviceCode, String currentEtag) {
            return SmartRedisLimiterTypedPolicyFetchResult.notModified();
        }
    }
}
