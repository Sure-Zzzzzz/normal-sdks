package io.github.surezzzzzz.sdk.cache.test.cases;

import io.github.surezzzzzz.sdk.cache.manager.SmartCacheManager;
import io.github.surezzzzzz.sdk.cache.stats.CacheStats;
import io.github.surezzzzzz.sdk.cache.test.BaseSmartCacheTest;
import io.github.surezzzzzz.sdk.cache.test.SmartCacheTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 并发测试
 * <p>
 * 测试多线程并发场景下的缓存行为
 * </p>
 *
 * @author Sure
 */
@Slf4j
@SpringBootTest(classes = SmartCacheTestApplication.class)
public class ConcurrencyTest extends BaseSmartCacheTest {

    @Autowired
    private SmartCacheManager cacheManager;

    @org.junit.jupiter.api.BeforeEach
    public void setUp() {
        requireRedisAvailable();
        // 清理所有测试缓存
        cacheManager.clear("concurrent-test");
        cacheManager.clear("concurrent-diff-test");
        cacheManager.clear("concurrent-put-test");
        cacheManager.clear("concurrent-batch-test");
        cacheManager.clear("concurrent-rw-test");
        cacheManager.clear("concurrent-evict-test");
        cacheManager.clear("concurrent-clear-test");
        cacheManager.clear("concurrent-stats-test");
    }

    /**
     * 测试并发读取同一个 key（缓存击穿场景）
     */
    @Test
    public void testConcurrentGetSameKey() throws InterruptedException {
        String cacheName = "concurrent-test";
        String key = "same-key";
        int threadCount = 50;

        // 计数器：记录 loader 被调用的次数
        AtomicInteger loaderCallCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);

        List<Future<String>> futures = new ArrayList<>();

        // 启动多个线程同时获取同一个 key
        for (int i = 0; i < threadCount; i++) {
            Future<String> future = executor.submit(() -> {
                try {
                    startLatch.await(); // 等待所有线程就绪
                    String value = cacheManager.get(cacheName, key, () -> {
                        loaderCallCount.incrementAndGet();
                        Thread.sleep(100); // 模拟耗时操作
                        return "value-" + System.currentTimeMillis();
                    });
                    return value;
                } finally {
                    latch.countDown();
                }
            });
            futures.add(future);
        }

        // 所有线程同时开始
        startLatch.countDown();
        latch.await(10, TimeUnit.SECONDS);

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        // 验证所有线程都获取到了值
        Set<String> values = new HashSet<>();
        for (Future<String> future : futures) {
            try {
                String value = future.get();
                assertNotNull(value);
                values.add(value);
            } catch (Exception e) {
                fail("获取缓存值失败: " + e.getMessage());
            }
        }

        // 由于使用了分布式锁，loader 应该只被调用一次
        int callCount = loaderCallCount.get();
        log.info("并发 {} 个线程获取同一个 key，loader 被调用 {} 次", threadCount, callCount);
        assertEquals(1, callCount, "分布式锁应该防止缓存击穿，loader 应该只被调用1次");

