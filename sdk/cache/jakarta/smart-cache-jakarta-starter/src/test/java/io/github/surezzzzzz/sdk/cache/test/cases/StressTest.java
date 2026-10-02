package io.github.surezzzzzz.sdk.cache.test.cases;

import io.github.surezzzzzz.sdk.cache.manager.SmartCacheManager;
import io.github.surezzzzzz.sdk.cache.stats.CacheStats;
import io.github.surezzzzzz.sdk.cache.test.BaseSmartCacheTest;
import io.github.surezzzzzz.sdk.cache.test.SmartCacheTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 压力测试
 * <p>
 * 测试高并发、大数据量场景下的缓存性能和稳定性
 * </p>
 *
 * @author Sure
 * @since 1.0.0
 */
@Slf4j
@SpringBootTest(
        classes = SmartCacheTestApplication.class,
        properties = {
                "io.github.surezzzzzz.sdk.cache.consistency.mode=eventual",
                "io.github.surezzzzzz.sdk.cache.pubsub.mode=disabled"
        }
)
public class StressTest extends BaseSmartCacheTest {

    @Autowired
    private SmartCacheManager cacheManager;

    @BeforeEach
    public void setUp() {
        requireRedisAvailable();
        log.info("========== 初始化压力测试环境 ==========");
        cacheManager.clear("stress-test");
        log.info("压力测试环境初始化完成");
    }

    /**
     * 压力测试：高并发读写混合
     * 模拟真实生产环境的高并发场景
     */
    @Test
    public void testHighConcurrencyMixedOperations() throws InterruptedException {
        log.info("========== 压力测试：高并发读写混合 ==========");

        String cacheName = "stress-test";

        int threadCount = 100;
        int operationsPerThread = 1000;
        int keyRange = 100;

        log.info("测试规模: {}线程 × {}操作 = {}总操作",
                threadCount, operationsPerThread, threadCount * operationsPerThread);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        AtomicLong totalLatency = new AtomicLong(0);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);

        log.info("启动 {} 个线程，每个线程执行 {} 次操作", threadCount, operationsPerThread);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    Random random = new Random();

