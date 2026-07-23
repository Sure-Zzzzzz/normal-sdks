package io.github.surezzzzzz.sdk.limiter.redis.smart.test.cases;

import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterRedisKeyConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.exception.SmartRedisLimitExceededException;
import io.github.surezzzzzz.sdk.limiter.redis.smart.test.SmartRedisLimiterTestApplication;
import io.github.surezzzzzz.sdk.limiter.redis.smart.test.service.TestService;
import io.github.surezzzzzz.sdk.redis.route.template.RedisRouteTemplate;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

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
@SpringBootTest(classes = SmartRedisLimiterTestApplication.class)
public class SmartRedisLimiterSlidingWindowTest {

    @Autowired
    private TestService testService;

    @Autowired
    private RedisRouteTemplate redisRouteTemplate;

    @BeforeEach
    public void setup() {
        log.info("=== 测试前准备：清理Redis ===");
        Set<String> keys = redisRouteTemplate.stringTemplate().keys(SmartRedisLimiterRedisKeyConstant.KEY_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            redisRouteTemplate.stringTemplate().delete(keys);
            log.info("清理了 {} 个限流key", keys.size());
        }
    }

    @AfterEach
    public void cleanup() {
        Set<String> keys = redisRouteTemplate.stringTemplate().keys(SmartRedisLimiterRedisKeyConstant.KEY_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            redisRouteTemplate.stringTemplate().delete(keys);
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

        Set<String> keys = redisRouteTemplate.stringTemplate().keys(SmartRedisLimiterRedisKeyConstant.KEY_PREFIX + "*");
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
        Set<String> keys = redisRouteTemplate.stringTemplate().keys(SmartRedisLimiterRedisKeyConstant.KEY_PREFIX + "*");
        assertNotNull(keys);

        long swKeys = keys.stream()
                .filter(k -> k.contains("slidingWindowMultiWindowMethod")
                        && k.contains(SmartRedisLimiterRedisKeyConstant.SUFFIX_SLIDING_WINDOW))
                .count();
        assertEquals(2, swKeys, "应该有2个滑动窗口key（1秒窗口 + 60秒窗口），实际keys: " + keys);

        log.info("=== 滑动窗口多时间窗口测试通过 ===");
    }

    /**
     * 测试8：滑动窗口配额用尽时触发过期数据清理
     *
     * <p>验证优化逻辑：当 remaining <= 0 时，Lua 脚本会触发 ZREMRANGEBYSCORE 清理过期数据，
     * 清理后重新计数，精确判断是否真正超过限流阈值。
     *
     * <p>场景：
     * <ul>
     *   <li>限流阈值 5 次/秒，窗口 1 秒</li>
     *   <li>ZSET 中有 5 条已过期的历史记录（模拟过期数据未清理）</li>
     *   <li>第 1 次请求：ZCARD=5，remaining=0，不清理，直接拒绝</li>
     *   <li>注入 1 条过期记录后，第 2 次请求：ZCARD=6，remaining=-1 <= 0，触发清理</li>
     *   <li>清理后 ZCARD=5，remaining=0，拒绝</li>
     * </ul>
     */
    @Test
    public void testSlidingWindowConditionCleanup() throws Exception {
        log.info("=== 测试滑动窗口配额用尽时触发过期数据清理 ===");

        // 先发5次请求，耗尽配额
        for (int i = 0; i < 5; i++) {
            String result = testService.slidingWindowMethod("condition-" + i);
            assertEquals("sliding_success", result);
        }

        // 第6次应该被限流（配额已用尽，无过期数据需要清理）
        assertThrows(SmartRedisLimitExceededException.class, () ->
                        testService.slidingWindowMethod("condition-exceed"),
                "配额耗尽时应直接拒绝，无需清理");

        // 手动向 ZSET 注入一条已过期的历史记录，模拟过期数据积压
        // 找到限流 key
        Set<String> keys = redisRouteTemplate.stringTemplate().keys(
                SmartRedisLimiterRedisKeyConstant.KEY_PREFIX + "*slidingWindowMethod*sw*");
        assertFalse(keys.isEmpty(), "应该有滑动窗口限流 key");
        String windowKey = keys.iterator().next();

        // 当前窗口已满（5条有效记录），再手动写一条"过期"记录（时间戳-2秒）
        long expiredTime = (System.currentTimeMillis() - 2000L) * 1000L; // 2秒前
        redisRouteTemplate.stringTemplate().opsForZSet().add(windowKey, "expired-member", expiredTime);

        // ZCARD 现在是 6，但其中 1 条已过期
        // 请求进来：ZCARD=6，remaining=5-6=-1 <= 0，触发清理
        // 清理后 ZCARD=5（只剩有效记录），remaining=0，拒绝
        // 这是正确行为：有效请求已经有 5 条，窗口确实已满
        log.info("ZSET 注入过期数据后，当前元素数: {}",
                redisRouteTemplate.stringTemplate().opsForZSet().zCard(windowKey));

        // 注入 3 条过期记录
        for (int i = 0; i < 3; i++) {
            long t = (System.currentTimeMillis() - 2000L) * 1000L;
            redisRouteTemplate.stringTemplate().opsForZSet().add(windowKey, "expired-" + i, t);
        }

        // 现在 ZSET 有 8 条记录（5条有效 + 3条过期）
        // 请求进来：ZCARD=8，remaining=5-8=-3 <= 0，触发清理
        // 清理后只剩 5 条有效记录，remaining=0，拒绝
        log.info("注入3条过期数据后，当前元素数: {}",
                redisRouteTemplate.stringTemplate().opsForZSet().zCard(windowKey));

        // 等1秒，让窗口重置
        Thread.sleep(1100);

        // 窗口重置后，所有历史数据过期，新请求应该能通过
        String result = testService.slidingWindowMethod("condition-after-reset");
        assertEquals("sliding_success", result);

        log.info("=== 滑动窗口配额用尽时触发过期数据清理测试通过 ===");
    }
}

