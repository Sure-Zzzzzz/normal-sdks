package io.github.surezzzzzz.sdk.limiter.redis.smart.test.cases;

import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.ErrorCode;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.event.SmartRedisLimiterEvent;
import io.github.surezzzzzz.sdk.limiter.redis.smart.exception.SmartRedisLimiterException;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.SmartRedisLimiterEventPayload;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SmartRedisLimiterEvent 测试
 *
 * @author surezzzzzz
 */
@Slf4j
public class SmartRedisLimiterEventTest {

    @Test
    public void testPayloadConstructorAndGetterDelegation() {
        log.info("开始测试事件 payload 构造器和 getter 委托");
        Object publisher = new Object();
        SmartRedisLimiterEventPayload payload = SmartRedisLimiterEventPayload.builder()
                .limitKey("limit-key")
                .routeKey("route-key")
                .datasourceKey("default")
                .redisMode(SmartRedisLimiterConstant.REDIS_MODE_STANDALONE)
                .routeRequired(true)
                .routeResolved(true)
                .keyStrategy("path")
                .algorithm("sliding")
                .limitRules("10/1s")
                .passed(false)
                .sourceType(SmartRedisLimiterConstant.SOURCE_INTERCEPTOR)
                .requestUri("/api/test")
                .httpMethod("GET")
                .clientIp("127.0.0.1")
                .matchedPathPattern("/api/test")
                .limit(10)
                .remaining(0)
                .resetAt(100)
                .durationNanos(2000)
                .fallbackReason(SmartRedisLimiterConstant.FALLBACK_REASON_REDIS_ERROR)
                .resourceCode("test-resource")
                .policySource(SmartRedisLimiterConstant.POLICY_SOURCE_REMOTE)
                .policyRevision(3L)
                .build();

        SmartRedisLimiterEvent event = new SmartRedisLimiterEvent(publisher, payload);

        assertSame(payload, event.getPayload());
        assertSame(publisher, event.getRawSource());
        assertEquals(SmartRedisLimiterConstant.SOURCE_INTERCEPTOR, event.getSource());
        assertEquals(SmartRedisLimiterConstant.SOURCE_INTERCEPTOR, event.getSourceType());
        assertEquals("limit-key", event.getLimitKey());
        assertEquals("route-key", event.getRouteKey());
        assertEquals("default", event.getDatasourceKey());
        assertEquals(SmartRedisLimiterConstant.REDIS_MODE_STANDALONE, event.getRedisMode());
        assertTrue(event.isRouteRequired());
        assertTrue(event.isRouteResolved());
        assertEquals(SmartRedisLimiterConstant.FALLBACK_REASON_REDIS_ERROR, event.getFallbackReason());
        assertEquals("test-resource", event.getResourceCode());
        assertEquals(SmartRedisLimiterConstant.POLICY_SOURCE_REMOTE, event.getPolicySource());
        assertEquals(3L, event.getPolicyRevision());
        SmartRedisLimiterException nullPayload = assertThrows(SmartRedisLimiterException.class,
                () -> new SmartRedisLimiterEvent(publisher, null),
                "执行事件 payload 为空时应使用统一异常体系");
        assertEquals(ErrorCode.EXECUTION_EVENT_PAYLOAD_INVALID, nullPayload.getErrorCode(),
                "执行事件 payload 为空应使用标准错误码");
        log.info("事件 payload 构造器和 getter 委托测试通过");
    }

    @Test
    public void testDeprecatedConstructorBridge() {
        log.info("开始测试旧构造器兼容桥");
        Object publisher = new Object();
        SmartRedisLimiterEvent event = new SmartRedisLimiterEvent(
                publisher,
                "limit-key",
                "path",
                "fixed",
                "10/1s",
                true,
                SmartRedisLimiterConstant.SOURCE_INTERCEPTOR,
                "/api/test",
                "GET",
                "127.0.0.1",
                "/api/test",
                null,
                null,
                null,
                10,
                9,
                100,
                1000);

        assertEquals("limit-key", event.getLimitKey());
        assertEquals("limit-key", event.getRouteKey());
        assertNull(event.getDatasourceKey());
        assertEquals(SmartRedisLimiterConstant.REDIS_MODE_UNKNOWN, event.getRedisMode());
        assertNull(event.getFallbackReason());
        assertNull(event.getResourceCode());
        assertEquals(SmartRedisLimiterConstant.POLICY_SOURCE_LOCAL, event.getPolicySource());
        assertNull(event.getPolicyRevision());
        assertEquals(SmartRedisLimiterConstant.SOURCE_INTERCEPTOR, event.getSource());
        assertSame(publisher, event.getRawSource());
        log.info("旧构造器兼容桥测试通过");
    }
}
