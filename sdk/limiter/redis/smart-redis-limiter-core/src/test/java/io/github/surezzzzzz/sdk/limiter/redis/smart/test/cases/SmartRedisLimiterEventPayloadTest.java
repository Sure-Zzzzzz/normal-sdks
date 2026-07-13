package io.github.surezzzzzz.sdk.limiter.redis.smart.test.cases;

import io.github.surezzzzzz.sdk.limiter.redis.smart.model.SmartRedisLimiterEventPayload;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * SmartRedisLimiterEventPayload 测试
 *
 * @author surezzzzzz
 */
@Slf4j
public class SmartRedisLimiterEventPayloadTest {

    @Test
    public void testPayloadBuilderAndAttributesCopy() {
        log.info("开始测试事件载荷构建与 attributes 防御性拷贝");
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("trace", "trace-1");

        SmartRedisLimiterEventPayload payload = SmartRedisLimiterEventPayload.builder()
                .limitKey("limit-key")
                .routeKey("route-key")
                .datasourceKey("default")
                .redisMode("standalone")
                .routeRequired(true)
                .routeResolved(true)
                .keyStrategy("path")
                .algorithm("fixed")
                .limitRules("10/1s")
                .passed(true)
                .sourceType("INTERCEPTOR")
                .attributes(attributes)
                .limit(10)
                .remaining(9)
                .resetAt(100)
                .durationNanos(1000)
                .fallbackReason(null)
                .build();

        attributes.put("trace", "trace-2");

        assertEquals("limit-key", payload.getLimitKey());
        assertEquals("route-key", payload.getRouteKey());
        assertEquals("default", payload.getDatasourceKey());
        assertEquals("standalone", payload.getRedisMode());
        assertEquals("trace-1", payload.getAttributes().get("trace"));
        assertThrows(UnsupportedOperationException.class, () -> payload.getAttributes().put("newKey", "newValue"));
        log.info("事件载荷构建与 attributes 防御性拷贝测试通过");
    }
}
