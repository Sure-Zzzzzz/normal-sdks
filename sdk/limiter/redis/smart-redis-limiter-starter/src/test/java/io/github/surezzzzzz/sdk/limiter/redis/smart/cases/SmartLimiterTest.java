package io.github.surezzzzzz.sdk.limiter.redis.smart.cases;

import io.github.surezzzzzz.sdk.limiter.redis.smart.SmartRedisLimiterApplication;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterRedisKeyConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.exception.SmartRedisLimitExceededException;
import io.github.surezzzzzz.sdk.limiter.redis.smart.service.TestService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
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

    /**
     * Redis 初始化器
     */
    public static class RedisInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        private static RedisServer redisServer;

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
                        if (redisServer != null) {
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
    private RedisTemplate<String, String> smartRedisLimiterRedisTemplate;

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
        log.info("=== 开始并发限流测试（1秒10次） ===");

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
        log.info("=== 开始多时间窗口限流测试 ===");

        // 第一秒：10次请求
        for (int i = 0; i < 10; i++) {
            String result = testService.multiWindowMethod("first-batch-" + i);
            assertEquals("success", result);
        }

        // 第11次应该被1秒窗口限流
        assertThrows(SmartRedisLimitExceededException.class, () -> {
            testService.multiWindowMethod("exceed-1s");
        });

        log.info("第一秒限流验证通过，等待1.2秒窗口重置...");
        Thread.sleep(1200);

        // 第二秒：又可以10次
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
}
