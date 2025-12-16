package io.github.surezzzzzz.sdk.limiter.redis.smart.cases;

import io.github.surezzzzzz.sdk.limiter.redis.smart.SmartRedisLimiterApplication;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterRedisKeyConstant;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import redis.embedded.RedisServer;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author: Sure.
 * @description 智能限流器Web接口测试
 * @Date: 2024/12/XX XX:XX
 */
@Slf4j
@SpringBootTest(classes = SmartRedisLimiterApplication.class)
@AutoConfigureMockMvc
@ContextConfiguration(initializers = SmartLimiterWebTest.RedisInitializer.class)
public class SmartLimiterWebTest {

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
    private MockMvc mockMvc;

    @Autowired
    private RedisTemplate<String, String> smartRedisLimiterRedisTemplate;

    @BeforeEach
    public void setup() throws Exception {
        // 预热：发一个请求确保Redis连接建立，避免第一次请求超时
        try {
            mockMvc.perform(get("/api/health"));
            log.debug("Redis连接预热完成");
        } catch (Exception e) {
            log.warn("Redis连接预热失败: {}", e.getMessage());
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
     * 测试1：路径级别限流（path策略 - 独立限流）
     * 降级策略：fallback=allow
     */
    @Test
    public void testPathStrategyIndependentLimit() throws Exception {
        log.info("=== 测试路径级别独立限流（5次/10秒，降级策略：allow） ===");

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/api/public/test"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("public endpoint"));
        }

        mockMvc.perform(get("/api/public/test"))
                .andDo(print())
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(429));

        log.info("=== 路径级别独立限流测试通过 ===");
    }

    /**
     * 测试2：不同用户ID独立限流（path策略）
     * 降级策略：GET请求 fallback=allow
     */
    @Test
    public void testDifferentUserIdIndependentLimit() throws Exception {
        log.info("=== 测试不同用户ID独立限流（降级策略：allow） ===");

        for (int i = 0; i < 15; i++) {
            mockMvc.perform(get("/api/user/123"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.userId").value(123));
        }

        for (int i = 0; i < 10; i++) {
            mockMvc.perform(get("/api/user/456"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.userId").value(456));
        }

        mockMvc.perform(get("/api/user/123"))
                .andExpect(status().isTooManyRequests());

        mockMvc.perform(get("/api/user/456"))
                .andExpect(status().isTooManyRequests());

        log.info("=== 不同用户ID独立限流测试通过 ===");
    }

    /**
     * 测试3：路径模式共享限流（path-pattern策略）
     * 降级策略：POST请求 fallback=deny
     */
    @Test
    public void testPathPatternSharedLimit() throws Exception {
        log.info("=== 测试路径模式共享限流（POST /api/user/** 共享5次，降级策略：deny） ===");

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/user/111"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("user created"));
        }

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/user/222"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("user created"));
        }

        mockMvc.perform(post("/api/user/333"))
                .andDo(print())
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(429));

        log.info("=== 路径模式共享限流测试通过 ===");
    }

    /**
     * 测试4：精确路径优先级
     * 降级策略：使用拦截器默认 fallback=allow
     */
    @Test
    public void testExactPathPriority() throws Exception {
        log.info("=== 测试精确路径优先级（降级策略：allow） ===");

        for (int i = 0; i < 15; i++) {
            mockMvc.perform(get("/api/user/123"))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(get("/api/user/123"))
                .andDo(print())
                .andExpect(status().isTooManyRequests());

        for (int i = 0; i < 10; i++) {
            mockMvc.perform(get("/api/user/456"))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(get("/api/user/456"))
                .andExpect(status().isTooManyRequests());

        log.info("=== 精确路径优先级测试通过 ===");
    }

    /**
     * 测试5：排除路径不限流
     */
    @Test
    public void testExcludedPath() throws Exception {
        log.info("=== 测试排除路径不限流 ===");

        for (int i = 0; i < 100; i++) {
            mockMvc.perform(get("/api/health"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("UP"));
        }

        log.info("=== 排除路径测试通过（100次请求都成功） ===");
    }

    /**
     * 测试6：验证Redis Key结构
     */
    @Test
    public void testRedisKeyStructure() throws Exception {
        log.info("=== 测试Redis Key结构 ===");

        // 清空之前的key
        Set<String> oldKeys = smartRedisLimiterRedisTemplate.keys(SmartRedisLimiterRedisKeyConstant.KEY_PREFIX + "*");
        if (oldKeys != null && !oldKeys.isEmpty()) {
            smartRedisLimiterRedisTemplate.delete(oldKeys);
            log.info("清空了{}个旧key", oldKeys.size());
        }

        // 1. 触发GET限流
        log.info("触发GET /api/public/test...");
        mockMvc.perform(get("/api/public/test"))
                .andExpect(status().isOk());

        // 立即检查GET的key
        Set<String> keysAfterGet = smartRedisLimiterRedisTemplate.keys(SmartRedisLimiterRedisKeyConstant.KEY_PREFIX + "*");
        log.info("GET请求后的keys: {}", keysAfterGet);

        // 2. 触发POST限流
        log.info("触发POST /api/user/123...");
        mockMvc.perform(post("/api/user/123"))
                .andExpect(status().isOk());

        // 3. 检查所有Redis中的key
        Set<String> keys = smartRedisLimiterRedisTemplate.keys(SmartRedisLimiterRedisKeyConstant.KEY_PREFIX + "*");
        log.info("所有Redis Keys ({}个): {}", keys.size(), keys);

        // 打印每个key的详细信息
        for (String key : keys) {
            String value = smartRedisLimiterRedisTemplate.opsForValue().get(key);
            Long ttl = smartRedisLimiterRedisTemplate.getExpire(key, java.util.concurrent.TimeUnit.SECONDS);
            log.info("Key: {}, Value: {}, TTL: {}s", key, value, ttl);
        }

        // 验证path策略的key (GET /api/public/test)
        boolean hasPathKey = keys.stream()
                .anyMatch(key -> key.contains("path:") && key.contains("/api/public/test"));

        // 验证path-pattern策略的key (POST /api/user/**)
        boolean hasPatternKey = keys.stream()
                .anyMatch(key -> key.contains("path-pattern:") && key.contains("/api/user/**"));

        // 更详细的断言信息
        if (!hasPathKey) {
            log.error("缺少path策略的key，期望包含: path:/api/public/test");
            log.error("实际keys: {}", keys);
        }

        if (!hasPatternKey) {
            log.error("缺少path-pattern策略的key，期望包含: path-pattern:/api/user/**");
            log.error("实际keys: {}", keys);
        }

        assertTrue(hasPathKey, "应该有path策略的key (GET /api/public/test)");
        assertTrue(hasPatternKey, "应该有path-pattern策略的key (POST /api/user/**)");

        log.info("=== Redis Key结构测试通过 ===");
    }

}
