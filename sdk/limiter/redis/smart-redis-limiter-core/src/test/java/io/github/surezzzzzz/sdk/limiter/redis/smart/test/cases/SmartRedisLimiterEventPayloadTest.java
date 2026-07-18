package io.github.surezzzzzz.sdk.limiter.redis.smart.test.cases;

import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.ErrorCode;
import io.github.surezzzzzz.sdk.limiter.redis.smart.exception.SmartRedisLimiterException;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.SmartRedisLimiterEventPayload;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
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
        List<String> tags = new ArrayList<>(Arrays.asList("tag-1"));
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("tags", tags);
        attributes.put("trace", "trace-1");
        attributes.put("nested", nested);

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
                .resourceCode("test-resource")
                .policySource("remote")
                .policyRevision(3L)
                .build();

        attributes.put("trace", "trace-2");
        tags.add("tag-2");

        assertEquals("limit-key", payload.getLimitKey());
        assertEquals("route-key", payload.getRouteKey());
        assertEquals("default", payload.getDatasourceKey());
        assertEquals("standalone", payload.getRedisMode());
        assertEquals("test-resource", payload.getResourceCode());
        assertEquals("remote", payload.getPolicySource());
        assertEquals(3L, payload.getPolicyRevision());
        assertEquals("trace-1", payload.getAttributes().get("trace"));
        Map<?, ?> snapshotNested = (Map<?, ?>) payload.getAttributes().get("nested");
        assertEquals(Arrays.asList("tag-1"), snapshotNested.get("tags"),
                "嵌套集合应保持构造时快照");
        List<?> snapshotTags = (List<?>) snapshotNested.get("tags");
        assertThrows(UnsupportedOperationException.class,
                snapshotTags::clear,
                "嵌套集合快照不应允许修改");
        assertThrows(UnsupportedOperationException.class,
                () -> payload.getAttributes().put("newKey", "newValue"),
                "顶层 attributes 不应允许修改");

        SmartRedisLimiterEventPayload localPayload = new SmartRedisLimiterEventPayload(
                "limit-key", "route-key", null, "unknown", false, false,
                "path", "fixed", "10/1s", true, "INTERCEPTOR",
                null, null, null, null, null, null, null,
                10L, 9L, 100L, 1000L, null);
        assertEquals("local", localPayload.getPolicySource());
        assertEquals(null, localPayload.getResourceCode());
        assertEquals(null, localPayload.getPolicyRevision());

        Map<String, Object> invalidAttributes = new LinkedHashMap<>();
        invalidAttributes.put("unsupported", new StringBuilder("mutable"));
        SmartRedisLimiterException invalidAttribute = assertThrows(SmartRedisLimiterException.class,
                () -> SmartRedisLimiterEventPayload.builder().attributes(invalidAttributes).build(),
                "未知可变属性类型应被拒绝");
        assertEquals(ErrorCode.ATTRIBUTE_VALUE_INVALID, invalidAttribute.getErrorCode(),
                "未知属性类型应使用扩展属性错误码");
        log.info("事件载荷构建与 attributes 防御性拷贝测试通过");
    }
}
