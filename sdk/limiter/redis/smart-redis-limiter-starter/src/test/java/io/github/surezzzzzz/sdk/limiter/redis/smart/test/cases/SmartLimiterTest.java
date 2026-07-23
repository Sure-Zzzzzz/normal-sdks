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
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author: Sure.
 * @description 智能限流器注解模式测试
 * @Date: 2024/12/XX XX:XX
 */
@Slf4j
@SpringBootTest(classes = SmartRedisLimiterTestApplication.class)
public class SmartLimiterTest {

    @Autowired
    private TestService testService;

    @Autowired
    private RedisRouteTemplate redisRouteTemplate;

    @BeforeEach
    public void setup() throws Exception {
        log.info("=== 测试前准备 ===");

        // 清理数据
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
     * 测试1：并发限流（1秒10次）
     */
    @Test
    public void testConcurrentRateLimiter() throws Exception {
        log.info("=== 开始并发限流测试（1秒10次，注解模式默认降级：allow） ===");

        int limit = 10;
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
                    testService.limitedMethod("task-" + taskId);
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

        log.info("=== 并发限流测试通过 ===");
    }

    /**
     * 测试2：多时间窗口限流（1秒10次 + 1分钟100次）
     */
    @Test
    public void testMultiWindowRateLimiter() throws Exception {
        log.info("=== 开始多时间窗口限流测试（注解模式默认降级：allow） ===");

        for (int i = 0; i < 10; i++) {
            String result = testService.multiWindowMethod("first-batch-" + i);
            assertEquals("success", result);
        }

        assertThrows(SmartRedisLimitExceededException.class, () -> {
            testService.multiWindowMethod("exceed-1s");
        });

        log.info("第一秒限流验证通过，等待1.2秒窗口重置...");
        Thread.sleep(1200);

        for (int i = 0; i < 10; i++) {
            String result = testService.multiWindowMethod("second-batch-" + i);
            assertEquals("success", result);
        }

        log.info("已调用20次，检查Redis keys...");

        try {
            testService.multiWindowMethod("trigger-for-check");
        } catch (SmartRedisLimitExceededException ignored) {
            log.info("触发请求被限流（预期）");
        }

        Set<String> keys = redisRouteTemplate.stringTemplate().keys(SmartRedisLimiterRedisKeyConstant.KEY_PREFIX + "*");
        log.info("查询到的keys数量: {}", keys == null ? "null" : keys.size());
        log.info("查询到的keys内容: {}", keys);

        assertNotNull(keys);
        assertFalse(keys.isEmpty(), "应该有限流key");

        Set<String> multiWindowKeys = keys.stream()
                .filter(k -> k.contains("multiWindowMethod"))
                .collect(Collectors.toSet());

        log.info("multiWindowMethod相关的keys: {}", multiWindowKeys);
        assertFalse(multiWindowKeys.isEmpty(), "应该有multiWindowMethod的限流key");

        boolean has1sKey = multiWindowKeys.stream().anyMatch(k -> k.contains(":fw2:1s"));
        boolean has60sKey = multiWindowKeys.stream().anyMatch(k -> k.contains(":fw2:60s"));
        assertTrue(has1sKey, "应该有1秒窗口的key");
        assertTrue(has60sKey, "应该有60秒窗口的key");

        for (String key : multiWindowKeys) {
            String value = redisRouteTemplate.stringTemplate().opsForValue().get(key);
            Long ttl = redisRouteTemplate.stringTemplate().getExpire(key, TimeUnit.SECONDS);
            log.info("Key: {}, 剩余token: {}, TTL: {}s", key, value, ttl);
        }

        log.info("=== 多时间窗口限流测试通过 ===");
    }
}
