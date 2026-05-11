package io.github.surezzzzzz.sdk.limiter.redis.smart.cases;

import io.github.surezzzzzz.sdk.limiter.redis.smart.SmartRedisLimiterApplication;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterRedisKeyConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.exception.SmartRedisLimitExceededException;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.SmartRedisLimiterRecord;
import io.github.surezzzzzz.sdk.limiter.redis.smart.service.TestService;
import io.github.surezzzzzz.sdk.limiter.redis.smart.support.TestEventListener;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SmartRedisLimiter 事件发布端到端测试
 *
 * <p>测试场景：
 * <ul>
 *   <li>Interceptor 触发限流后发布事件，Listener 收到并转换为 Record</li>
 *   <li>Aspect 触发限流后发布事件，Listener 收到并转换为 Record</li>
 *   <li>UserProvider / TraceIdProvider 的值正确填入 Record</li>
 *   <li>多次限流事件均能被 Listener 捕获</li>
 * </ul>
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SmartRedisLimiterApplication.class)
@AutoConfigureMockMvc
public class SmartRedisLimiterEndToEndTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TestService testService;
    @Autowired
    private TestEventListener testEventListener;
    @Autowired
    private RedisTemplate<String, String> smartRedisLimiterRedisTemplate;

    @BeforeEach
    public void setup() {
        testEventListener.reset();
        cleanRedis();
    }

    @AfterEach
    public void cleanup() {
        cleanRedis();
    }

    private void cleanRedis() {
        try {
            Set<String> keys = smartRedisLimiterRedisTemplate.keys(SmartRedisLimiterRedisKeyConstant.KEY_PREFIX + "*");
            if (keys != null && !keys.isEmpty()) {
                smartRedisLimiterRedisTemplate.delete(keys);
            }
        } catch (Exception e) {
            log.warn("清理 Redis 失败: {}", e.getMessage());
        }
    }

    // ==================== 测试用例 ====================

    /**
     * 测试1：Interceptor 模式触发限流 → 事件发布 → Listener 收到 Record，字段正确
     */
    @Test
    public void testInterceptorLimitEventEndToEnd() throws Exception {
        log.info("=== 测试 Interceptor 限流事件端到端 ===");

        // 触发限流（规则：5次/10秒）
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/api/public/audit-test")).andExpect(status().isOk());
        }
        mockMvc.perform(get("/api/public/audit-test")).andExpect(status().isTooManyRequests());

        // 等待事件处理
        boolean received = testEventListener.limitEventLatch.await(3, TimeUnit.SECONDS);

        assertTrue(received, "Listener 应收到限流事件");
        assertEquals(1, testEventListener.records.size(), "应收到 1 条 Record");

        SmartRedisLimiterRecord record = testEventListener.records.get(0);

        // 来源与限流结果
        assertEquals(SmartRedisLimiterConstant.SOURCE_INTERCEPTOR, record.getSource());
        assertFalse(record.isPassed());

        // 请求信息
        assertEquals("/api/public/audit-test", record.getRequestUri());
        assertEquals("GET", record.getHttpMethod());
        assertNotNull(record.getLimitKey());
        assertNotNull(record.getTimestamp());

        // UserProvider 填充
        assertEquals("test-client-id", record.getClientId());
        assertEquals("test-client-type", record.getClientType());
        assertEquals("test-user-id", record.getUserId());
        assertEquals("test-username", record.getUsername());

        // TraceIdProvider 填充
        assertEquals("test-trace-id-12345", record.getTraceId());

        // Aspect 专属字段应为空
        assertNull(record.getMethodName());
        assertNull(record.getMethodQualifiedName());

        log.info("=== Interceptor 端到端测试通过 ===");
    }

    /**
     * 测试2：Aspect 模式触发限流 → 事件发布 → Listener 收到 Record，字段正确
     */
    @Test
    public void testAspectLimitEventEndToEnd() throws Exception {
        log.info("=== 测试 Aspect 限流事件端到端 ===");

        // 触发限流（规则：10次/秒）
        for (int i = 0; i < 10; i++) {
            testService.limitedMethod("e2e-test-" + i);
        }
        try {
            testService.limitedMethod("e2e-test-10");
        } catch (SmartRedisLimitExceededException e) {
            // 预期
        }

        // 等待事件处理
        boolean received = testEventListener.limitEventLatch.await(3, TimeUnit.SECONDS);

        assertTrue(received, "Listener 应收到限流事件");
        assertEquals(1, testEventListener.records.size(), "应收到 1 条 Record");

        SmartRedisLimiterRecord record = testEventListener.records.get(0);

        // 来源与限流结果
        assertEquals(SmartRedisLimiterConstant.SOURCE_ASPECT, record.getSource());
        assertFalse(record.isPassed());

        // 方法信息
        assertEquals("limitedMethod", record.getMethodName());
        assertNotNull(record.getMethodQualifiedName());
        assertTrue(record.getMethodQualifiedName().contains("TestService"));
        assertNotNull(record.getTimestamp());

        // UserProvider 填充
        assertEquals("test-client-id", record.getClientId());
        assertEquals("test-user-id", record.getUserId());
        assertEquals("test-username", record.getUsername());

        // TraceIdProvider 填充
        assertEquals("test-trace-id-12345", record.getTraceId());

        // Interceptor 专属字段应为空
        assertNull(record.getRequestUri());
        assertNull(record.getHttpMethod());

        log.info("=== Aspect 端到端测试通过 ===");
    }

    /**
     * 测试3：多次限流事件均被 Listener 捕获
     */
    @Test
    public void testMultipleLimitEventsAllCaptured() throws Exception {
        log.info("=== 测试多次限流事件均被 Listener 捕获 ===");

        testEventListener.reset(3);

        for (int batch = 0; batch < 3; batch++) {
            cleanRedis();
            for (int i = 0; i < 5; i++) {
                mockMvc.perform(get("/api/public/audit-test")).andExpect(status().isOk());
            }
            mockMvc.perform(get("/api/public/audit-test")).andExpect(status().isTooManyRequests());
        }

        boolean received = testEventListener.limitEventLatch.await(5, TimeUnit.SECONDS);

        assertTrue(received, "应收到 3 次限流事件");
        assertEquals(3, testEventListener.records.size(), "应收到 3 条 Record");

        for (SmartRedisLimiterRecord r : testEventListener.records) {
            assertEquals("test-trace-id-12345", r.getTraceId());
            assertEquals("test-user-id", r.getUserId());
            assertFalse(r.isPassed());
        }

        log.info("=== 多事件捕获测试通过，共收到 {} 条 Record ===", testEventListener.records.size());
    }
}
