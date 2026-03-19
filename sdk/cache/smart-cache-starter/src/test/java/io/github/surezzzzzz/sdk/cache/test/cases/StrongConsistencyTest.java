package io.github.surezzzzzz.sdk.cache.test.cases;

import io.github.surezzzzzz.sdk.cache.cache.L1Cache;
import io.github.surezzzzzz.sdk.cache.manager.SmartCacheManager;
import io.github.surezzzzzz.sdk.cache.test.BaseSmartCacheTest;
import io.github.surezzzzzz.sdk.cache.test.SmartCacheTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Strong Consistency Test
 * <p>
 * 强一致性模式测试
 * </p>
 *
 * @author Sure
 * @since 1.0.0
 */
@Slf4j
@SpringBootTest(classes = SmartCacheTestApplication.class)
@ActiveProfiles("strong")
public class StrongConsistencyTest extends BaseSmartCacheTest {

    @Autowired
    private SmartCacheManager cacheManager;

    @Autowired
    private L1Cache l1Cache;

    @BeforeEach
    public void setUp() {
        log.info("========== 初始化测试环境 ==========");
        cacheManager.clear("testCache");
        log.info("测试环境初始化完成，Redis可用: {}", isRedisAvailable());
    }

    /**
     * 测试场景：强一致性模式下的缓存失效
     */
    @Test
    public void testStrongConsistency() throws Exception {
        // 如果Redis不可用，测试降级行为
        if (!isRedisAvailable()) {
            testDegradationBehavior();
            return;
        }

        log.info("========== 端到端测试：强一致性模式 ==========");

        // Given - 写入缓存数据
        log.info("【步骤 1】写入缓存数据");
        cacheManager.put("testCache", "key1", "value1");

        // 验证数据已写入
        String value1 = cacheManager.get("testCache", "key1");
        assertEquals("value1", value1);
        log.info("缓存数据已写入: {}", value1);

        // When - 删除缓存（应该发布 Pub/Sub 消息）
        log.info("【步骤 2】删除缓存");
        cacheManager.evict("testCache", "key1");

        // 等待 Pub/Sub 消息传播
        Thread.sleep(100);

        // Then - 验证缓存已失效
        log.info("【步骤 3】验证缓存已失效");
        String value2 = cacheManager.get("testCache", "key1");
        assertNull(value2, "缓存应该已被删除");

        log.info("✓ 强一致性模式测试通过");
    }

    /**
     * 降级行为测试：Redis不可用时的基本功能验证
     */
    private void testDegradationBehavior() {
        log.info("========== 测试：强一致性降级行为 ==========");

        // 验证基本缓存功能仍然可用
        cacheManager.put("testCache", "key1", "value1");
        assertEquals("value1", cacheManager.get("testCache", "key1"));

        // 验证L1工作正常
        assertNotNull(l1Cache.get("testCache", "key1"));

        // 验证删除功能
        cacheManager.evict("testCache", "key1");
        assertNull(cacheManager.get("testCache", "key1"));

        log.info("✓ 降级行为验证通过：基本缓存功能正常，但不保证跨实例一致性");
    }

    /**
     * 测试场景：强一致性模式下的缓存清空
     */
    @Test
    public void testStrongConsistencyClear() throws Exception {
        // Redis不可用时跳过
        if (shouldSkipRedisTest("testStrongConsistencyClear")) {
            return;
        }

        log.info("========== 端到端测试：强一致性清空 ==========");

        // Given - 写入多个缓存数据
        log.info("【步骤 1】写入多个缓存数据");
        cacheManager.put("testCache", "key1", "value1");
        cacheManager.put("testCache", "key2", "value2");
        cacheManager.put("testCache", "key3", "value3");

        // 验证数据已写入
        assertEquals("value1", cacheManager.get("testCache", "key1"));
        assertEquals("value2", cacheManager.get("testCache", "key2"));
        assertEquals("value3", cacheManager.get("testCache", "key3"));

        // When - 清空缓存（应该发布 Pub/Sub 消息）
        log.info("【步骤 2】清空缓存");
        cacheManager.clear("testCache");

        // 等待 Pub/Sub 消息传播
        Thread.sleep(100);

        // Then - 验证所有缓存已失效
        log.info("【步骤 3】验证所有缓存已失效");
        assertNull(cacheManager.get("testCache", "key1"));
        assertNull(cacheManager.get("testCache", "key2"));
        assertNull(cacheManager.get("testCache", "key3"));

        log.info("✓ 强一致性清空测试通过");
    }

    /**
     * 测试场景：强一致性模式下的并发更新
     */
    @Test
    public void testStrongConsistencyConcurrentUpdate() throws Exception {
        log.info("========== 端到端测试：强一致性并发更新 ==========");

        // Given - 写入初始数据
        log.info("【步骤 1】写入初始数据");
        cacheManager.put("testCache", "counter", 0);

        // When - 并发更新缓存
        log.info("【步骤 2】并发更新缓存");
        int threadCount = 5;
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final int value = i + 1;
            new Thread(() -> {
                try {
                    cacheManager.put("testCache", "counter", value);
                    Thread.sleep(10);
                    cacheManager.evict("testCache", "counter");
                } catch (Exception e) {
                    log.error("并发更新失败", e);
                } finally {
                    latch.countDown();
                }
            }).start();
        }

        // 等待所有线程完成
        assertTrue(latch.await(5, TimeUnit.SECONDS));