        // 所有线程应该获取到相同的值
        assertEquals(1, values.size(), "所有线程应该获取到相同的缓存值");
    }

    /**
     * 测试并发读取不同的 key
     */
    @Test
    public void testConcurrentGetDifferentKeys() throws InterruptedException {
        String cacheName = "concurrent-diff-test";
        int threadCount = 100;
        int keyCount = 50;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        Map<String, AtomicInteger> loaderCallCounts = new ConcurrentHashMap<>();
        for (int i = 0; i < keyCount; i++) {
            loaderCallCounts.put("key-" + i, new AtomicInteger(0));
        }

        Random random = new Random();

        // 启动多个线程随机获取不同的 key
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    String key = "key-" + random.nextInt(keyCount);
                    String value = cacheManager.get(cacheName, key, () -> {
                        loaderCallCounts.get(key).incrementAndGet();
                        Thread.sleep(10);
                        return "value-" + key;
                    });
                    assertNotNull(value);
                    assertEquals("value-" + key, value);
                } catch (Exception e) {
                    fail("并发获取失败: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        // 验证每个 key 的 loader 调用次数
        int totalCalls = 0;
        int keysLoaded = 0;
        for (Map.Entry<String, AtomicInteger> entry : loaderCallCounts.entrySet()) {
            int count = entry.getValue().get();
            totalCalls += count;
            if (count > 0) {
                keysLoaded++;
                // 每个被访问的 key 应该只被加载一次（分布式锁保护）
                assertEquals(1, count, "Key " + entry.getKey() + " 应该只被加载1次");
            }
            log.info("Key: {}, loader 调用次数: {}", entry.getKey(), count);
        }

        log.info("总共 {} 个线程，loader 总调用次数: {}, 被加载的key数: {}", threadCount, totalCalls, keysLoaded);
        assertTrue(keysLoaded > 0, "至少应该有一些key被加载");
        assertEquals(keysLoaded, totalCalls, "总调用次数应该等于被加载的key数（每个key只加载1次）");
    }

    /**
     * 测试并发写入
     */
    @Test
    public void testConcurrentPut() throws InterruptedException {
        String cacheName = "concurrent-put-test";
        int threadCount = 50;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        // 启动多个线程并发写入
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    cacheManager.put(cacheName, "key-" + index, "value-" + index);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        // 验证所有值都写入成功
        for (int i = 0; i < threadCount; i++) {
            String value = cacheManager.get(cacheName, "key-" + i);
            assertNotNull(value, "key-" + i + " 应该存在");
            assertEquals("value-" + i, value, "key-" + i + " 的值应该正确");
        }
    }

    /**
     * 测试并发批量操作
     */
    @Test
    public void testConcurrentBatchOperations() throws InterruptedException {
        String cacheName = "concurrent-batch-test";
        int threadCount = 20;
        int batchSize = 100;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        // 启动多个线程并发批量写入
        for (int i = 0; i < threadCount; i++) {
            final int threadIndex = i;
            executor.submit(() -> {
                try {
                    Map<String, Object> entries = new HashMap<>();
                    for (int j = 0; j < batchSize; j++) {
                        String key = "thread-" + threadIndex + "-key-" + j;
                        entries.put(key, "value-" + j);
                    }
                    cacheManager.putAll(cacheName, entries);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        // 验证所有值都写入成功
        int successCount = 0;
        int totalKeys = threadCount * batchSize;
        for (int i = 0; i < threadCount; i++) {
            List<String> keys = new ArrayList<>();
            for (int j = 0; j < batchSize; j++) {
                keys.add("thread-" + i + "-key-" + j);
            }
            Map<String, Object> result = cacheManager.getAll(cacheName, keys);
            successCount += result.size();

            // 验证每个值都正确
            for (int j = 0; j < batchSize; j++) {
                String key = "thread-" + i + "-key-" + j;
                if (result.containsKey(key)) {
                    assertEquals("value-" + j, result.get(key), key + " 的值应该正确");
                }
            }
        }

        assertEquals(totalKeys, successCount, "所有key都应该存在");
        log.info("所有 {} 个key都成功写入和读取", totalKeys);
    }

    /**
     * 测试并发读写混合
     */
    @Test
    public void testConcurrentReadWrite() throws InterruptedException {
        String cacheName = "concurrent-rw-test";
        int threadCount = 100;
        int keyCount = 20;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        Random random = new Random();

        // 启动多个线程并发读写
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    String key = "key-" + random.nextInt(keyCount);
                    if (random.nextBoolean()) {
                        // 读操作
                        cacheManager.get(cacheName, key, () -> "value-" + key);
                    } else {
                        // 写操作
                        cacheManager.put(cacheName, key, "value-" + System.currentTimeMillis());
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        // 验证缓存状态正常
        CacheStats stats = cacheManager.getStats(cacheName);
        if (stats != null) {
            assertNotNull(stats);
            long totalRequests = stats.getTotalRequests();
            assertTrue(totalRequests > 0, "应该有请求记录");
            assertTrue(stats.getHitRate() >= 0 && stats.getHitRate() <= 100, "命中率应该在 0-100 之间");
            assertEquals(totalRequests,
                    stats.getL1HitCount() + stats.getL2HitCount() + stats.getMissCount(),
                    "总请求数应该等于各项统计之和");
            log.info("并发读写测试统计: {}", stats);
        }
    }

    /**
     * 测试并发 evict
     */
    @Test
    public void testConcurrentEvict() throws InterruptedException {
        String cacheName = "concurrent-evict-test";
        int keyCount = 50;
        int threadCount = 50;

        // 先写入数据
        for (int i = 0; i < keyCount; i++) {
            cacheManager.put(cacheName, "key-" + i, "value-" + i);
        }

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        Random random = new Random();

        // 启动多个线程并发删除
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    String key = "key-" + random.nextInt(keyCount);
                    cacheManager.evict(cacheName, key);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        // 验证部分 key 已被删除
        int existCount = 0;
        for (int i = 0; i < keyCount; i++) {
            String value = cacheManager.get(cacheName, "key-" + i);
            if (value != null) {
                existCount++;
            }
        }

        log.info("并发删除后，剩余 {} 个 key", existCount);
        // 由于并发删除，剩余数量应该小于初始数量
        assertTrue(existCount < keyCount, "应该有部分key被删除，剩余数量应该小于" + keyCount);
    }

    /**
     * 测试并发 clear
     */
    @Test
    public void testConcurrentClear() throws InterruptedException {
        String cacheName = "concurrent-clear-test";
        int threadCount = 10;

        // 先写入数据
        for (int i = 0; i < 100; i++) {
            cacheManager.put(cacheName, "key-" + i, "value-" + i);
        }

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        // 启动多个线程并发清空
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    cacheManager.clear(cacheName);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        // 验证缓存已清空
        long size = cacheManager.size(cacheName);
        assertEquals(0, size, "缓存应该已被清空");
    }

    /**
     * 测试高并发下的统计准确性
     */
    @Test
    public void testConcurrentStats() throws InterruptedException {
        String cacheName = "concurrent-stats-test";
        int threadCount = 100;
        int operationsPerThread = 50;

        // 先写入一些数据
        for (int i = 0; i < 20; i++) {
            cacheManager.put(cacheName, "key-" + i, "value-" + i);
        }

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        Random random = new Random();

        // 启动多个线程并发操作
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        String key = "key-" + random.nextInt(30);
                        cacheManager.get(cacheName, key, () -> "value-" + key);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        // 验证统计信息
        CacheStats stats = cacheManager.getStats(cacheName);
        if (stats != null) {
            assertNotNull(stats);
            long totalRequests = stats.getTotalRequests();
            log.info("并发统计测试: 总请求数={}, L1命中率={}, L2命中率={}, 总命中率={}",
                    totalRequests, stats.getL1HitRate(), stats.getL2HitRate(), stats.getHitRate());

            assertTrue(totalRequests > 0, "应该有请求记录");
            assertTrue(stats.getHitRate() >= 0 && stats.getHitRate() <= 100, "命中率应该在 0-100 之间");
            assertEquals(totalRequests,
                    stats.getL1HitCount() + stats.getL2HitCount() + stats.getMissCount(),
                    "总请求数应该等于各项统计之和");
        }
    }
}
