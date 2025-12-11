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
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ContextConfiguration;
import redis.embedded.RedisServer;

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
@ContextConfiguration(initializers = SmartLimiterTest.RedisInitializer.class)
public class SmartLimiterTest {

    private static RedisServer redisServer;

    public static class RedisInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        @Override
        public void initialize(ConfigurableApplicationContext applicationContext) {
            int maxRetries = 3;
            Exception lastException = null;

            for (int attempt = 1; attempt <= maxRetries; attempt++) {
                try {
                    int redisPort = findAvailablePort();
                    log.info("=== 尝试启动 Embedded Redis (第{}次)，端口: {} ===", attempt, redisPort);

                    redisServer = RedisServer.builder()
                            .port(redisPort)
                            .setting("maxheap 128mb")
                            .setting("bind 127.0.0.1")
                            .build();

                    redisServer.start();
                    log.info("Embedded Redis 启动成功");

                    System.setProperty("spring.redis.host", "localhost");
                    System.setProperty("spring.redis.port", String.valueOf(redisPort));
                    System.setProperty("io.github.surezzzzzz.sdk.limiter.redis.smart.enable", "true");

                    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                        if (redisServer != null && redisServer.isActive()) {
                            try {
                                log.info("=== 关闭 Embedded Redis ===");
                                redisServer.stop();
                                log.info("Embedded Redis 关闭成功");
                            } catch (Exception e) {
                                log.warn("Redis 关闭时出现异常（可忽略）", e);
                            }
                        }
                    }));

                    return;

                } catch (Exception e) {
                    lastException = e;
                    log.warn("启动 Embedded Redis 失败 (第{}次): {}", attempt, e.getMessage());

                    if (redisServer != null) {
                        try {
                            redisServer.stop();
                        } catch (Exception ignored) {
                        }
                        redisServer = null;
                    }

                    if (attempt < maxRetries) {
                        try {
                            log.info("等待2秒后重试...");
                            Thread.sleep(2000);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }
            }

            log.error("启动 Embedded Redis 失败，已重试{}次", maxRetries);
            throw new RuntimeException("无法启动 Embedded Redis", lastException);
        }

        private static int findAvailablePort() {
            try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
                return socket.getLocalPort();
            } catch (Exception e) {
                return 6380 + (int) (Math.random() * 1000);
            }
        }
    }

    @Autowired
    private TestService testService;

    @Autowired
    @Qualifier("smartRedisLimiterRedisTemplate")
    private RedisTemplate<String, String> smartRedisLimiterRedisTemplate;

    @BeforeEach
    public void setup() throws Exception {
        log.info("=== 测试前准备 ===");

        // 确保Redis启动
        if (redisServer != null && !redisServer.isActive()) {
            startRedisAndInitConnection();
        }

        // 清理数据
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

        boolean has60sKey = multiWindowKeys.stream().anyMatch(k -> k.contains(":60s"));
        assertTrue(has60sKey, "应该有60秒窗口的key");

        for (String key : multiWindowKeys) {
            String value = smartRedisLimiterRedisTemplate.opsForValue().get(key);
            Long ttl = smartRedisLimiterRedisTemplate.getExpire(key, TimeUnit.SECONDS);
            log.info("Key: {}, 剩余token: {}, TTL: {}s", key, value, ttl);
        }

        log.info("=== 多时间窗口限流测试通过 ===");
    }

    /**
     * ✅ 测试3：查询方法（fallback=allow）- Redis异常时放行
     */
//    @Test
//    public void testQueryMethodFallbackAllow() throws Exception {
//        log.info("=== 测试查询方法降级策略ALLOW ===");
//
//        // 停止Redis
//        stopRedisAndCloseConnection();
//
//        // Redis异常时应该全部放行
//        for (int i = 0; i < 20; i++) {
//            String result = testService.queryMethod("fallback-" + i);
//            assertEquals("query_success", result);
//        }
//
//        log.info("=== 查询方法降级ALLOW测试通过 ===");
//    }

    /**
     * ✅ 测试4：创建订单方法（fallback=deny）- Redis异常时拒绝
     */
//    @Test
//    public void testCreateOrderFallbackDeny() throws Exception {
//        log.info("=== 测试创建订单降级策略DENY ===");
//
//        // 停止Redis
//        stopRedisAndCloseConnection();
//
//        // Redis异常时应该全部拒绝
//        assertThrows(SmartRedisLimitExceededException.class, () -> {
//            testService.createOrder("fallback-order");
//        });
//
//        log.info("=== 创建订单降级DENY测试通过 ===");
//    }

    /**
     * ✅ 测试5：支付方法（fallback=deny）- Redis异常时拒绝
     */
//    @Test
//    public void testPaymentFallbackDeny() throws Exception {
//        log.info("=== 测试支付方法降级策略DENY ===");
//
//        // 停止Redis
//        stopRedisAndCloseConnection();
//
//        // Redis异常时应该全部拒绝
//        assertThrows(SmartRedisLimitExceededException.class, () -> {
//            testService.payment("payment-1");
//        });
//
//        log.info("=== 支付方法降级DENY测试通过 ===");
//    }

    /**
     * ✅ 测试6：使用注解模式默认降级策略（allow）
     */
//    @Test
//    public void testDefaultFallbackAllow() throws Exception {
//        log.info("=== 测试注解模式默认降级策略 ===");
//
//        // 停止Redis
//        stopRedisAndCloseConnection();
//
//        // 没有设置fallback，应该使用annotation.default-fallback=allow
//        for (int i = 0; i < 20; i++) {
//            String result = testService.defaultFallbackMethod("default-" + i);
//            assertEquals("default_success", result);
//        }
//
//        log.info("=== 注解模式默认降级测试通过 ===");
//    }

    /**
     * 停止Redis并关闭连接
     */
    private void stopRedisAndCloseConnection() throws Exception {
        log.info("停止Redis并关闭连接...");

        if (redisServer != null && redisServer.isActive()) {
            redisServer.stop();
        }

        LettuceConnectionFactory factory = (LettuceConnectionFactory) smartRedisLimiterRedisTemplate.getConnectionFactory();
        if (factory != null) {
            factory.resetConnection();
        }

        Thread.sleep(3500);
    }

    /**
     * 启动Redis并重新初始化连接
     */
    private void startRedisAndInitConnection() throws Exception {
        log.info("启动Redis并重新初始化连接...");

        if (redisServer != null && !redisServer.isActive()) {
            redisServer.start();
        }

        LettuceConnectionFactory factory = (LettuceConnectionFactory) smartRedisLimiterRedisTemplate.getConnectionFactory();
        if (factory != null) {
            factory.afterPropertiesSet();
        }

        Thread.sleep(2000);
    }
}
