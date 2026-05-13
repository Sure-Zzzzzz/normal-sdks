package io.github.surezzzzzz.sdk.audit.limiter.test.cases;

import io.github.surezzzzzz.sdk.audit.limiter.test.SmartRedisLimiterAuditListenerTestApplication;
import io.github.surezzzzzz.sdk.audit.limiter.test.support.TestSmartRedisLimiterAuditHandler;
import io.github.surezzzzzz.sdk.limiter.redis.smart.event.SmartRedisLimiterEvent;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.SmartRedisLimiterRecord;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SmartRedisLimiter 审计监听器测试
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SmartRedisLimiterAuditListenerTestApplication.class)
public class SmartRedisLimiterAuditListenerTest {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private TestSmartRedisLimiterAuditHandler testHandler;

    @BeforeEach
    public void setUp() {
        testHandler.reset();
    }

    @Test
    public void testInterceptorRejectedEvent() throws InterruptedException {
        log.info("========== 测试：拦截器模式拒绝事件 ==========");

        SmartRedisLimiterEvent event = new SmartRedisLimiterEvent(
                this, "smart-limiter:my-service:path:/api/user/123:10s",
                "path", "sliding", "10/1s", false,
                "INTERCEPTOR",
                "/api/user/123", "GET", "192.168.1.1", "/api/user/**",
                null, null,
                null,
                10, 0, 1715635200L, 500_000L
        );

        eventPublisher.publishEvent(event);

        boolean received = testHandler.latch.await(5, TimeUnit.SECONDS);
        assertTrue(received, "Handler should receive the event");

        SmartRedisLimiterRecord record = testHandler.records.get(0);
        assertFalse(record.isPassed());
        assertEquals("INTERCEPTOR", record.getSource());
        assertEquals("sliding", record.getAlgorithm());
        assertEquals("path", record.getKeyStrategy());
        assertEquals("smart-limiter:my-service:path:/api/user/123:10s", record.getLimitKey());
        assertEquals("10/1s", record.getLimitRules());
        assertEquals("/api/user/123", record.getRequestUri());
        assertEquals("GET", record.getHttpMethod());
        assertEquals("192.168.1.1", record.getClientIp());
        assertEquals("/api/user/**", record.getMatchedPathPattern());
        assertEquals(10, record.getLimit());
        assertEquals(0, record.getRemaining());
        assertEquals(1715635200L, record.getResetAt());
        assertEquals(500_000L, record.getDurationNanos());
        assertNull(record.getMethodName());
        assertNull(record.getMethodQualifiedName());
        assertNull(record.getExtra());

        // 用户信息来自 TestAuditUserProvider
        assertEquals("test-client", record.getClientId());
        assertEquals("platform", record.getClientType());
        assertEquals("user-001", record.getUserId());
        assertEquals("testuser", record.getUsername());

        // TraceId 来自 TestAuditTraceIdProvider
        assertEquals("trace-test-001", record.getTraceId());

        log.info("testInterceptorRejectedEvent passed");
    }

    @Test
    public void testAspectPassedEvent() throws InterruptedException {
        log.info("========== 测试：注解模式通过事件 ==========");

        SmartRedisLimiterEvent event = new SmartRedisLimiterEvent(
                this, "smart-limiter:my-service:method:UserService.getUser:60s",
                "method", "fixed", "100/1m", true,
                "ASPECT",
                null, null, null, null,
                "getUser", "com.example.UserService.getUser",
                null,
                100, 95, 1715635260L, 200_000L
        );

        eventPublisher.publishEvent(event);

        boolean received = testHandler.latch.await(5, TimeUnit.SECONDS);
        assertTrue(received, "Handler should receive the event");

        SmartRedisLimiterRecord record = testHandler.records.get(0);
        assertTrue(record.isPassed());
        assertEquals("ASPECT", record.getSource());
        assertEquals("fixed", record.getAlgorithm());
        assertEquals("method", record.getKeyStrategy());
        assertEquals("smart-limiter:my-service:method:UserService.getUser:60s", record.getLimitKey());
        assertEquals("100/1m", record.getLimitRules());
        assertNull(record.getRequestUri());
        assertNull(record.getHttpMethod());
        assertNull(record.getClientIp());
        assertNull(record.getMatchedPathPattern());
        assertEquals("getUser", record.getMethodName());
        assertEquals("com.example.UserService.getUser", record.getMethodQualifiedName());
        assertEquals(100, record.getLimit());
        assertEquals(95, record.getRemaining());
        assertEquals(1715635260L, record.getResetAt());
        assertEquals(200_000L, record.getDurationNanos());
        assertNull(record.getExtra());

        log.info("testAspectPassedEvent passed");
    }

