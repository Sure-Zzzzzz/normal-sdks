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
 * 固定窗口限流算法测试
 *
 * <p>测试场景：
 * <ul>
 *   <li>基本限流：5次/秒，第6次触发限流</li>
 *   <li>窗口重置：窗口过期后计数器归零</li>
 *   <li>并发限流：20并发，精确限制5次</li>
 *   <li>Key结构：固定窗口Key包含秒后缀（不含sw）</li>
 *   <li>边界突刺：固定窗口在窗口边界处可通过2倍流量（与滑动窗口的核心区别）</li>
 * </ul>
 *
 * @author Sure.
 * @Date: 2026-05-09
 */
@Slf4j
@SpringBootTest(classes = SmartRedisLimiterApplication.class)
public class SmartRedisLimiterFixedWindowTest {

    @Autowired
    private TestService testService;

    @Autowired
    @Qualifier("smartRedisLimiterRedisTemplate")
    private RedisTemplate<String, String> smartRedisLimiterRedisTemplate;

    @BeforeEach
    public void setup() {
        log.info("=== 测试前准备：清理Redis ===");
        cleanRedis();
    }

    @AfterEach
    public void cleanup() {
        cleanRedis();
    }

    private void cleanRedis() {
        Set<String> keys = smartRedisLimiterRedisTemplate.keys(SmartRedisLimiterRedisKeyConstant.KEY_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            smartRedisLimiterRedisTemplate.delete(keys);
            log.info("清理了 {} 个限流key", keys.size());
        }
    }

    /**
     * 测试1：固定窗口基本限流（1秒5次）
     */
    @Test
    public void testFixedWindowBasic() {
        log.info("=== 测试固定窗口基本限流（1秒5次） ===");

        for (int i = 0; i < 5; i++) {
            String result = testService.fixedWindowMethod("test-" + i);
            assertEquals("fixed_success", result);
        }

        assertThrows(SmartRedisLimitExceededException.class, () ->
                testService.fixedWindowMethod("exceed"));

        log.info("=== 固定窗口基本限流测试通过 ===");
    }

    /**
     * 测试2：固定窗口精度测试（窗口重置）
     */
    @Test
    public void testFixedWindowReset() throws Exception {
        log.info("=== 测试固定窗口精度（窗口重置） ===");

        for (int i = 0; i < 5; i++) {
            testService.fixedWindowMethod("fast-" + i);
        }

        // 确认窗口已满
        assertThrows(SmartRedisLimitExceededException.class, () ->
                testService.fixedWindowMethod("fast-exceed"));

        // 等待窗口过期
        Thread.sleep(1100);

        // 窗口重置，应该又能请求
        String result = testService.fixedWindowMethod("after-window");
        assertEquals("fixed_success", result);

        log.info("=== 固定窗口精度测试通过 ===");
    }

    /**
     * 测试3：固定窗口并发限流（20并发，5次限制）
     */
    @Test
    public void testFixedWindowConcurrent() throws Exception {
        log.info("=== 测试固定窗口并发限流（20并发，5次限制） ===");

        int limit = 5;
        int concurrentRequests = 20;
        ExecutorService executorService = Executors.newFixedThreadPool(concurrentRequests);
        CountDownLatch latch = new CountDownLatch(concurrentRequests);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < concurrentRequests; i++) {
            int taskId = i;
            executorService.submit(() -> {
                try {
                    testService.fixedWindowMethod("concurrent-" + taskId);
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

        log.info("=== 固定窗口并发限流测试通过 ===");
    }

    /**
     * 测试4：固定窗口Redis Key结构验证
     */
    @Test
    public void testFixedWindowKeyStructure() {
        log.info("=== 测试固定窗口Redis Key结构 ===");

        testService.fixedWindowMethod("key-test");

        Set<String> keys = smartRedisLimiterRedisTemplate.keys(SmartRedisLimiterRedisKeyConstant.KEY_PREFIX + "*");
        log.info("固定窗口限流后的keys: {}", keys);

        assertNotNull(keys);
        assertFalse(keys.isEmpty(), "应该有限流key");

        // 固定窗口key包含秒后缀，但不包含滑动窗口后缀
        boolean hasFixedWindowKey = keys.stream()
                .anyMatch(k -> k.contains(SmartRedisLimiterRedisKeyConstant.SUFFIX_SECONDS)
                        && !k.contains(SmartRedisLimiterRedisKeyConstant.SUFFIX_SLIDING_WINDOW));
        assertTrue(hasFixedWindowKey,
                "固定窗口key应包含秒后缀（不含sw），实际keys: " + keys);

        log.info("=== 固定窗口Redis Key结构测试通过 ===");
    }

    /**
     * 测试5：固定窗口边界突刺问题
     *
     * <p>固定窗口的缺陷：窗口按整点重置，在窗口边界处可以在极短时间内通过 2 倍限流阈值的请求。
     *
     * <p>场景：限流 5次/秒
     * <ul>
     *   <li>第一批：发 5 次（通过），窗口已满</li>
     *   <li>等待窗口重置（1.1秒）</li>
     *   <li>第二批：再发 5 次（通过），新窗口配额重置</li>
     *   <li>结果：1.1秒内共通过 10 次请求，是限流阈值的 2 倍</li>
     * </ul>
     *
     * <p>如果需要严格防止突刺，应使用滑动窗口算法（见 {@link SmartRedisLimiterSlidingWindowTest#testSlidingWindowNoBurstAtBoundary()}）。
     */
    @Test
    public void testFixedWindowBurstAtBoundary() throws Exception {
        log.info("=== 测试固定窗口边界突刺问题 ===");

        // 第一批：耗尽当前窗口配额
        for (int i = 0; i < 5; i++) {
            String result = testService.fixedWindowMethod("batch1-" + i);
            assertEquals("fixed_success", result);
        }
        assertThrows(SmartRedisLimitExceededException.class, () ->
                testService.fixedWindowMethod("batch1-exceed"),
                "当前窗口已满，应该被限流");

        // 等待窗口重置
        Thread.sleep(1100);

        // 第二批：新窗口开始，配额重置，可以再发 5 次
        for (int i = 0; i < 5; i++) {
            String result = testService.fixedWindowMethod("batch2-" + i);
            assertEquals("fixed_success", result);
        }

        // 结论：1.1秒内共通过了10次请求（2倍限流阈值），这就是固定窗口的突刺问题
        log.info("=== 固定窗口边界突刺：1.1秒内通过了10次请求（限流阈值为5次/秒） ===");
        log.info("=== 如需防止突刺，请使用滑动窗口算法 ===");
    }
}
