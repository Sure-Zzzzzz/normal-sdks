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
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;

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
@SpringBootTest(classes = SmartRedisLimiterApplication.class)
public class SmartLimiterTest {

    private RedisConnectionFactory originalFactory;

    @Autowired
    private TestService testService;

    @Autowired
    @Qualifier("smartRedisLimiterRedisTemplate")
    private RedisTemplate<String, String> smartRedisLimiterRedisTemplate;

    @BeforeEach
    public void setup() throws Exception {
        log.info("=== 测试前准备 ===");

        // 清理数据
        Set<String> keys = smartRedisLimiterRedisTemplate.keys(SmartRedisLimiterRedisKeyConstant.KEY_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            smartRedisLimiterRedisTemplate.delete(keys);
            log.info("清理了 {} 个限流key", keys.size());
        }
    }

    @AfterEach
    public void cleanup() {
        // 确保每个测试后都恢复正常连接
        if (originalFactory != null) {
            smartRedisLimiterRedisTemplate.setConnectionFactory(originalFactory);
            originalFactory = null;
        }
        Set<String> keys = smartRedisLimiterRedisTemplate.keys(SmartRedisLimiterRedisKeyConstant.KEY_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            smartRedisLimiterRedisTemplate.delete(keys);
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

        Set<String> keys = smartRedisLimiterRedisTemplate.keys(SmartRedisLimiterRedisKeyConstant.KEY_PREFIX + "*");
        log.info("查询到的keys数量: {}", keys == null ? "null" : keys.size());
        log.info("查询到的keys内容: {}", keys);

        assertNotNull(keys);
        assertFalse(keys.isEmpty(), "应该有限流key");

        Set<String> multiWindowKeys = keys.stream()
                .filter(k -> k.contains("multiWindowMethod"))
                .collect(Collectors.toSet());

        log.info("multiWindowMethod相关的keys: {}", multiWindowKeys);
        assertFalse(multiWindowKeys.isEmpty(), "应该有multiWindowMethod的限流key");

        boolean has1sKey = multiWindowKeys.stream().anyMatch(k -> k.contains(":1s"));
        boolean has60sKey = multiWindowKeys.stream().anyMatch(k -> k.contains(":60s"));
        assertTrue(has1sKey, "应该有1秒窗口的key");
        assertTrue(has60sKey, "应该有60秒窗口的key");

        for (String key : multiWindowKeys) {
            String value = smartRedisLimiterRedisTemplate.opsForValue().get(key);
            Long ttl = smartRedisLimiterRedisTemplate.getExpire(key, TimeUnit.SECONDS);
            log.info("Key: {}, 剩余token: {}, TTL: {}s", key, value, ttl);
        }

        log.info("=== 多时间窗口限流测试通过 ===");
    }

    /**
     * 测试3：查询方法（fallback=allow）- Redis异常时放行
     */
    @Test
    public void testQueryMethodFallbackAllow() throws Exception {
        log.info("=== 测试查询方法降级策略ALLOW ===");

        stopRedisAndCloseConnection();

        for (int i = 0; i < 20; i++) {
            String result = testService.queryMethod("fallback-" + i);
            assertEquals("query_success", result);
        }

        log.info("=== 查询方法降级ALLOW测试通过 ===");
    }

    /**
     * 测试4：创建订单方法（fallback=deny）- Redis异常时拒绝
     */
    @Test
    public void testCreateOrderFallbackDeny() throws Exception {
        log.info("=== 测试创建订单降级策略DENY ===");

        stopRedisAndCloseConnection();

        assertThrows(SmartRedisLimitExceededException.class, () ->
                testService.createOrder("fallback-order"));

        log.info("=== 创建订单降级DENY测试通过 ===");
    }

    /**
     * 测试5：支付方法（fallback=deny）- Redis异常时拒绝
     */
    @Test
    public void testPaymentFallbackDeny() throws Exception {
        log.info("=== 测试支付方法降级策略DENY ===");

        stopRedisAndCloseConnection();

        assertThrows(SmartRedisLimitExceededException.class, () ->
                testService.payment("payment-1"));

        log.info("=== 支付方法降级DENY测试通过 ===");
    }

    /**
     * 测试6：注解模式默认降级策略（allow）- Redis异常时放行
     */
    @Test
    public void testDefaultFallbackAllow() throws Exception {
        log.info("=== 测试注解模式默认降级策略 ===");

        stopRedisAndCloseConnection();

        for (int i = 0; i < 20; i++) {
            String result = testService.defaultFallbackMethod("default-" + i);
            assertEquals("default_success", result);
        }

        log.info("=== 注解模式默认降级测试通过 ===");
    }

    private void stopRedisAndCloseConnection() throws Exception {
        log.info("模拟Redis不可用：切换到不存在的端口 16379...");
        originalFactory = smartRedisLimiterRedisTemplate.getConnectionFactory();
        LettuceConnectionFactory brokenFactory = new LettuceConnectionFactory("localhost", 16379);
        brokenFactory.afterPropertiesSet();
        smartRedisLimiterRedisTemplate.setConnectionFactory(brokenFactory);
        Thread.sleep(200);
    }
}
