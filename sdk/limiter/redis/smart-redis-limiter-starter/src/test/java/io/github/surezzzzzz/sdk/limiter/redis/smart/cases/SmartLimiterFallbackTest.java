package io.github.surezzzzzz.sdk.limiter.redis.smart.cases;

import io.github.surezzzzzz.sdk.limiter.redis.smart.SmartRedisLimiterApplication;
import io.github.surezzzzzz.sdk.limiter.redis.smart.configuration.SmartRedisLimiterProperties;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterFallbackStrategy;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterRedisKeyConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.service.TestService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import redis.embedded.RedisServer;

import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author: Sure.
 * @description 降级策略测试
 * @Date: 2024/12/XX XX:XX
 */
@Slf4j
@SpringBootTest(classes = SmartRedisLimiterApplication.class)
@AutoConfigureMockMvc
@ContextConfiguration(initializers = SmartLimiterFallbackTest.RedisInitializer.class)
public class SmartLimiterFallbackTest {

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
    private MockMvc mockMvc;

    @Autowired
    private TestService testService;

    @Autowired
    private SmartRedisLimiterProperties properties;

    @Autowired
    @Qualifier("smartRedisLimiterRedisTemplate")
    private RedisTemplate<String, String> smartRedisLimiterRedisTemplate;

    @AfterEach
    public void cleanup() {
        // 清理Redis数据
        Set<String> keys = smartRedisLimiterRedisTemplate.keys(SmartRedisLimiterRedisKeyConstant.KEY_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            smartRedisLimiterRedisTemplate.delete(keys);
            log.info("清理了 {} 个限流key", keys.size());
        }

        // 确保Redis启动
        if (redisServer != null && !redisServer.isActive()) {
            try {
                log.info("恢复Redis...");
                redisServer.start();
                Thread.sleep(2000);
            } catch (Exception e) {
                log.error("恢复Redis失败", e);
            }
        }

        // 恢复降级策略为配置文件的值（deny）
        properties.getFallback().setOnRedisError(SmartRedisLimiterFallbackStrategy.DENY.getCode());
    }