                    for (int j = 0; j < operationsPerThread; j++) {
                        String key = "key-" + random.nextInt(keyRange);
                        long start = System.nanoTime();

                        try {
                            int operation = random.nextInt(10);
                            if (operation < 7) {
                                // 70% 读操作
                                cacheManager.get(cacheName, key, () -> "value-" + key);
                            } else if (operation < 9) {
                                // 20% 写操作
                                cacheManager.put(cacheName, key, "value-" + System.currentTimeMillis());
                            } else {
                                // 10% 删除操作
                                cacheManager.evict(cacheName, key);
                            }
                            successCount.incrementAndGet();
                        } catch (Exception e) {
                            failureCount.incrementAndGet();
                            log.error("操作失败", e);
                        } finally {
                            long latency = System.nanoTime() - start;
                            totalLatency.addAndGet(latency);
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
        boolean finished = endLatch.await(120, TimeUnit.SECONDS);  // 恢复到120秒
        long testDuration = System.currentTimeMillis() - testStart;

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        // 验证结果
        assertTrue(finished, "所有线程应该在120秒内完成");

        int totalOperations = threadCount * operationsPerThread;
        int actualOperations = successCount.get() + failureCount.get();
        double successRate = (double) successCount.get() / actualOperations * 100;
        double avgLatencyMs = totalLatency.get() / (double) actualOperations / 1_000_000;
        double throughput = (double) actualOperations / testDuration * 1000;

        log.info("========== 压力测试结果 ==========");
        log.info("总操作数: {}", totalOperations);
        log.info("成功操作数: {}", successCount.get());
        log.info("失败操作数: {}", failureCount.get());
        log.info("成功率: {}%", String.format("%.2f", successRate));
        log.info("平均延迟: {} ms", String.format("%.2f", avgLatencyMs));
        log.info("吞吐量: {} ops/s", String.format("%.2f", throughput));
        log.info("总耗时: {} ms", testDuration);

        // 断言
        assertEquals(totalOperations, actualOperations, "实际操作数应该等于预期操作数");

        double expectedSuccessRate = 99.0;
        double expectedMaxLatency = 60;
        double expectedMinThroughput = 1000;

        log.info("性能要求: 成功率>={}%, 延迟<{}ms, 吞吐量>{}ops/s",
                expectedSuccessRate, expectedMaxLatency, expectedMinThroughput);

        assertTrue(successRate >= expectedSuccessRate,
                "成功率应该至少" + expectedSuccessRate + "%，实际: " + successRate + "%");
        assertTrue(avgLatencyMs < expectedMaxLatency,
                "平均延迟应该小于" + expectedMaxLatency + "ms，实际: " + avgLatencyMs + "ms");
        assertTrue(throughput > expectedMinThroughput,
                "吞吐量应该大于" + expectedMinThroughput + " ops/s，实际: " + throughput + " ops/s");

        log.info("✓ 高并发读写混合压力测试通过");
    }

    /**
     * 压力测试：大数据量写入
     */
    @Test
    public void testLargeDataVolumeWrite() throws InterruptedException {
        log.info("========== 压力测试：大数据量写入 ==========");

        String cacheName = "stress-test";

        int dataCount = 10000;

        log.info("测试数据量: {}", dataCount);

        long start = System.currentTimeMillis();

        // 批量写入
        Map<String, Object> largeDataSet = new HashMap<>();
        for (int i = 0; i < dataCount; i++) {
            largeDataSet.put("large-key-" + i, "large-value-" + i);
        }
        cacheManager.putAll(cacheName, largeDataSet);

        long writeDuration = System.currentTimeMillis() - start;
        double writeSpeed = (double) dataCount / writeDuration * 1000;

        log.info("写入 {} 条数据耗时: {} ms", dataCount, writeDuration);
        log.info("写入速度: {} records/s", String.format("%.2f", writeSpeed));

        // 验证数据完整性
        start = System.currentTimeMillis();
        int verifiedCount = 0;
        for (int i = 0; i < dataCount; i++) {
            String value = cacheManager.get(cacheName, "large-key-" + i);
            if (value != null && value.equals("large-value-" + i)) {
                verifiedCount++;
            }
        }
        long readDuration = System.currentTimeMillis() - start;
        double readSpeed = (double) dataCount / readDuration * 1000;

        log.info("读取验证 {} 条数据耗时: {} ms", dataCount, readDuration);
        log.info("读取速度: {} records/s", String.format("%.2f", readSpeed));
        log.info("数据完整性: {}/{}", verifiedCount, dataCount);

        double expectedMinWriteSpeed = 500;
        double expectedMinReadSpeed = 1000;
        double expectedDataIntegrity = 1.0;

        log.info("性能要求: 写入速度>{}records/s, 读取速度>{}records/s, 数据完整性>={}%",
                expectedMinWriteSpeed, expectedMinReadSpeed, expectedDataIntegrity * 100);

        // 断言
        double actualIntegrity = (double) verifiedCount / dataCount;
        assertTrue(actualIntegrity >= expectedDataIntegrity,
                "数据完整性应该至少" + (expectedDataIntegrity * 100) + "%，实际: " + (actualIntegrity * 100) + "%");
        assertTrue(writeSpeed > expectedMinWriteSpeed,
                "写入速度应该大于" + expectedMinWriteSpeed + " records/s，实际: " + writeSpeed);
        assertTrue(readSpeed > expectedMinReadSpeed,
                "读取速度应该大于" + expectedMinReadSpeed + " records/s，实际: " + readSpeed);

        log.info("✓ 大数据量写入压力测试通过");
    }

    /**
     * 压力测试：缓存击穿防护（热点数据并发访问）
     */
    @Test
    public void testCacheBreakdownProtectionUnderStress() throws InterruptedException {
        log.info("========== 压力测试：缓存击穿防护 ==========");

        String cacheName = "stress-test";
        String hotKey = "hot-key";
        int threadCount = 200;

        AtomicInteger loaderCallCount = new AtomicInteger(0);
        AtomicInteger successCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);

        log.info("启动 {} 个线程并发访问热点key", threadCount);

        long testStart = System.currentTimeMillis();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    String value = cacheManager.get(cacheName, hotKey, () -> {
                        loaderCallCount.incrementAndGet();
                        Thread.sleep(100); // 模拟耗时的数据库查询
                        return "hot-value";
                    });
                    if ("hot-value".equals(value)) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    log.error("访问热点key失败", e);
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean finished = endLatch.await(30, TimeUnit.SECONDS);
        long testDuration = System.currentTimeMillis() - testStart;

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        log.info("========== 缓存击穿防护测试结果 ==========");
        log.info("并发线程数: {}", threadCount);
        log.info("loader调用次数: {}", loaderCallCount.get());
        log.info("成功获取次数: {}", successCount.get());
        log.info("总耗时: {} ms", testDuration);

        // 断言
        assertTrue(finished, "所有线程应该在30秒内完成");
        assertEquals(1, loaderCallCount.get(), "分布式锁应该保证loader只被调用1次");
        assertEquals(threadCount, successCount.get(), "所有线程都应该成功获取到值");

        log.info("✓ 缓存击穿防护压力测试通过");
    }

