package io.github.surezzzzzz.sdk.limiter.redis.smart.cases;

import io.github.surezzzzzz.sdk.limiter.redis.smart.SmartRedisLimiterApplication;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterRedisKeyConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.exception.SmartRedisLimitExceededException;
import io.github.surezzzzzz.sdk.limiter.redis.smart.service.TestService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 滑动窗口限流算法测试
 *
 * <p>测试场景：
 * <ul>
 *   <li>基本限流：5次/秒，第6次触发限流</li>
 *   <li>精度测试：窗口边界精确滑动</li>
 *   <li>并发限流：20并发，精确限制5次</li>
 *   <li>Key结构：滑动窗口Key包含sw后缀</li>
 *   <li>无突刺：任意时间段内请求数不超过阈值（与固定窗口的核心区别）</li>
 * </ul>
 *
 * @author Sure.
 * @Date: 2026-05-08
 */
@Slf4j
@SpringBootTest(classes = SmartRedisLimiterApplication.class)
public class SmartRedisLimiterSlidingWindowTest {

    @Autowired
    private TestService testService;

    @Autowired
    @Qualifier("smartRedisLimiterRedisTemplate")
    private RedisTemplate<String, String> smartRedisLimiterRedisTemplate;

    @BeforeEach
    public void setup() {
        log.info("=== 测试前准备：清理Redis ===");
        Set<String> keys = smartRedisLimiterRedisTemplate.keys(SmartRedisLimiterRedisKeyConstant.KEY_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            smartRedisLimiterRedisTemplate.delete(keys);
            log.info("清理了 {} 个限流key", keys.size());
        }
    }

    @AfterEach
    public void cleanup() {
        Set<String> keys = smartRedisLimiterRedisTemplate.keys(SmartRedisLimiterRedisKeyConstant.KEY_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            smartRedisLimiterRedisTemplate.delete(keys);
            log.info("清理了 {} 个限流key", keys.size());
        }
    }

    /**
     * 测试1：滑动窗口基本限流
     */
    @Test
    public void testSlidingWindowBasic() {
        log.info("=== 测试滑动窗口基本限流（1秒5次） ===");

        for (int i = 0; i < 5; i++) {
            String result = testService.slidingWindowMethod("test-" + i);
            assertEquals("sliding_success", result);
        }

        // 第6次应该被限流
        assertThrows(SmartRedisLimitExceededException.class, () -> {
            testService.slidingWindowMethod("exceed");
        });

        log.info("=== 滑动窗口基本限流测试通过 ===");
    }

    /**
     * 测试2：滑动窗口精度测试
     * 在窗口边界附近请求，验证滑动窗口的精确性
     */
    @Test
    public void testSlidingWindowPrecision() throws Exception {
        log.info("=== 测试滑动窗口精度 ===");

        // 快速请求5次，耗尽配额
        for (int i = 0; i < 5; i++) {
            testService.slidingWindowMethod("fast-" + i);
        }

        // 第6次应该被限流，验证窗口确实已满
        assertThrows(SmartRedisLimitExceededException.class, () ->
                testService.slidingWindowMethod("fast-exceed"));

        // 等待1秒窗口过期
        Thread.sleep(1100);

        // 窗口重置，应该又能请求
        String result = testService.slidingWindowMethod("after-window");
        assertEquals("sliding_success", result);

        log.info("=== 滑动窗口精度测试通过 ===");
    }

