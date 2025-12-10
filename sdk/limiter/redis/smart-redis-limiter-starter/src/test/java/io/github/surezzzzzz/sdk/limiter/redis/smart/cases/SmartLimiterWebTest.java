package io.github.surezzzzzz.sdk.limiter.redis.smart.cases;

import io.github.surezzzzzz.sdk.limiter.redis.smart.SmartRedisLimiterApplication;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterRedisKeyConstant;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
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
     */
    @Test
    public void testPathStrategyIndependentLimit() throws Exception {
        log.info("=== 测试路径级别独立限流（5次/10秒） ===");

        // /api/public/test 前5次成功
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/api/public/test"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("public endpoint"));
        }

        // 第6次被限流
        mockMvc.perform(get("/api/public/test"))
                .andDo(print())
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(429));

        log.info("=== 路径级别独立限流测试通过 ===");
    }

    /**
     * 测试2：不同用户ID独立限流（path策略）
     */
    @Test
    public void testDifferentUserIdIndependentLimit() throws Exception {
        log.info("=== 测试不同用户ID独立限流 ===");

        // GET /api/user/123 限流15次
        for (int i = 0; i < 15; i++) {
            mockMvc.perform(get("/api/user/123"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.userId").value(123));
        }

        // GET /api/user/456 也有独立的10次限额
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(get("/api/user/456"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.userId").value(456));
        }

        // user/123 第16次被限流
        mockMvc.perform(get("/api/user/123"))
                .andExpect(status().isTooManyRequests());

        // user/456 第11次也被限流
        mockMvc.perform(get("/api/user/456"))
                .andExpect(status().isTooManyRequests());

        log.info("=== 不同用户ID独立限流测试通过 ===");
    }

    /**
     * 测试3：路径模式共享限流（path-pattern策略）
     */
    @Test
    public void testPathPatternSharedLimit() throws Exception {
        log.info("=== 测试路径模式共享限流（POST /api/user/** 共享5次） ===");

        // POST /api/user/111
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/user/111"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("user created"));
        }

        // POST /api/user/222
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/user/222"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("user created"));
        }

        // 已经调用了5次（2 + 3），第6次任何POST /api/user/** 都会被限流
        mockMvc.perform(post("/api/user/333"))
                .andDo(print())
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(429));

        log.info("=== 路径模式共享限流测试通过 ===");
    }

    /**
     * 测试4：精确路径优先级
     */
    @Test
    public void testExactPathPriority() throws Exception {
        log.info("=== 测试精确路径优先级 ===");

        // /api/user/123 有精确规则：15次/10秒
        for (int i = 0; i < 15; i++) {
            mockMvc.perform(get("/api/user/123"))
                    .andExpect(status().isOk());
        }

        // 第16次被限流（精确规则只允许15次）
        mockMvc.perform(get("/api/user/123"))
                .andDo(print())
                .andExpect(status().isTooManyRequests());

        // /api/user/456 走通配符规则：10次/10秒
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

        // 健康检查接口默认排除，不限流
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

        // 触发限流
        mockMvc.perform(get("/api/public/test"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/user/123"))
                .andExpect(status().isOk());

        // 检查Redis中的key
        Set<String> keys = smartRedisLimiterRedisTemplate.keys(SmartRedisLimiterRedisKeyConstant.KEY_PREFIX + "*");
        log.info("Redis Keys: {}", keys);

        // 验证path策略的key
        boolean hasPathKey = keys.stream()
                .anyMatch(key -> key.contains("path:/api/public/test"));

        // 验证path-pattern策略的key
        boolean hasPatternKey = keys.stream()
                .anyMatch(key -> key.contains("path-pattern:/api/user/**"));

        if (!hasPathKey || !hasPatternKey) {
            throw new AssertionError("Redis Key结构不正确，实际keys: " + keys);
        }

        log.info("=== Redis Key结构测试通过 ===");
    }
}