    /**
     * 压力测试：持续负载稳定性测试
     */
    @Test
    public void testSustainedLoadStability() throws InterruptedException {
        log.info("========== 压力测试：持续负载稳定性 ==========");

        String cacheName = "stress-test";
        int threadCount = 50;
        int durationSeconds = 30;
        int keyRange = 50;

        AtomicLong operationCount = new AtomicLong(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        AtomicBoolean stopFlag = new AtomicBoolean(false);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Future<?>> futures = new ArrayList<>();

        log.info("启动 {} 个线程，持续运行 {} 秒", threadCount, durationSeconds);

        long testStart = System.currentTimeMillis();

        for (int i = 0; i < threadCount; i++) {
            Future<?> future = executor.submit(() -> {
                Random random = new Random();
                while (!stopFlag.get()) {
                    try {
                        String key = "sustained-key-" + random.nextInt(keyRange);
                        int operation = random.nextInt(10);

                        if (operation < 8) {
                            cacheManager.get(cacheName, key, () -> "value-" + key);
                        } else {
                            cacheManager.put(cacheName, key, "value-" + System.currentTimeMillis());
                        }

                        operationCount.incrementAndGet();
                        Thread.sleep(1); // 避免过度消耗CPU
                    } catch (Exception e) {
                        errorCount.incrementAndGet();
                        if (errorCount.get() > 100) {
                            log.error("错误次数过多，停止测试", e);
                            stopFlag.set(true);
                        }
                    }
                }
            });
            futures.add(future);
        }

        // 运行指定时间
        Thread.sleep(durationSeconds * 1000L);
        stopFlag.set(true);

        // 等待所有线程结束
        for (Future<?> future : futures) {
            try {
                future.get(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.warn("等待线程结束超时", e);
            }
        }

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        long testDuration = System.currentTimeMillis() - testStart;
        long totalOps = operationCount.get();
        double avgThroughput = (double) totalOps / testDuration * 1000;
        double errorRate = (double) errorCount.get() / totalOps * 100;

        log.info("========== 持续负载测试结果 ==========");
        log.info("运行时长: {} ms", testDuration);
        log.info("总操作数: {}", totalOps);
        log.info("错误次数: {}", errorCount.get());
        log.info("错误率: {}%", String.format("%.4f", errorRate));
        log.info("平均吞吐量: {} ops/s", String.format("%.2f", avgThroughput));

        // 获取统计信息
        CacheStats stats = cacheManager.getStats(cacheName);
        if (stats != null) {
            log.info("缓存统计: L1命中={}, L2命中={}, 未命中={}, 命中率={}%",
                    stats.getL1HitCount(), stats.getL2HitCount(), stats.getMissCount(),
                    String.format("%.2f", stats.getHitRate()));
        }

        // 断言
        assertTrue(totalOps > 10000, "30秒内应该完成至少10000次操作，实际: " + totalOps);
        assertTrue(errorRate < 1.0, "错误率应该小于1%，实际: " + errorRate + "%");
        assertTrue(avgThroughput > 300, "平均吞吐量应该大于300 ops/s，实际: " + avgThroughput);

        log.info("✓ 持续负载稳定性压力测试通过");
    }

    /**
     * 压力测试：批量操作性能
     */
    @Test
    public void testBatchOperationPerformance() {
        log.info("========== 压力测试：批量操作性能 ==========");

        String cacheName = "stress-test";

        int batchSize = 5000;
        int batchCount = 10;

        log.info("测试规模: {}批次 × {}条/批 = {}总记录",
                batchCount, batchSize, batchCount * batchSize);

        long totalWriteTime = 0;
        long totalReadTime = 0;

        for (int batch = 0; batch < batchCount; batch++) {
            // 批量写入
            Map<String, Object> batchData = new HashMap<>();
            for (int i = 0; i < batchSize; i++) {
                batchData.put("batch-" + batch + "-key-" + i, "batch-value-" + i);
            }

            long writeStart = System.currentTimeMillis();
            cacheManager.putAll(cacheName, batchData);
            long writeTime = System.currentTimeMillis() - writeStart;
            totalWriteTime += writeTime;

            // 批量读取
            List<String> keys = new ArrayList<>(batchData.keySet());
            long readStart = System.currentTimeMillis();
            Map<String, Object> result = cacheManager.getAll(cacheName, keys);
            long readTime = System.currentTimeMillis() - readStart;
            totalReadTime += readTime;

            // 验证读取结果
            assertEquals(batchSize, result.size(), "批次" + batch + "应该读取到所有数据");
        }

        double avgWriteTime = (double) totalWriteTime / batchCount;
        double avgReadTime = (double) totalReadTime / batchCount;
        double avgWriteSpeed = batchSize / avgWriteTime * 1000;
        double avgReadSpeed = batchSize / avgReadTime * 1000;

        log.info("========== 批量操作性能测试结果 ==========");
        log.info("批次数: {}, 每批大小: {}", batchCount, batchSize);
        log.info("平均批量写入耗时: {} ms", String.format("%.2f", avgWriteTime));
        log.info("平均批量读取耗时: {} ms", String.format("%.2f", avgReadTime));
        log.info("平均写入速度: {} records/s", String.format("%.2f", avgWriteSpeed));
        log.info("平均读取速度: {} records/s", String.format("%.2f", avgReadSpeed));

        double expectedMaxWriteTime = 2000;
        double expectedMaxReadTime = 500;
        double expectedMinWriteSpeed = 2500;
        double expectedMinReadSpeed = 10000;

        log.info("性能要求: 写入<{}ms, 读取<{}ms, 写入速度>{}records/s, 读取速度>{}records/s",
                expectedMaxWriteTime, expectedMaxReadTime, expectedMinWriteSpeed, expectedMinReadSpeed);

        // 断言
        assertTrue(avgWriteTime < expectedMaxWriteTime,
                "批量写入平均耗时应该小于" + (expectedMaxWriteTime / 1000) + "秒，实际: " + avgWriteTime + "ms");
        assertTrue(avgReadTime < expectedMaxReadTime,
                "批量读取平均耗时应该小于" + (expectedMaxReadTime / 1000) + "秒，实际: " + avgReadTime + "ms");
        assertTrue(avgWriteSpeed > expectedMinWriteSpeed,
                "写入速度应该大于" + expectedMinWriteSpeed + " records/s，实际: " + avgWriteSpeed);
        assertTrue(avgReadSpeed > expectedMinReadSpeed,
                "读取速度应该大于" + expectedMinReadSpeed + " records/s，实际: " + avgReadSpeed);

        log.info("✓ 批量操作性能压力测试通过");
    }
}
