package io.github.surezzzzzz.sdk.limiter.redis.smart.test.cases;

import io.github.surezzzzzz.sdk.limiter.redis.smart.algorithm.SmartRedisLimiterContext;
import io.github.surezzzzzz.sdk.limiter.redis.smart.algorithm.SmartRedisLimiterResult;
import io.github.surezzzzzz.sdk.limiter.redis.smart.configuration.SmartRedisLimiterProperties;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterContextAttribute;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterTimeUnit;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.SmartRedisLimiterEventPayload;
import io.github.surezzzzzz.sdk.limiter.redis.smart.support.SmartRedisLimiterEventHelper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SmartRedisLimiter 事件载荷辅助类测试
 *
 * @author surezzzzzz
 */
@Slf4j
public class SmartRedisLimiterEventPayloadHelperTest {

    @Test
    public void testBuildPayloadUsesResultRouteSnapshot() {
        SmartRedisLimiterContext context = SmartRedisLimiterContext.builder()
                .attribute(SmartRedisLimiterContextAttribute.REQUEST_PATH, "/test")
                .attribute(SmartRedisLimiterContextAttribute.REQUEST_METHOD, "GET")
                .attribute(SmartRedisLimiterContextAttribute.CLIENT_IP, "127.0.0.1")
                .attribute(SmartRedisLimiterContextAttribute.DURATION_NANOS, 123L)
                .build();
        SmartRedisLimiterResult result = SmartRedisLimiterResult.builder()
                .passed(true)
                .limit(10L)
                .remaining(9L)
                .resetAt(1000L)
                .routeKey("smart-limiter:test:path:/test:GET")
                .datasourceKey("default")
                .redisMode(SmartRedisLimiterConstant.REDIS_MODE_STANDALONE)
                .routeRequired(true)
                .routeResolved(true)
                .build();

        SmartRedisLimiterEventPayload payload = SmartRedisLimiterEventHelper.buildEventPayload(
                context,
                Collections.singletonList(rule()),
                "path",
                SmartRedisLimiterConstant.ALGORITHM_FIXED,
                result,
                SmartRedisLimiterConstant.SOURCE_INTERCEPTOR);

        log.info("事件路由快照: routeKey={}, datasource={}, mode={}",
                payload.getRouteKey(), payload.getDatasourceKey(), payload.getRedisMode());
        assertEquals(result.getRouteKey(), payload.getLimitKey(), "limitKey 应与 routeKey 一致");
        assertEquals(result.getRouteKey(), payload.getRouteKey(), "routeKey 应来自限流结果快照");
        assertEquals("default", payload.getDatasourceKey(), "datasourceKey 应来自限流结果快照");
        assertEquals(SmartRedisLimiterConstant.REDIS_MODE_STANDALONE, payload.getRedisMode(),
                "Redis 模式应精确透传");
        assertTrue(payload.isRouteRequired(), "2.0.0 事件必须标记 routeRequired=true");
        assertTrue(payload.isRouteResolved(), "已解析 datasource 时应标记 routeResolved=true");
        assertEquals(123L, payload.getDurationNanos(), "耗时应来自业务线程上下文");
    }

    @Test
    public void testFallbackAllowPayloadKeepsFallbackReason() {
        SmartRedisLimiterContext context = SmartRedisLimiterContext.builder()
                .attribute(SmartRedisLimiterContextAttribute.REQUEST_PATH, "/test")
                .build();
        SmartRedisLimiterResult result = SmartRedisLimiterResult.builder()
                .passed(true)
                .limit(10L)
                .remaining(9L)
                .resetAt(1000L)
                .fallback(true)
                .fallbackReason(SmartRedisLimiterConstant.FALLBACK_REASON_TIMEOUT)
                .routeKey("smart-limiter:test:path:/test")
                .redisMode(SmartRedisLimiterConstant.REDIS_MODE_UNKNOWN)
                .routeRequired(true)
                .routeResolved(false)
                .build();

        SmartRedisLimiterEventPayload payload = SmartRedisLimiterEventHelper.buildEventPayload(
                context,
                Collections.singletonList(rule()),
                "path",
                SmartRedisLimiterConstant.ALGORITHM_FIXED,
                result,
                SmartRedisLimiterConstant.SOURCE_INTERCEPTOR);

        log.info("fallback allow 事件: passed={}, reason={}", payload.isPassed(), payload.getFallbackReason());
        assertTrue(payload.isPassed(), "fallback=allow 的事件应标记 passed=true");
        assertEquals(SmartRedisLimiterConstant.FALLBACK_REASON_TIMEOUT, payload.getFallbackReason(),
                "降级原因应完整透传");
        assertTrue(payload.isRouteRequired(), "降级事件仍应标记 routeRequired=true");
        assertFalse(payload.isRouteResolved(), "超时前未拿到快照时 routeResolved 应为 false");
    }

    private SmartRedisLimiterProperties.SmartLimitRule rule() {
        SmartRedisLimiterProperties.SmartLimitRule rule = new SmartRedisLimiterProperties.SmartLimitRule();
        rule.setCount(10L);
        rule.setWindow(1L);
        rule.setUnit(SmartRedisLimiterTimeUnit.SECONDS);
        return rule;
    }
}
