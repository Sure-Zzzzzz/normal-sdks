package io.github.surezzzzzz.sdk.limiter.redis.smart.cases;

import io.github.surezzzzzz.sdk.limiter.redis.smart.SmartRedisLimiterApplication;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterRedisKeyConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.event.SmartRedisLimiterEvent;
import io.github.surezzzzzz.sdk.limiter.redis.smart.exception.SmartRedisLimitExceededException;
import io.github.surezzzzzz.sdk.limiter.redis.smart.service.TestService;
import io.github.surezzzzzz.sdk.limiter.redis.smart.support.TestAuditEventListener;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SmartRedisLimiter 审计事件发布测试
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SmartRedisLimiterApplication.class)
@AutoConfigureMockMvc
public class SmartRedisLimiterAuditTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RedisTemplate<String, String> smartRedisLimiterRedisTemplate;

    @Autowired
    private TestService testService;

    @Autowired
    private TestAuditEventListener eventListener;

    @BeforeEach
    public void setup() throws Exception {
        eventListener.clear();
        // 预热 Redis 连接
        try {
            mockMvc.perform(get("/api/health"));
        } catch (Exception e) {
            log.warn("Redis 预热失败: {}", e.getMessage());
        }
    }

    @AfterEach
    public void cleanup() {
        Set<String> keys = smartRedisLimiterRedisTemplate.keys(
                SmartRedisLimiterRedisKeyConstant.KEY_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            smartRedisLimiterRedisTemplate.delete(keys);
            log.info("清理了 {} 个限流 key", keys.size());
        }
    }

    // ========== Interceptor 模式测试 ==========

    /**
     * 测试 Interceptor 模式限流触发时发布事件
     */
    @Test
    public void testInterceptorModeLimitEventPublished() throws Exception {
        log.info("=== 测试 Interceptor 模式限流触发时发布事件 ===");

        // 触发限流（规则：5次/10秒）
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/api/public/audit-test"))
                    .andDo(print())
                    .andExpect(status().isOk());
        }
        // 第6次触发限流
        mockMvc.perform(get("/api/public/audit-test"))
                .andDo(print())
                .andExpect(status().isTooManyRequests());

        SmartRedisLimiterEvent event = eventListener.findFirstBySource(
                SmartRedisLimiterConstant.SOURCE_INTERCEPTOR);

        assertNotNull(event, "应发布 INTERCEPTOR 事件");
        assertFalse(event.isPassed(), "限流触发时 passed 应为 false");
        assertEquals("path", event.getKeyStrategy(), "keyStrategy 应为 path");
        assertNotNull(event.getLimitKey(), "limitKey 不应为空");
        assertEquals("GET", event.getHttpMethod(), "httpMethod 应为 GET");
        assertEquals("/api/public/audit-test", event.getRequestUri(), "requestUri 应匹配");
        assertNull(event.getMethodName(), "Aspect 字段应为空");

        log.info("=== Interceptor 限流事件发布测试通过 ===");
    }

    /**
     * 测试 Interceptor 模式限流通过时默认不发布事件（logOnPass=false）
     */
    @Test
    public void testInterceptorModePassNoEventWhenLogOnPassDisabled() throws Exception {
        log.info("=== 测试 Interceptor 模式限流通过时默认不发布事件 ===");

        // 触发一次通过
        mockMvc.perform(get("/api/public/audit-pass"))
                .andDo(print())
                .andExpect(status().isOk());

        SmartRedisLimiterEvent event = eventListener.findFirstBySource(
                SmartRedisLimiterConstant.SOURCE_INTERCEPTOR);

        assertNull(event, "logOnPass=false 时，通过的请求不应发布事件");

        log.info("=== Interceptor 通过不发布测试通过 ===");
    }

    // ========== Aspect 模式测试 ==========

    /**
     * 测试 Aspect 模式限流触发时发布事件
     */
    @Test
    public void testAspectModeLimitEventPublished() throws Exception {
        log.info("=== 测试 Aspect 模式限流触发时发布事件 ===");

        // 规则：10次/秒，前10次通过，第11次触发限流
        for (int i = 0; i < 10; i++) {
            testService.limitedMethod("audit-test-" + i);
        }
        try {
            testService.limitedMethod("audit-test-10");
        } catch (SmartRedisLimitExceededException e) {
            // 预期：第11次触发限流
        }

        SmartRedisLimiterEvent event = eventListener.findFirstBySource(
                SmartRedisLimiterConstant.SOURCE_ASPECT);

        assertNotNull(event, "应发布 ASPECT 事件");
        assertFalse(event.isPassed(), "限流触发时 passed 应为 false");
        assertEquals("method", event.getKeyStrategy(), "keyStrategy 应为 method");
        assertEquals("limitedMethod", event.getMethodName(), "methodName 应为 limitedMethod");
        assertNotNull(event.getMethodQualifiedName(), "methodQualifiedName 不应为空");
        assertNull(event.getRequestUri(), "Interceptor 字段应为空");

        log.info("=== Aspect 限流事件发布测试通过 ===");
    }

    /**
     * 测试 Aspect 模式限流通过时默认不发布事件
     */
    @Test
    public void testAspectModePassNoEventWhenLogOnPassDisabled() throws Exception {
        log.info("=== 测试 Aspect 模式限流通过时默认不发布事件 ===");

        testService.limitedMethod("audit-pass");

        SmartRedisLimiterEvent event = eventListener.findFirstBySource(
                SmartRedisLimiterConstant.SOURCE_ASPECT);

        assertNull(event, "logOnPass=false 时，通过的请求不应发布事件");

        log.info("=== Aspect 通过不发布测试通过 ===");
    }
}
