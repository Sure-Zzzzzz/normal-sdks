package io.github.surezzzzzz.sdk.limiter.redis.smart.test.cases;

import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterRedisKeyConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.test.SmartRedisLimiterTestApplication;
import io.github.surezzzzzz.sdk.limiter.redis.smart.test.support.TestEventListener;
import io.github.surezzzzzz.sdk.redis.route.template.RedisRouteTemplate;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 1.1.4 自定义 KeyProvider 集成测试
 * <p>覆盖：</p>
 * <ul>
 *   <li>provider 返回非空 → 不同 key 独立限流</li>
 *   <li>provider 返回 null → 回退到 keyStrategy=path</li>
 *   <li>provider 抛异常 + fallback=allow → 放行</li>
 *   <li>provider 抛异常 + fallback=deny → 429</li>
 * </ul>
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SmartRedisLimiterTestApplication.class)
@AutoConfigureMockMvc
public class SmartRedisLimiterKeyProviderTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RedisRouteTemplate redisRouteTemplate;

    @Autowired
    private TestEventListener testEventListener;

    @BeforeEach
    public void setup() throws Exception {
        try {
            mockMvc.perform(get("/api/health"));
        } catch (Exception e) {
            log.warn("预热失败: {}", e.getMessage());
        }
        cleanupKeys();
    }

    @AfterEach
    public void cleanup() {
        cleanupKeys();
    }

    private void cleanupKeys() {
        Set<String> keys = redisRouteTemplate.stringTemplate().keys(SmartRedisLimiterRedisKeyConstant.KEY_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            redisRouteTemplate.stringTemplate().delete(keys);
        }
    }

    /**
     * 测试1：KeyProvider 提取 header 中的 clientId，不同 clientId 独立限流
     */
    @Test
    public void testHeaderKeyProviderIndependentLimit() throws Exception {
        log.info("=== 测试 KeyProvider 基于 header 独立限流（3次/10秒） ===");

        // clientA 用满 3 次
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/api/limiter/by-header").header("X-Client-Id", "clientA"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("by-header endpoint"));
        }
        // clientA 第 4 次 → 429
        mockMvc.perform(get("/api/limiter/by-header").header("X-Client-Id", "clientA"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(429));

        // clientB 仍可访问 3 次
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/api/limiter/by-header").header("X-Client-Id", "clientB"))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(get("/api/limiter/by-header").header("X-Client-Id", "clientB"))
                .andExpect(status().isTooManyRequests());

        // 验证 Redis 中确实有 keyProvider 写入的 key（含 matchedPathPattern + clientId）
        Set<String> keys = redisRouteTemplate.stringTemplate().keys(SmartRedisLimiterRedisKeyConstant.KEY_PREFIX + "*");
        if (keys == null) {
            keys = Collections.emptySet();
        }
        log.info("Redis keys: {}", keys);
        boolean hasClientAKey = keys.stream().anyMatch(k -> k.contains("clientA"));
        boolean hasClientBKey = keys.stream().anyMatch(k -> k.contains("clientB"));
        assertTrue(hasClientAKey, "应有 clientA 限流 key");
        assertTrue(hasClientBKey, "应有 clientB 限流 key");

        log.info("=== KeyProvider header 独立限流通过 ===");
    }

    /**
     * 测试2：KeyProvider 缺失 header → 返回 null → 回退 keyStrategy=path
     */
    @Test
    public void testProviderReturnsNullFallsBackToKeyStrategy() throws Exception {
        log.info("=== 测试 provider 返回 null 回退 keyStrategy=path ===");

        // 不带 header → testNullingKeyProvider 永远返回 null → 回退 path
        // 同一 path 共享限流，3 次后第 4 次 429
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/api/limiter/null-fallback/abc"))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(get("/api/limiter/null-fallback/abc"))
                .andExpect(status().isTooManyRequests());

        // 不同 path 独立计数（path 策略）
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/api/limiter/null-fallback/xyz"))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(get("/api/limiter/null-fallback/xyz"))
                .andExpect(status().isTooManyRequests());

        Set<String> keys = redisRouteTemplate.stringTemplate().keys(SmartRedisLimiterRedisKeyConstant.KEY_PREFIX + "*");
        if (keys == null) {
            keys = Collections.emptySet();
        }
        log.info("Redis keys: {}", keys);
        boolean hasPathKey = keys.stream().anyMatch(k -> k.contains("path:") && k.contains("/api/limiter/null-fallback/"));
        assertTrue(hasPathKey, "回退后应使用 path 策略生成 key");

        log.info("=== provider 返回 null 回退测试通过 ===");
    }

    /**
     * 测试3：KeyProvider 抛异常 + fallback=allow → 放行（不进入算法、不计数）
     */
    @Test
    public void testProviderThrowingFallbackAllow() throws Exception {
        log.info("=== 测试 provider 抛异常 + fallback=allow 放行 ===");

        // 100 次都应放行
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(get("/api/limiter/throwing-allow"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("throwing-allow endpoint"));
        }

        // 因 fallback=allow 直接放行，不应在 Redis 写入限流 key
        Set<String> keys = redisRouteTemplate.stringTemplate().keys(SmartRedisLimiterRedisKeyConstant.KEY_PREFIX + "*");
        if (keys == null) {
            keys = Collections.emptySet();
        }
        log.info("Redis keys after throwing-allow: {}", keys);
        boolean hasKey = keys.stream().anyMatch(k -> k.contains("throwing-allow"));
        assertEquals(false, hasKey, "fallback=allow 时不应进入算法，不应有限流 key");

        log.info("=== provider 抛异常 + fallback=allow 测试通过 ===");
    }

    /**
     * 测试4：KeyProvider 抛异常 + fallback=deny → 直接 429
     */
    @Test
    public void testProviderThrowingFallbackDeny() throws Exception {
        log.info("=== 测试 provider 抛异常 + fallback=deny 直接 429 ===");

        mockMvc.perform(get("/api/limiter/throwing-deny"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(429));

        log.info("=== provider 抛异常 + fallback=deny 测试通过 ===");
    }

    /**
     * 测试5：同时配置 keyProvider 和 keyStrategy 时，keyProvider 优先级更高
     */
    @Test
    public void testKeyProviderTakesPrecedenceOverKeyStrategy() throws Exception {
        log.info("=== 测试 keyProvider 优先级高于 keyStrategy ===");

        // 规则配置 keyProvider=testHeaderKeyProvider 且 key-strategy=path。
        // 如果实际使用 path，/a 和 /b 会独立计数；如果实际使用 provider，sameClient 共享计数，第4次应 429。
        mockMvc.perform(get("/api/limiter/provider-priority/a").header("X-Client-Id", "sameClient"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/limiter/provider-priority/b").header("X-Client-Id", "sameClient"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/limiter/provider-priority/c").header("X-Client-Id", "sameClient"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/limiter/provider-priority/d").header("X-Client-Id", "sameClient"))
                .andExpect(status().isTooManyRequests());

        Set<String> keys = redisRouteTemplate.stringTemplate().keys(SmartRedisLimiterRedisKeyConstant.KEY_PREFIX + "*");
        if (keys == null) {
            keys = Collections.emptySet();
        }
        log.info("Redis keys after provider priority test: {}", keys);
        boolean hasProviderKey = keys.stream().anyMatch(k -> k.contains("sameClient"));
        assertTrue(hasProviderKey, "同时配置 keyProvider 和 keyStrategy 时应优先使用 keyProvider 生成的 key");

        log.info("=== keyProvider 优先级测试通过 ===");
    }

    /**
     * 测试6：KeyProvider 返回空字符串 → 回退 keyStrategy=path
     */
    @Test
    public void testProviderReturnsEmptyFallsBackToKeyStrategy() throws Exception {
        log.info("=== 测试 provider 返回空字符串回退 keyStrategy=path ===");

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/api/limiter/empty-fallback/abc"))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(get("/api/limiter/empty-fallback/abc"))
                .andExpect(status().isTooManyRequests());

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/api/limiter/empty-fallback/xyz"))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(get("/api/limiter/empty-fallback/xyz"))
                .andExpect(status().isTooManyRequests());

        Set<String> keys = redisRouteTemplate.stringTemplate().keys(SmartRedisLimiterRedisKeyConstant.KEY_PREFIX + "*");
        if (keys == null) {
            keys = Collections.emptySet();
        }
        log.info("Redis keys after empty fallback test: {}", keys);
        boolean hasPathKey = keys.stream().anyMatch(k -> k.contains("path:") && k.contains("/api/limiter/empty-fallback/"));
        assertTrue(hasPathKey, "provider 返回空字符串时应回退到 path 策略生成 key");

        log.info("=== provider 返回空字符串回退测试通过 ===");
    }

    /**
     * 测试7：限流事件审计字段中 keyStrategy 应为 "custom:" + keyProviderName
     * <p>设计文档要求：keyProvider 命中时事件中 keyStrategy 字段记为
     * EVENT_KEY_STRATEGY_CUSTOM_PREFIX + keyProviderName，便于在审计日志中识别本次实际命中的 key 来源。</p>
     */
    @Test
    public void testEventKeyStrategyCarriesCustomPrefix() throws Exception {
        log.info("=== 测试限流事件 keyStrategy 字段为 'custom:' + providerName ===");

        testEventListener.reset(1);

        // 用满 3 次 → 第 4 次触发限流并发布事件（logOnPass=false，仅 limited 时发布）
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/api/limiter/by-header").header("X-Client-Id", "auditClient"))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(get("/api/limiter/by-header").header("X-Client-Id", "auditClient"))
                .andExpect(status().isTooManyRequests());

        boolean received = testEventListener.limitEventLatch.await(2, TimeUnit.SECONDS);
        assertTrue(received, "限流事件应在 2 秒内收到");

        String expected = SmartRedisLimiterConstant.EVENT_KEY_STRATEGY_CUSTOM_PREFIX + "testHeaderKeyProvider";
        boolean matched = testEventListener.records.stream()
                .anyMatch(r -> expected.equals(r.getKeyStrategy()));
        assertTrue(matched,
                "限流事件 keyStrategy 应为 '" + expected + "'，实际事件: " + testEventListener.records);

        // limitKey 也应包含 keyProvider 写入的 keyPart（matchedPathPattern + ":" + clientId）
        boolean limitKeyOk = testEventListener.records.stream()
                .anyMatch(r -> r.getLimitKey() != null && r.getLimitKey().contains("auditClient"));
        assertTrue(limitKeyOk, "事件 limitKey 应包含 KeyProvider 写入的 'auditClient'");

        log.info("=== 事件 keyStrategy 审计前缀测试通过 ===");
    }
}