    /**
     * 测试3：滑动窗口并发限流
     */
    @Test
    public void testSlidingWindowConcurrent() throws Exception {
        log.info("=== 测试滑动窗口并发限流（1秒5次，20个并发） ===");

        int limit = 5;
        int concurrentRequests = 20;
        int poolSize = Math.min(concurrentRequests, 50);

        ExecutorService executorService = Executors.newFixedThreadPool(poolSize);
        CountDownLatch latch = new CountDownLatch(concurrentRequests);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < concurrentRequests; i++) {
            int taskId = i;
            executorService.submit(() -> {
                try {
                    testService.slidingWindowMethod("concurrent-" + taskId);
                    successCount.incrementAndGet();
                } catch (SmartRedisLimitExceededException e) {
                    log.debug("任务 {} 被限流", taskId);
                    failCount.incrementAndGet();
                } catch (Exception e) {
                    log.error("任务 {} 发生异常", taskId, e);
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS), "所有任务应该在10秒内完成");
        executorService.shutdown();
        assertTrue(executorService.awaitTermination(5, TimeUnit.SECONDS), "线程池应该在5秒内关闭");

        log.info("成功次数: {}, 失败次数: {}", successCount.get(), failCount.get());
        assertEquals(limit, successCount.get(), "成功次数应该等于限流阈值");
        assertEquals(concurrentRequests - limit, failCount.get(), "失败次数应该等于超出限流的请求数");
        assertEquals(concurrentRequests, successCount.get() + failCount.get(), "所有请求都应被计数（无未捕获异常）");

        log.info("=== 滑动窗口并发限流测试通过 ===");
    }

    /**
     * 测试4：验证滑动窗口Redis Key结构
     */
    @Test
    public void testSlidingWindowKeyStructure() {
        log.info("=== 测试滑动窗口Redis Key结构 ===");

        testService.slidingWindowMethod("key-test");

        Set<String> keys = smartRedisLimiterRedisTemplate.keys(SmartRedisLimiterRedisKeyConstant.KEY_PREFIX + "*");
        log.info("滑动窗口限流后的keys: {}", keys);

        assertNotNull(keys);
        assertFalse(keys.isEmpty(), "应该有限流key");

        // 滑动窗口的key应该包含 "sw" 后缀
        boolean hasSlidingWindowKey = keys.stream()
                .anyMatch(key -> key.contains(SmartRedisLimiterRedisKeyConstant.SUFFIX_SLIDING_WINDOW));
        assertTrue(hasSlidingWindowKey,
                "滑动窗口的key应该包含 [" + SmartRedisLimiterRedisKeyConstant.SUFFIX_SLIDING_WINDOW + "] 后缀，实际keys: " + keys);

        log.info("=== 滑动窗口Redis Key结构测试通过 ===");
    }

    /**
     * 测试5：滑动窗口无突刺（对比固定窗口）
     *
     * <p>滑动窗口的优势：任意时间段内请求数都不超过限流阈值，不存在边界突刺问题。
     *
     * <p>场景：限流 5次/秒
     * <ul>
     *   <li>T=0s：发 5 次（通过），窗口已满</li>
     *   <li>T=0.5s：第 6 次被拒绝（窗口内仍有 5 条记录，固定窗口此时已可通过）</li>
     *   <li>T=1.1s：第 7 次通过（最早的记录已过期，窗口滑动）</li>
     * </ul>
     *
     * <p>对比固定窗口（见 {@link SmartRedisLimiterFixedWindowTest#testFixedWindowBurstAtBoundary()}）：
     * 固定窗口在 1.1 秒内可通过 10 次请求，滑动窗口只能通过 6 次。
     */
    @Test
    public void testSlidingWindowNoBurstAtBoundary() throws Exception {
        log.info("=== 测试滑动窗口无突刺（对比固定窗口） ===");

        // T=0s：发5次请求，耗尽配额
        for (int i = 0; i < 5; i++) {
            testService.slidingWindowMethod("burst-" + i);
        }

        // T=0.5s：窗口未过期，第6次仍然被拒绝
        // （固定窗口此时已接近窗口边界，但滑动窗口内仍有5条记录）
        Thread.sleep(500);
        assertThrows(SmartRedisLimitExceededException.class, () ->
                        testService.slidingWindowMethod("burst-at-0.5s"),
                "0.5秒时窗口内仍有5条记录，应该被限流");

        // T=1.1s：最早的记录已过期，窗口滑动，可以再次请求
        Thread.sleep(600);
        String result = testService.slidingWindowMethod("burst-at-1.1s");
        assertEquals("sliding_success", result);

        log.info("=== 滑动窗口无突刺测试通过：0.5秒时被拒绝，1.1秒后才能通过 ===");
    }

    /**
     * 测试7：滑动窗口多时间窗口限流（3次/秒 + 10次/分钟）
     *
     * <p>验证两个窗口独立生效：
     * <ul>
     *   <li>短窗口（1秒）先触发：3次后被拒绝，等待1.1秒后恢复</li>
     *   <li>长窗口（1分钟）后触发：累计10次后被拒绝，短窗口重置也无法通过</li>
     *   <li>两个窗口的 Redis Key 都带 sw 后缀</li>
     * </ul>
     */
    @Test
    public void testSlidingWindowMultiWindow() throws Exception {
        log.info("=== 测试滑动窗口多时间窗口限流（3次/秒 + 10次/分钟） ===");

        // 第一批：3次通过，第4次触发短窗口（1秒）限流
        for (int i = 0; i < 3; i++) {
            String result = testService.slidingWindowMultiWindowMethod("batch1-" + i);
            assertEquals("sliding_multi_success", result);
        }
        assertThrows(SmartRedisLimitExceededException.class, () ->
                testService.slidingWindowMultiWindowMethod("batch1-exceed"),
                "第4次应触发1秒窗口限流");

        // 等待短窗口（1秒）过期
        Thread.sleep(1100);

        // 第二批：短窗口已重置，再发3次（累计6次，未触发长窗口）
        for (int i = 0; i < 3; i++) {
            String result = testService.slidingWindowMultiWindowMethod("batch2-" + i);
            assertEquals("sliding_multi_success", result);
        }

        // 等待短窗口再次过期
        Thread.sleep(1100);

        // 第三批：再发3次（累计9次，未触发长窗口）
        for (int i = 0; i < 3; i++) {
            String result = testService.slidingWindowMultiWindowMethod("batch3-" + i);
            assertEquals("sliding_multi_success", result);
        }

        // 等待短窗口再次过期
        Thread.sleep(1100);

        // 第四批：第1次通过（累计10次），第2次触发长窗口（1分钟）限流
        String result = testService.slidingWindowMultiWindowMethod("batch4-0");
        assertEquals("sliding_multi_success", result);

        assertThrows(SmartRedisLimitExceededException.class, () ->
                testService.slidingWindowMultiWindowMethod("batch4-exceed"),
                "第11次应触发1分钟窗口限流，即使短窗口已重置");

        // 验证两个窗口的 Redis Key 都存在且带 sw 后缀
        Set<String> keys = smartRedisLimiterRedisTemplate.keys(SmartRedisLimiterRedisKeyConstant.KEY_PREFIX + "*");
        assertNotNull(keys);

        long swKeys = keys.stream()
                .filter(k -> k.contains("slidingWindowMultiWindowMethod")
                        && k.contains(SmartRedisLimiterRedisKeyConstant.SUFFIX_SLIDING_WINDOW))
                .count();
        assertEquals(2, swKeys, "应该有2个滑动窗口key（1秒窗口 + 60秒窗口），实际keys: " + keys);

        log.info("=== 滑动窗口多时间窗口测试通过 ===");
    }
}