    private void stopRedisAndCloseConnection() throws Exception {
        log.info("停止Redis并关闭连接...");

        // 1. 停止Redis
        if (redisServer != null && redisServer.isActive()) {
            redisServer.stop();
        }

        // 2. 关闭RedisTemplate的连接，阻止重连
        RedisConnectionFactory factory = smartRedisLimiterRedisTemplate.getConnectionFactory();
        if (factory instanceof LettuceConnectionFactory) {
            LettuceConnectionFactory lettuceFactory = (LettuceConnectionFactory) factory;
            // ✅ 重置连接，停止重连
            lettuceFactory.resetConnection();
        }

        Thread.sleep(1500);
    }
//
//    /**
//     * 测试1：降级策略 ALLOW（Redis异常时放行）
//     */
//    @Test
//    public void testFallbackStrategyAllow() throws Exception {
//        log.info("=== 测试降级策略 ALLOW（Redis异常时放行） ===");
//
//        // 1. 修改降级策略为ALLOW
//        properties.getFallback().setOnRedisError(SmartRedisLimiterFallbackStrategy.ALLOW.getCode());
//
//        // 2. 验证Redis正常时限流生效
//        log.info("验证Redis正常时限流生效...");
//        for (int i = 0; i < 5; i++) {
//            mockMvc.perform(get("/api/public/test"))
//                    .andExpect(status().isOk());
//        }
//
//        // 第6次被限流
//        mockMvc.perform(get("/api/public/test"))
//                .andExpect(status().isTooManyRequests());
//
//        // 3. 停止Redis模拟故障
//        log.info("停止Redis模拟故障...");
//        stopRedisAndCloseConnection();
//        Thread.sleep(1000);
//
//        // 4. 验证Redis异常时自动放行
//        log.info("验证Redis异常时自动放行...");
//        for (int i = 0; i < 5; i++) {
//            mockMvc.perform(get("/api/public/test"))
//                    .andExpect(status().isOk()); // ✅ 全部成功
//        }
//
//        log.info("=== 降级策略 ALLOW 测试通过 ===");
//
//    }
//
//    /**
//     * 测试2：降级策略 DENY（Redis异常时拒绝）
//     */
//    @Test
//    public void testFallbackStrategyDeny() throws Exception {
//        log.info("=== 测试降级策略 DENY（Redis异常时拒绝） ===");
//
//        // 1. 保持降级策略为DENY（配置文件默认值）
//        properties.getFallback().setOnRedisError(SmartRedisLimiterFallbackStrategy.DENY.getCode());
//
//
//        // 2. 验证Redis正常时限流生效
//        log.info("验证Redis正常时限流生效...");
//        mockMvc.perform(get("/api/public/test"))
//                .andExpect(status().isOk());
//
//        // 3. 停止Redis模拟故障
//        log.info("停止Redis模拟故障...");
//        stopRedisAndCloseConnection();
//        Thread.sleep(1000);
//
//        // 4. 验证Redis异常时全部拒绝
//        log.info("验证Redis异常时全部拒绝...");
//        mockMvc.perform(get("/api/public/test"))
//                .andExpect(status().isTooManyRequests()); // ✅ 被拒绝
//
//        log.info("=== 降级策略 DENY 测试通过 ===");
//
//
//    }
//
//    /**
//     * 测试3：注解模式降级策略 ALLOW
//     */
//    @Test
//    public void testAnnotationFallbackAllow() throws Exception {
//        log.info("=== 测试注解模式降级策略 ALLOW ===");
//
//        properties.getFallback().setOnRedisError(SmartRedisLimiterFallbackStrategy.ALLOW.getCode());
//
//
//        // Redis正常时
//        String result = testService.limitedMethod("test");
//        assertEquals("success", result);
//
//        // 停止Redis
//        log.info("停止Redis...");
//        stopRedisAndCloseConnection();
//        Thread.sleep(1000);
//
//        // Redis异常时应该放行
//        for (int i = 0; i < 20; i++) {
//            String fallbackResult = testService.limitedMethod("test-fallback-" + i);
//            assertEquals("success", fallbackResult); // ✅ 全部成功
//        }
//
//        log.info("=== 注解模式降级策略 ALLOW 测试通过 ===");
//
//
//    }
//
//    /**
//     * 测试4：注解模式降级策略 DENY
//     */
//    @Test
//    public void testAnnotationFallbackDeny() throws Exception {
//        log.info("=== 测试注解模式降级策略 DENY ===");
//
//        properties.getFallback().setOnRedisError(SmartRedisLimiterFallbackStrategy.DENY.getCode());
//
//
//        // Redis正常时
//        String result = testService.limitedMethod("test");
//        assertEquals("success", result);
//
//        // 停止Redis
//        log.info("停止Redis...");
//        stopRedisAndCloseConnection();
//        Thread.sleep(1000);
//
//        // Redis异常时应该拒绝
//        assertThrows(SmartRedisLimitExceededException.class, () -> {
//            testService.limitedMethod("test-fallback");
//        });
//
//        log.info("=== 注解模式降级策略 DENY 测试通过 ===");
//
//
//    }

    /**
     * 测试5：Redis恢复后限流恢复正常
     */
    @Test
    public void testRedisRecovery() throws Exception {
        log.info("=== 测试Redis恢复后限流恢复正常 ===");

        properties.getFallback().setOnRedisError(SmartRedisLimiterFallbackStrategy.ALLOW.getCode());

        // 1. 停止Redis
        log.info("停止Redis...");
        redisServer.stop();
        Thread.sleep(3000);

        // 2. 降级期间请求成功
        mockMvc.perform(get("/api/public/test"))
                .andExpect(status().isOk());

        // 3. 重启Redis
        log.info("重启Redis...");
        redisServer.start();
        Thread.sleep(2000);

        // 4. 清理之前的key，确保从0开始
        Set<String> keys = smartRedisLimiterRedisTemplate.keys(SmartRedisLimiterRedisKeyConstant.KEY_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            smartRedisLimiterRedisTemplate.delete(keys);
        }

        // 5. 验证限流恢复正常（5次限流）
        log.info("验证限流恢复...");
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/api/public/test"))
                    .andExpect(status().isOk());
        }

        // 第6次应该被限流
        mockMvc.perform(get("/api/public/test"))
                .andExpect(status().isTooManyRequests());

        log.info("=== Redis恢复后限流恢复正常测试通过 ===");

    }
}