    @Test
    public void testEventWithAttributes() throws InterruptedException {
        log.info("========== 测试：带扩展属性的事件 ==========");

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("fallback", true);
        attributes.put("fallbackStrategy", "allow");

        SmartRedisLimiterEvent event = new SmartRedisLimiterEvent(
                this, "smart-limiter:my-service:path:/api/query:10s",
                "path", "fixed", "5/10s", true,
                "INTERCEPTOR",
                "/api/query", "GET", "10.0.0.1", "/api/query",
                null, null,
                attributes,
                5, 4, 1715635300L, 1_000_000L
        );

        eventPublisher.publishEvent(event);

        boolean received = testHandler.latch.await(5, TimeUnit.SECONDS);
        assertTrue(received, "Handler should receive the event");

        SmartRedisLimiterRecord record = testHandler.records.get(0);
        assertTrue(record.isPassed());
        assertEquals("INTERCEPTOR", record.getSource());
        assertEquals("fixed", record.getAlgorithm());
        assertEquals("/api/query", record.getRequestUri());
        assertEquals(5, record.getLimit());
        assertEquals(4, record.getRemaining());
        assertEquals(1715635300L, record.getResetAt());
        assertEquals(1_000_000L, record.getDurationNanos());
        assertNotNull(record.getExtra());
        assertEquals("true", record.getExtra().get("fallback"));
        assertEquals("allow", record.getExtra().get("fallbackStrategy"));
        assertEquals(2, record.getExtra().size(), "extra should only contain the 2 attributes we put in");

        log.info("testEventWithAttributes passed");
    }

    @Test
    public void testEventWithoutAttributes() throws InterruptedException {
        log.info("========== 测试：无扩展属性的事件 ==========");

        SmartRedisLimiterEvent event = new SmartRedisLimiterEvent(
                this, "smart-limiter:my-service:method:OrderService.create:60s",
                "method", "sliding", "10/1m", false,
                "ASPECT",
                null, null, null, null,
                "create", "com.example.OrderService.create",
                null,
                10, 0, 1715635400L, 300_000L
        );

        eventPublisher.publishEvent(event);

        boolean received = testHandler.latch.await(5, TimeUnit.SECONDS);
        assertTrue(received, "Handler should receive the event");

        SmartRedisLimiterRecord record = testHandler.records.get(0);
        assertFalse(record.isPassed());
        assertEquals("ASPECT", record.getSource());
        assertEquals("sliding", record.getAlgorithm());
        assertEquals(10, record.getLimit());
        assertEquals(0, record.getRemaining());
        assertEquals(1715635400L, record.getResetAt());
        assertEquals(300_000L, record.getDurationNanos());
        assertNull(record.getExtra());

        log.info("testEventWithoutAttributes passed");
    }

    @Test
    public void testMultipleHandlersReceiveSameEvent() throws InterruptedException {
        log.info("========== 测试：多个 Handler 接收同一事件 ==========");

        SmartRedisLimiterEvent event = new SmartRedisLimiterEvent(
                this, "smart-limiter:my-service:path:/api/test:5s",
                "path", "fixed", "5/10s", false,
                "INTERCEPTOR",
                "/api/test", "POST", "127.0.0.1", null,
                null, null,
                null,
                5, 0, 1715635500L, 100L
        );

        eventPublisher.publishEvent(event);

        boolean received = testHandler.latch.await(5, TimeUnit.SECONDS);
        assertTrue(received, "Handler should receive the event");

        SmartRedisLimiterRecord record = testHandler.records.get(0);
        assertFalse(record.isPassed());
        assertEquals("INTERCEPTOR", record.getSource());
        assertEquals("fixed", record.getAlgorithm());
        assertEquals("/api/test", record.getRequestUri());
        assertEquals("POST", record.getHttpMethod());
        assertEquals("127.0.0.1", record.getClientIp());
        assertEquals(5, record.getLimit());
        assertEquals(0, record.getRemaining());

        log.info("testMultipleHandlersReceiveSameEvent passed");
    }
}
