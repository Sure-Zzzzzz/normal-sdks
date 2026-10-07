package io.github.surezzzzzz.sdk.audit.limiter.test.cases;

import io.github.surezzzzzz.sdk.audit.limiter.test.SmartRedisLimiterAuditListenerTestApplication;
import io.github.surezzzzzz.sdk.audit.limiter.test.support.TestSmartRedisLimiterAuditHandler;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.event.SmartRedisLimiterEvent;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.SmartRedisLimiterEventPayload;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.SmartRedisLimiterRecord;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;

import java.util.LinkedHashMap;
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
        publish(SmartRedisLimiterEventPayload.builder()
                .limitKey("smart-limiter:test-service:path")
                .routeKey("smart-limiter:test-service:path")
                .datasourceKey("test-redis")
                .redisMode(SmartRedisLimiterConstant.REDIS_MODE_STANDALONE)
                .routeRequired(true)
                .routeResolved(true)
                .keyStrategy("path")
                .algorithm("sliding")
                .limitRules("10/1s")
                .passed(false)
                .sourceType("INTERCEPTOR")
                .requestUri("/api/user/123")
                .httpMethod("GET")
                .clientIp("192.168.1.1")
                .matchedPathPattern("/api/user/**")
                .limit(10L)
                .remaining(0L)
                .resetAt(1715635200L)
                .durationNanos(500000L)
                .policySource(SmartRedisLimiterConstant.POLICY_SOURCE_LOCAL)
                .build());

        SmartRedisLimiterRecord record = awaitRecord();
        log.info("拦截器审计快照：source={}, algorithm={}, routeResolved={}",
                record.getSource(), record.getAlgorithm(), record.isRouteResolved());
        assertFalse(record.isPassed());
        assertEquals("INTERCEPTOR", record.getSource());
        assertEquals("sliding", record.getAlgorithm());
        assertEquals("path", record.getKeyStrategy());
        assertEquals("smart-limiter:test-service:path", record.getLimitKey());
        assertEquals("test-redis", record.getDatasourceKey());
        assertEquals(SmartRedisLimiterConstant.REDIS_MODE_STANDALONE, record.getRedisMode());
        assertTrue(record.isRouteRequired());
        assertTrue(record.isRouteResolved());
        assertEquals("10/1s", record.getLimitRules());
        assertEquals("/api/user/123", record.getRequestUri());
        assertEquals("GET", record.getHttpMethod());
        assertEquals("192.168.1.1", record.getClientIp());
        assertEquals("/api/user/**", record.getMatchedPathPattern());
        assertEquals(10L, record.getLimit());
        assertEquals(0L, record.getRemaining());
        assertEquals(1715635200L, record.getResetAt());
        assertEquals(500000L, record.getDurationNanos());
        assertNull(record.getMethodName());
        assertNull(record.getMethodQualifiedName());
        assertNull(record.getExtra());
        assertEquals("test-client", record.getClientId());
        assertEquals("platform", record.getClientType());
        assertEquals("user-001", record.getUserId());
        assertEquals("testuser", record.getUsername());
        assertEquals("trace-test-001", record.getTraceId());
    }

    @Test
    public void testAspectPassedEvent() throws InterruptedException {
        publish(SmartRedisLimiterEventPayload.builder()
                .limitKey("smart-limiter:test-service:method")
                .routeKey("smart-limiter:test-service:method")
                .datasourceKey("test-redis")
                .redisMode(SmartRedisLimiterConstant.REDIS_MODE_STANDALONE)
                .routeRequired(true)
                .routeResolved(true)
                .keyStrategy("method")
                .algorithm("fixed")
                .limitRules("100/1m")
                .passed(true)
                .sourceType("ASPECT")
                .methodName("getUser")
                .methodQualifiedName("example.UserService.getUser")
                .limit(100L)
                .remaining(95L)
                .resetAt(1715635260L)
                .durationNanos(200000L)
                .policySource(SmartRedisLimiterConstant.POLICY_SOURCE_LOCAL)
                .build());

        SmartRedisLimiterRecord record = awaitRecord();
        log.info("注解审计快照：source={}, algorithm={}, remaining={}",
                record.getSource(), record.getAlgorithm(), record.getRemaining());
        assertTrue(record.isPassed());
        assertEquals("ASPECT", record.getSource());
        assertEquals("fixed", record.getAlgorithm());
        assertEquals("method", record.getKeyStrategy());
        assertEquals("smart-limiter:test-service:method", record.getLimitKey());
        assertNull(record.getRequestUri());
        assertNull(record.getHttpMethod());
        assertNull(record.getClientIp());
        assertNull(record.getMatchedPathPattern());
        assertEquals("getUser", record.getMethodName());
        assertEquals("example.UserService.getUser", record.getMethodQualifiedName());
        assertEquals(100L, record.getLimit());
        assertEquals(95L, record.getRemaining());
        assertEquals(1715635260L, record.getResetAt());
        assertEquals(200000L, record.getDurationNanos());
        assertNull(record.getExtra());
    }

    @Test
    public void testEventAttributesAreNotForwarded() throws InterruptedException {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("authorization", "test-secret-value");
        attributes.put("requestBody", "test-request-body-value");

        publish(SmartRedisLimiterEventPayload.builder()
                .limitKey("smart-limiter:test-service:path")
                .routeKey("smart-limiter:test-service:path")
                .redisMode(SmartRedisLimiterConstant.REDIS_MODE_STANDALONE)
                .routeRequired(true)
                .routeResolved(true)
                .keyStrategy("path")
                .algorithm("fixed")
                .limitRules("5/10s")
                .passed(true)
                .sourceType("INTERCEPTOR")
                .requestUri("/api/query")
                .httpMethod("GET")
                .matchedPathPattern("/api/query")
                .attributes(attributes)
                .limit(5L)
                .remaining(4L)
                .resetAt(1715635300L)
                .durationNanos(1000000L)
                .policySource(SmartRedisLimiterConstant.POLICY_SOURCE_LOCAL)
                .build());

        SmartRedisLimiterRecord record = awaitRecord();
        log.info("属性脱敏审计快照：source={}, passed={}, extraIsNull={}",
                record.getSource(), record.isPassed(), record.getExtra() == null);
        assertTrue(record.isPassed());
        assertEquals("INTERCEPTOR", record.getSource());
        assertEquals("fixed", record.getAlgorithm());
        assertEquals("/api/query", record.getRequestUri());
        assertEquals(5L, record.getLimit());
        assertEquals(4L, record.getRemaining());
        assertEquals(1715635300L, record.getResetAt());
        assertEquals(1000000L, record.getDurationNanos());
        assertNull(record.getExtra(), "任意 Context attributes 不得进入 2.x 审计记录");
    }

    @Test
    public void testRemoteFallbackEvent() throws InterruptedException {
        publish(SmartRedisLimiterEventPayload.builder()
                .limitKey("smart-limiter:test-service:remote")
                .routeKey("smart-limiter:test-service:remote")
                .redisMode(SmartRedisLimiterConstant.REDIS_MODE_UNKNOWN)
                .routeRequired(true)
                .routeResolved(false)
                .keyStrategy("path")
                .algorithm("sliding")
                .limitRules("10/1m")
                .passed(false)
                .sourceType("INTERCEPTOR")
                .matchedPathPattern("/api/fallback")
                .limit(10L)
                .remaining(0L)
                .resetAt(1715635400L)
                .durationNanos(300000L)
                .fallbackReason("route-unavailable")
                .resourceCode("test-resource")
                .policySource(SmartRedisLimiterConstant.POLICY_SOURCE_REMOTE)
                .policyRevision(9L)
                .build());

        SmartRedisLimiterRecord record = awaitRecord();
        log.info("远程降级审计快照：passed={}, routeResolved={}, policySource={}",
                record.isPassed(), record.isRouteResolved(), record.getPolicySource());
        assertFalse(record.isPassed());
        assertFalse(record.isRouteResolved());
        assertEquals("route-unavailable", record.getFallbackReason());
        assertEquals("test-resource", record.getResourceCode());
        assertEquals(SmartRedisLimiterConstant.POLICY_SOURCE_REMOTE, record.getPolicySource());
        assertEquals(Long.valueOf(9L), record.getPolicyRevision());
    }

    private void publish(SmartRedisLimiterEventPayload payload) {
        eventPublisher.publishEvent(new SmartRedisLimiterEvent(this, payload));
    }

    private SmartRedisLimiterRecord awaitRecord() throws InterruptedException {
        log.info("等待异步审计 Handler 投递记录");
        assertTrue(testHandler.latch.await(5, TimeUnit.SECONDS), "Handler 应接收到审计记录");
        log.info("异步审计 Handler 收到记录数：{}", testHandler.records.size());
        assertEquals(1, testHandler.records.size(), "每个事件应只投递一条审计记录");
        return testHandler.records.get(0);
    }
}