        // 等待 Pub/Sub 消息传播
        Thread.sleep(200);

        // Then - 验证最终一致性
        log.info("【步骤 3】验证最终一致性");
        Object finalValue = cacheManager.get("testCache", "counter");
        assertNull(finalValue, "所有更新后缓存应该被清空");

        log.info("✓ 强一致性并发更新测试通过");
    }

    /**
     * 测试场景：高并发场景下的强一致性
     */
    @Test
    public void testStrongConsistencyUnderHighConcurrency() throws Exception {
        log.info("========== 压力测试：高并发强一致性 ==========");

        String cacheName = "concurrency-test";
        int threadCount = 50;
        int operationsPerThread = 100;

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);

        log.info("启动 {} 个线程，每个线程执行 {} 次操作", threadCount, operationsPerThread);

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startLatch.await();

                    for (int j = 0; j < operationsPerThread; j++) {
                        String key = "key-" + (threadId % 10);
                        String value = "value-" + threadId + "-" + j;

                        try {
                            // 写入
                            cacheManager.put(cacheName, key, value);
                            // 读取
                            Object cached = cacheManager.get(cacheName, key);
                            // 删除
                            cacheManager.evict(cacheName, key);

                            successCount.incrementAndGet();
                        } catch (Exception e) {
                            failureCount.incrementAndGet();
                            log.error("操作失败", e);
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        long testStart = System.currentTimeMillis();
        startLatch.countDown();
        boolean finished = endLatch.await(60, TimeUnit.SECONDS);
        long testDuration = System.currentTimeMillis() - testStart;

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        // 等待 Pub/Sub 消息传播
        Thread.sleep(500);

        // 验证结果
        assertTrue(finished, "所有线程应该在60秒内完成");

        int totalOperations = threadCount * operationsPerThread;
        int actualOperations = successCount.get() + failureCount.get();
        double successRate = (double) successCount.get() / actualOperations * 100;

        log.info("========== 高并发强一致性测试结果 ==========");
        log.info("总操作数: {}", totalOperations);
        log.info("成功操作数: {}", successCount.get());
        log.info("失败操作数: {}", failureCount.get());
        log.info("成功率: {}%", String.format("%.2f", successRate));
        log.info("总耗时: {} ms", testDuration);

        // 断言
        assertEquals(totalOperations, actualOperations, "实际操作数应该等于预期操作数");
        assertTrue(successRate >= 99.0, "成功率应该至少99%，实际: " + successRate + "%");

        log.info("✓ 高并发强一致性测试通过");
    }

    /**
     * 测试场景：批量操作的强一致性
     */
    @Test
    public void testStrongConsistencyBatchOperations() throws Exception {
        log.info("========== 测试：批量操作强一致性 ==========");

        String cacheName = "batch-test";

        // Given - 批量写入数据
        log.info("【步骤 1】批量写入数据");
        java.util.Map<String, Object> data = new java.util.HashMap<>();
        for (int i = 0; i < 100; i++) {
            data.put("batch-key-" + i, "batch-value-" + i);
        }
        cacheManager.putAll(cacheName, data);

        // 验证数据已写入
        for (int i = 0; i < 100; i++) {
            assertNotNull(cacheManager.get(cacheName, "batch-key-" + i));
        }
        log.info("批量写入 100 条数据完成");

        // When - 清空缓存
        log.info("【步骤 2】清空缓存");
        cacheManager.clear(cacheName);

        // 等待 Pub/Sub 消息传播
        Thread.sleep(200);

        // Then - 验证所有数据已清空
        log.info("【步骤 3】验证所有数据已清空");
        for (int i = 0; i < 100; i++) {
            assertNull(cacheManager.get(cacheName, "batch-key-" + i),
                    "batch-key-" + i + " 应该已被清空");
        }

        log.info("✓ 批量操作强一致性测试通过");
    }

    /**
     * 测试场景：多缓存空间的强一致性隔离
     */
    @Test
    public void testStrongConsistencyMultipleCacheSpaces() throws Exception {
        log.info("========== 测试：多缓存空间强一致性隔离 ==========");

        // Given - 在不同缓存空间写入数据
        log.info("【步骤 1】在不同缓存空间写入数据");
        cacheManager.put("cache1", "key1", "value1-cache1");
        cacheManager.put("cache2", "key1", "value1-cache2");
        cacheManager.put("cache3", "key1", "value1-cache3");

        // 验证数据已写入
        assertEquals("value1-cache1", cacheManager.get("cache1", "key1"));
        assertEquals("value1-cache2", cacheManager.get("cache2", "key1"));
        assertEquals("value1-cache3", cacheManager.get("cache3", "key1"));

        // When - 清空 cache1
        log.info("【步骤 2】清空 cache1");
        cacheManager.clear("cache1");

        // 等待 Pub/Sub 消息传播
        Thread.sleep(100);

        // Then - 验证只有 cache1 被清空，其他缓存空间不受影响
        log.info("【步骤 3】验证缓存空间隔离");
        assertNull(cacheManager.get("cache1", "key1"), "cache1 应该被清空");
        assertEquals("value1-cache2", cacheManager.get("cache2", "key1"), "cache2 不应该受影响");
        assertEquals("value1-cache3", cacheManager.get("cache3", "key1"), "cache3 不应该受影响");

        // 清理
        cacheManager.clear("cache2");
        cacheManager.clear("cache3");

        log.info("✓ 多缓存空间强一致性隔离测试通过");
    }
}
