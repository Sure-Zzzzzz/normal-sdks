package io.github.surezzzzzz.sdk.limiter.redis.smart.test.cases;

import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.ErrorCode;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterManagementOperation;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterTimeUnit;
import io.github.surezzzzzz.sdk.limiter.redis.smart.event.SmartRedisLimiterManagementEvent;
import io.github.surezzzzzz.sdk.limiter.redis.smart.exception.SmartRedisLimiterException;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.SmartRedisLimiterManagementEventPayload;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.SmartRedisLimiterLimit;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.SmartRedisLimiterPolicy;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.SmartRedisLimiterPolicyKey;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SmartRedisLimiter 动态策略管理事件测试
 *
 * @author surezzzzzz
 */
@Slf4j
public class SmartRedisLimiterManagementEventTest {

    @Test
    public void testCreatePayloadAndEventSource() {
        log.info("开始测试 CREATE 管理事件载荷与事件来源");
        Object publisher = new Object();
        SmartRedisLimiterPolicy policy = policy();
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("requestId", "request-1");

        SmartRedisLimiterManagementEventPayload payload = SmartRedisLimiterManagementEventPayload.builder()
                .operation(SmartRedisLimiterManagementOperation.CREATE)
                .policyKey(policy.getKey())
                .afterPolicy(policy)
                .afterEnabled(true)
                .revision(1L)
                .operator(" admin ")
                .occurredAt(Instant.parse("2026-07-17T10:00:00Z"))
                .attributes(attributes)
                .build();
        attributes.put("requestId", "request-2");

        SmartRedisLimiterManagementEvent event = new SmartRedisLimiterManagementEvent(publisher, payload);
        assertSame(publisher, event.getSource());
        assertSame(payload, event.getPayload());
        assertEquals("admin", payload.getOperator());
        assertEquals("request-1", payload.getAttributes().get("requestId"));
        assertThrows(UnsupportedOperationException.class,
                () -> payload.getAttributes().put("new", "value"));
        log.info("CREATE 管理事件载荷与事件来源测试通过");
    }

    @Test
    public void testOperationValidation() {
        log.info("开始测试管理操作状态矩阵");
        SmartRedisLimiterPolicy policy = policy();
        Instant occurredAt = Instant.parse("2026-07-17T10:00:00Z");

        SmartRedisLimiterManagementEventPayload enable = SmartRedisLimiterManagementEventPayload.builder()
                .operation(SmartRedisLimiterManagementOperation.ENABLE)
                .policyKey(policy.getKey())
                .beforePolicy(policy)
                .afterPolicy(policy)
                .beforeEnabled(false)
                .afterEnabled(true)
                .revision(2L)
                .operator("admin")
                .occurredAt(occurredAt)
                .build();
        assertEquals(SmartRedisLimiterManagementOperation.ENABLE, enable.getOperation());

        SmartRedisLimiterException invalid = assertThrows(SmartRedisLimiterException.class,
                () -> SmartRedisLimiterManagementEventPayload.builder()
                        .operation(SmartRedisLimiterManagementOperation.ENABLE)
                        .policyKey(policy.getKey())
                        .beforePolicy(policy)
                        .afterPolicy(policy)
                        .beforeEnabled(true)
                        .afterEnabled(true)
                        .revision(2L)
                        .operator("admin")
                        .occurredAt(occurredAt)
                        .build());
        assertEquals(ErrorCode.MANAGEMENT_PAYLOAD_INVALID, invalid.getErrorCode());

        SmartRedisLimiterException nullRevision = assertThrows(SmartRedisLimiterException.class,
                () -> SmartRedisLimiterManagementEventPayload.builder()
                        .operation(SmartRedisLimiterManagementOperation.CREATE)
                        .policyKey(policy.getKey())
                        .afterPolicy(policy)
                        .afterEnabled(true)
                        .revision(null)
                        .operator("admin")
                        .occurredAt(occurredAt)
                        .build(),
                "管理事件 revision 为空时应拒绝载荷");
        assertEquals(ErrorCode.MANAGEMENT_PAYLOAD_INVALID, nullRevision.getErrorCode(),
                "revision 为空应使用管理事件载荷错误码");

        SmartRedisLimiterException nullPayload = assertThrows(SmartRedisLimiterException.class,
                () -> new SmartRedisLimiterManagementEvent(new Object(), null),
                "管理事件 payload 为空时应拒绝构造");
        assertEquals(ErrorCode.MANAGEMENT_PAYLOAD_INVALID, nullPayload.getErrorCode(),
                "payload 为空应使用管理事件载荷错误码");
        log.info("管理操作状态矩阵测试通过");
    }

    private SmartRedisLimiterPolicy policy() {
        SmartRedisLimiterPolicyKey key = new SmartRedisLimiterPolicyKey(
                "test-service", "test-resource", "test-subject");
        return new SmartRedisLimiterPolicy(key, Collections.singletonList(
                new SmartRedisLimiterLimit(10L, 1L, SmartRedisLimiterTimeUnit.MINUTES)));
    }
}
