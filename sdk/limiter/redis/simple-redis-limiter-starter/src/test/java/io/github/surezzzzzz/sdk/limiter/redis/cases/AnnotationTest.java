package io.github.surezzzzzz.sdk.limiter.redis.cases;

import io.github.surezzzzzz.sdk.limiter.redis.LimiterApplication;
import io.github.surezzzzzz.sdk.limiter.redis.SimpleRedisLimiter;
import io.github.surezzzzzz.sdk.limiter.redis.annotation.SimpleRedisRateLimiter;
import io.github.surezzzzzz.sdk.limiter.redis.configuration.RedisLimiterProperties;
import io.github.surezzzzzz.sdk.limiter.redis.exception.RateLimitException;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.test.context.ContextConfiguration;
import redis.embedded.RedisServer;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @SimpleRedisRateLimiter 注解功能测试 (Java 8 兼容版本)
 * @author: Sure.
 */
@Slf4j
@SpringBootTest(classes = {LimiterApplication.class, AnnotationTest.TestService.class})
@ContextConfiguration(initializers = AnnotationTest.RedisInitializer.class)
public class AnnotationTest {

    /**
     * Redis 初始化器
     */
    public static class RedisInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        private static RedisServer redisServer;

        @Override
        public void initialize(ConfigurableApplicationContext applicationContext) {
            try {
                int redisPort = findAvailablePort();
                log.info("=== 启动 Embedded Redis，端口: {} ===", redisPort);

                redisServer = new RedisServer(redisPort);
                redisServer.start();

                System.setProperty("spring.redis.host", "localhost");
                System.setProperty("spring.redis.port", String.valueOf(redisPort));
                System.setProperty("io.github.surezzzzzz.sdk.limiter.redis.enable", "true");

                Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
                    @Override
                    public void run() {
                        if (redisServer != null) {
                            redisServer.stop();
                        }
                    }
                }));

            } catch (Exception e) {
                throw new RuntimeException("Failed to start Redis", e);
            }
        }

        private static int findAvailablePort() {
            try {
                java.net.ServerSocket socket = new java.net.ServerSocket(0);
                int port = socket.getLocalPort();
                socket.close();
                return port;
            } catch (Exception e) {
                return 6380 + (int) (Math.random() * 1000);
            }
        }
    }

    /**
     * 测试用的 Service 类
     */
    @Service
    public static class TestService {

        @Autowired
        private SimpleRedisLimiter limiter;

        /**
         * 测试 1：仅消耗令牌（无 key）
         */
        @SimpleRedisRateLimiter(message = "测试限流1")
        public String testTokenOnly() {
            return "success";
        }

        /**
         * 测试 2：令牌 + 去重（固定 key）
         */
        @SimpleRedisRateLimiter(
                key = "test:fixed:key",
                message = "测试限流2"
        )
        public String testFixedKey() {
            return "success";
        }

        /**
         * 测试 3：SpEL 表达式 - 单参数
         */
        @SimpleRedisRateLimiter(
                key = "#userId",
                message = "用户请求重复"
        )
        public String testSpelSingleParam(String userId) {
            return "success:" + userId;
        }

        /**
         * 测试 4：SpEL 表达式 - 对象属性
         */
        @SimpleRedisRateLimiter(
                key = "#request.orderId",
                message = "订单已存在"
        )
        public String testSpelObjectProperty(OrderRequest request) {
            return "success:" + request.getOrderId();
        }

        /**
         * 测试 5：SpEL 表达式 - 组合
         */
        @SimpleRedisRateLimiter(
                key = "'user:' + #userId + ':action:' + #action",
                message = "操作重复"
        )
        public String testSpelCombination(String userId, String action) {
            return "success:" + userId + ":" + action;
        }

        /**
         * 测试 6：哈希存储
         */
        @SimpleRedisRateLimiter(
                key = "#longString",
                useHash = true,
                message = "长字符串限流"
        )
        public String testHashStorage(String longString) {
            return "success:" + longString.length();
        }

        /**
         * 测试 7：RETURN_NULL 策略
         */
        @SimpleRedisRateLimiter(
                key = "#id",
                fallback = SimpleRedisRateLimiter.FallbackStrategy.RETURN_NULL
        )
        public String testReturnNull(String id) {
            return "success:" + id;
        }

        /**
         * 测试 8：CUSTOM 策略
         */
        @SimpleRedisRateLimiter(
                key = "#id",
                fallback = SimpleRedisRateLimiter.FallbackStrategy.CUSTOM
        )
        public String testCustomFallback(String id) {
            return "success:" + id;
        }

        /**
         * 测试 8 的降级方法
         */
        public String testCustomFallbackFallback(String id) {
            return "fallback:" + id;
        }

        /**
         * 测试 9：指定降级方法名
         */
        @SimpleRedisRateLimiter(
                key = "#id",
                fallback = SimpleRedisRateLimiter.FallbackStrategy.CUSTOM,
                fallbackMethod = "customFallbackMethod"
        )
        public String testCustomFallbackWithMethodName(String id) {
            return "success:" + id;
        }

        public String customFallbackMethod(String id) {
            return "custom_fallback:" + id;
        }

        /**
         * 手动重置 Token（测试辅助方法）
         */
        public void resetTokenForTest() {
            limiter.resetToken();
        }
    }

    /**
     * 测试用的请求对象
     */
    @Data
    public static class OrderRequest {
        private String orderId;
        private String userId;
        private Double amount;
    }

    @Autowired
    private TestService testService;

    @Autowired
    private SimpleRedisLimiter limiter;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private RedisLimiterProperties redisLimiterProperties;

    /**
     * 测试 1：仅消耗令牌（无 key）
     */
    @Test
    public void testTokenOnly() {
        log.info("=== 测试 1：仅消耗令牌（无 key） ===");

        // 第一次调用应该成功
        String result1 = testService.testTokenOnly();
        assertEquals("success", result1);
        log.info("第一次调用成功");

        // 第二次调用也应该成功（因为有足够的令牌）
        String result2 = testService.testTokenOnly();
        assertEquals("success", result2);
        log.info("第二次调用成功");
    }

    /**
     * 测试 2：固定 key 去重
     */
    @Test
    public void testFixedKey() {
        log.info("=== 测试 2：固定 key 去重 ===");

        // 第一次调用应该成功
        String result1 = testService.testFixedKey();
        assertEquals("success", result1);
        log.info("第一次调用成功");

        // 第二次调用应该被拦截（因为 key 相同，Set 中已存在）
        try {
            testService.testFixedKey();
            fail("应该抛出 RateLimitException");
        } catch (RateLimitException exception) {
            assertTrue(exception.isDuplicate());
            log.info("第二次调用被拦截: {}", exception.getMessage());
        }
    }

    /**
     * 测试 3：SpEL 单参数
     */
    @Test
    public void testSpelSingleParam() {
        log.info("=== 测试 3：SpEL 单参数 ===");

        // 不同的 userId 应该都能成功
        String result1 = testService.testSpelSingleParam("user123");
        assertEquals("success:user123", result1);
        log.info("用户 user123 调用成功");

        String result2 = testService.testSpelSingleParam("user456");
        assertEquals("success:user456", result2);
        log.info("用户 user456 调用成功");

        // 相同的 userId 第二次调用应该被拦截
        try {
            testService.testSpelSingleParam("user123");
            fail("应该抛出 RateLimitException");
        } catch (RateLimitException exception) {
            assertTrue(exception.isDuplicate());
            log.info("用户 user123 重复调用被拦截");
        }
    }

    /**
     * 测试 4：SpEL 对象属性
     */
    @Test
    public void testSpelObjectProperty() {
        log.info("=== 测试 4：SpEL 对象属性 ===");

        OrderRequest request1 = new OrderRequest();
        request1.setOrderId("order001");
        request1.setUserId("user123");

        String result1 = testService.testSpelObjectProperty(request1);
        assertEquals("success:order001", result1);
        log.info("订单 order001 创建成功");

        // 相同的 orderId 应该被拦截
        OrderRequest request2 = new OrderRequest();
        request2.setOrderId("order001");
        request2.setUserId("user456"); // 不同的 userId

        try {
            testService.testSpelObjectProperty(request2);
            fail("应该抛出 RateLimitException");
        } catch (RateLimitException exception) {
            assertTrue(exception.isDuplicate());
            log.info("订单 order001 重复创建被拦截");
        }

        // 不同的 orderId 应该成功
        OrderRequest request3 = new OrderRequest();
        request3.setOrderId("order002");
        String result3 = testService.testSpelObjectProperty(request3);
        assertEquals("success:order002", result3);
        log.info("订单 order002 创建成功");
    }

    /**
     * 测试 5：SpEL 组合表达式
     */
    @Test
    public void testSpelCombination() {
        log.info("=== 测试 5：SpEL 组合表达式 ===");

        // user123 执行 like 操作
        String result1 = testService.testSpelCombination("user123", "like");
        assertEquals("success:user123:like", result1);
        log.info("user123 执行 like 成功");

        // user123 执行 favorite 操作（不同的 action）
        String result2 = testService.testSpelCombination("user123", "favorite");
        assertEquals("success:user123:favorite", result2);
        log.info("user123 执行 favorite 成功");

        // user456 执行 like 操作（不同的 userId）
        String result3 = testService.testSpelCombination("user456", "like");
        assertEquals("success:user456:like", result3);
        log.info("user456 执行 like 成功");

        // user123 再次执行 like 应该被拦截
        try {
            testService.testSpelCombination("user123", "like");
            fail("应该抛出 RateLimitException");
        } catch (RateLimitException exception) {
            assertTrue(exception.isDuplicate());
            log.info("user123 重复执行 like 被拦截");
        }
    }

    /**
     * 测试 6：哈希存储
     */
    @Test
    public void testHashStorage() {
        log.info("=== 测试 6：哈希存储 ===");

        // Java 8 字符串重复方法
        StringBuilder sb1 = new StringBuilder(100);
        for (int i = 0; i < 100; i++) {
            sb1.append("a");
        }
        String longString1 = sb1.toString();

        String result1 = testService.testHashStorage(longString1);
        assertEquals("success:100", result1);
        log.info("长字符串 1 处理成功");

        // 相同的长字符串应该被拦截
        try {
            testService.testHashStorage(longString1);
            fail("应该抛出 RateLimitException");
        } catch (RateLimitException exception) {
            assertTrue(exception.isDuplicate());
            log.info("长字符串 1 重复处理被拦截");
        }

        // 不同的长字符串应该成功
        StringBuilder sb2 = new StringBuilder(100);
        for (int i = 0; i < 100; i++) {
            sb2.append("b");
        }
        String longString2 = sb2.toString();

        String result2 = testService.testHashStorage(longString2);
        assertEquals("success:100", result2);
        log.info("长字符串 2 处理成功");
    }

    /**
     * 测试 7：RETURN_NULL 策略
     */
    @Test
    public void testReturnNull() {
        log.info("=== 测试 7：RETURN_NULL 策略 ===");

        String result1 = testService.testReturnNull("test001");
        assertEquals("success:test001", result1);
        log.info("第一次调用成功");

        // 第二次调用应该返回 null（而不是抛异常）
        String result2 = testService.testReturnNull("test001");
        assertNull(result2);
        log.info("第二次调用返回 null");
    }

    /**
     * 测试 8：CUSTOM 策略（默认降级方法名）
     */
    @Test
    public void testCustomFallback() {
        log.info("=== 测试 8：CUSTOM 策略（默认降级方法名） ===");

        String result1 = testService.testCustomFallback("test002");
        assertEquals("success:test002", result1);
        log.info("第一次调用成功");

        // 第二次调用应该调用降级方法
        String result2 = testService.testCustomFallback("test002");
        assertEquals("fallback:test002", result2);
        log.info("第二次调用执行降级方法: {}", result2);
    }

    /**
     * 测试 9：CUSTOM 策略（指定降级方法名）
     */
    @Test
    public void testCustomFallbackWithMethodName() {
        log.info("=== 测试 9：CUSTOM 策略（指定降级方法名） ===");

        String result1 = testService.testCustomFallbackWithMethodName("test003");
        assertEquals("success:test003", result1);
        log.info("第一次调用成功");

        // 第二次调用应该调用指定的降级方法
        String result2 = testService.testCustomFallbackWithMethodName("test003");
        assertEquals("custom_fallback:test003", result2);
        log.info("第二次调用执行指定的降级方法: {}", result2);
    }

    /**
     * 测试 10：令牌耗尽场景
     */
    @Test
    public void testTokenExhaustion() throws InterruptedException {
        log.info("=== 测试 10：令牌耗尽场景 ===");

        // 获取当前 Token 数量
        String tokenKey = redisLimiterProperties.getToken().getBucket() + ":" + redisLimiterProperties.getMe();
        String initialTokens = stringRedisTemplate.opsForValue().get(tokenKey);
        log.info("初始 Token 数量: {}", initialTokens);

        // 手动将 Token 数量设置为 2
        stringRedisTemplate.opsForValue().set(tokenKey, "2");
        log.info("手动设置 Token 数量为 2");

        // 第一次调用应该成功（剩余 1 个）
        String result1 = testService.testTokenOnly();
        assertEquals("success", result1);
        log.info("第一次调用成功，剩余 Token: 1");

        // 第二次调用应该成功（剩余 0 个）
        String result2 = testService.testTokenOnly();
        assertEquals("success", result2);
        log.info("第二次调用成功，剩余 Token: 0");

        // 第三次调用应该失败（令牌不足）
        try {
            testService.testTokenOnly();
            fail("应该抛出 RateLimitException");
        } catch (RateLimitException exception) {
            assertTrue(exception.isInsufficientTokens());
            log.info("第三次调用被拦截（令牌不足）: {}", exception.getMessage());
        }

        // 恢复 Token 数量
        testService.resetTokenForTest();
        log.info("Token 数量已恢复");
    }

    /**
     * 测试 11：并发场景
     */
    @Test
    public void testConcurrency() throws InterruptedException {
        log.info("=== 测试 11：并发场景 ===");

        int threadCount = 50;
        final CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        final AtomicInteger successCount = new AtomicInteger(0);
        final AtomicInteger duplicateCount = new AtomicInteger(0);
        final AtomicInteger insufficientCount = new AtomicInteger(0);

        // 所有线程尝试使用相同的 key
        final String testKey = "concurrent-test-key";

        for (int i = 0; i < threadCount; i++) {
            executor.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        testService.testSpelSingleParam(testKey);
                        successCount.incrementAndGet();
                    } catch (RateLimitException e) {
                        if (e.isDuplicate()) {
                            duplicateCount.incrementAndGet();
                        } else if (e.isInsufficientTokens()) {
                            insufficientCount.incrementAndGet();
                        }
                    } finally {
                        latch.countDown();
                    }
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        log.info("并发测试结果 - 成功: {}, 重复: {}, 令牌不足: {}",
                successCount.get(), duplicateCount.get(), insufficientCount.get());

        // 应该只有 1 个成功，其余的都是重复请求
        assertEquals(1, successCount.get(), "应该只有一个请求成功");
        assertTrue(duplicateCount.get() > 0, "应该有重复请求被拦截");
        log.info("并发测试通过");
    }

    /**
     * 测试 12：混合使用（注解 + 原始调用）
     */
    @Test
    public void testMixedUsage() {
        log.info("=== 测试 12：混合使用（注解 + 原始调用） ===");

        // 使用注解方式
        String result1 = testService.testSpelSingleParam("mixed-user");
        assertEquals("success:mixed-user", result1);
        log.info("注解方式调用成功");

        // 使用原始方式检查是否存在
        int checkResult = limiter.getToken("mixed-user", false);
        assertEquals(2, checkResult); // 应该返回 2（已存在）
        log.info("原始方式检查结果: 已存在");

        // 使用原始方式添加新的 key
        int addResult = limiter.getToken("another-mixed-user", false);
        assertEquals(1, addResult); // 应该返回 1（成功）
        log.info("原始方式添加新 key 成功");
    }
}
