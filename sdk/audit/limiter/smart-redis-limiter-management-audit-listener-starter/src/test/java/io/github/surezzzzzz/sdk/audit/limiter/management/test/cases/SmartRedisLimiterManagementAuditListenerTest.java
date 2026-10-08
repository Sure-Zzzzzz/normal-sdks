package io.github.surezzzzzz.sdk.audit.limiter.management.test.cases;

import io.github.surezzzzzz.sdk.audit.limiter.management.handler.SmartRedisLimiterManagementAuditHandler;
import io.github.surezzzzzz.sdk.audit.limiter.management.listener.SmartRedisLimiterManagementAuditEventListener;
import io.github.surezzzzzz.sdk.audit.limiter.management.listener.SmartRedisLimiterManagementAuditHandlerDispatcher;
import io.github.surezzzzzz.sdk.audit.limiter.management.model.SmartRedisLimiterManagementAuditRecord;
import io.github.surezzzzzz.sdk.audit.limiter.management.support.SmartRedisLimiterManagementAuditRecordHelper;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterManagementOperation;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterTimeUnit;
import io.github.surezzzzzz.sdk.limiter.redis.smart.event.SmartRedisLimiterManagementEvent;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.SmartRedisLimiterManagementEventPayload;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.SmartRedisLimiterLimit;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.SmartRedisLimiterPolicy;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.SmartRedisLimiterPolicyKey;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 三元组管理事件审计接入测试：动作事实映射 + 监听器分发 + 窗口数值不进记录
 *
 * @author surezzzzzz
 */
@Slf4j
public class SmartRedisLimiterManagementAuditListenerTest {

    private static SmartRedisLimiterManagementEventPayload payload() {
        SmartRedisLimiterPolicyKey key =
                new SmartRedisLimiterPolicyKey("mock-service", "mock-resource", "mock-subject");
        // core 载荷契约：DISABLE 表达启停变化且策略内容不变
        SmartRedisLimiterPolicy policy = new SmartRedisLimiterPolicy(key,
                Collections.singletonList(new SmartRedisLimiterLimit(100L, 1L, SmartRedisLimiterTimeUnit.MINUTES)));
        return SmartRedisLimiterManagementEventPayload.builder()
                .operation(SmartRedisLimiterManagementOperation.DISABLE)
                .policyKey(key)
                .beforePolicy(policy)
                .afterPolicy(policy)
                .beforeEnabled(Boolean.TRUE)
                .afterEnabled(Boolean.FALSE)
                .revision(9L)
                .operator("mock-person:mock-subject-id")
                .occurredAt(Instant.parse("2026-10-08T12:00:00Z"))
                .build();
    }

    @Test
    public void testRecordMappingCarriesActionFactsOnly() {
        SmartRedisLimiterManagementAuditRecord record =
                new SmartRedisLimiterManagementAuditRecordHelper().map(payload());
        assertEquals("DISABLE", record.getOperation());
        assertEquals("mock-service", record.getServiceCode());
        assertEquals("mock-resource", record.getResourceCode());
        assertEquals("mock-subject", record.getSubject());
        assertEquals(Boolean.TRUE, record.getBeforeEnabled());
        assertEquals(Boolean.FALSE, record.getAfterEnabled());
        assertEquals(Long.valueOf(9L), record.getRevision());
        assertEquals("mock-person:mock-subject-id", record.getOperator());
        log.info("动作事实审计记录: operation={}, subject={}, operator={}",
                record.getOperation(), record.getSubject(), record.getOperator());
    }

    @Test
    public void testListenerDispatchesAndIsolatesHandlerFailure() {
        AtomicReference<SmartRedisLimiterManagementAuditRecord> brokenReceived = new AtomicReference<>();
        SmartRedisLimiterManagementAuditEventListener listener = new SmartRedisLimiterManagementAuditEventListener(
                Arrays.asList(
                        new SmartRedisLimiterManagementAuditHandler() {
                            @Override
                            public void handle(SmartRedisLimiterManagementAuditRecord record) {
                                throw new IllegalStateException("第一个 Handler 故障");
                            }
                        },
                        new SmartRedisLimiterManagementAuditHandler() {
                            @Override
                            public void handle(SmartRedisLimiterManagementAuditRecord record) {
                                brokenReceived.set(record);
                            }

                            @Override
                            public String getName() {
                                return "second";
                            }
                        }),
                null,
                new SmartRedisLimiterManagementAuditHandlerDispatcher());
        listener.onManagementEvent(new SmartRedisLimiterManagementEvent(new Object(), payload()));
        assertEquals("DISABLE", brokenReceived.get().getOperation(),
                "前序 Handler 故障不得中断后续分发");
    }

    @Test
    public void testMappingToleratesMissingOptionalFields() {
        SmartRedisLimiterPolicyKey key =
                new SmartRedisLimiterPolicyKey("mock-service", "mock-resource", "mock-subject");
        SmartRedisLimiterPolicy policy = new SmartRedisLimiterPolicy(key,
                Collections.singletonList(new SmartRedisLimiterLimit(100L, 1L, SmartRedisLimiterTimeUnit.MINUTES)));
        SmartRedisLimiterManagementEventPayload sparse = SmartRedisLimiterManagementEventPayload.builder()
                .operation(SmartRedisLimiterManagementOperation.DELETE)
                .policyKey(key)
                .beforePolicy(policy)
                .beforeEnabled(Boolean.FALSE)
                .revision(1L)
                .operator("mock-person:mock-subject-id")
                .occurredAt(Instant.parse("2026-10-08T12:00:00Z"))
                .build();
        SmartRedisLimiterManagementAuditRecord record =
                new SmartRedisLimiterManagementAuditRecordHelper().map(sparse);
        assertNull(record.getAfterEnabled(), "删除事件不带后置状态");
        assertEquals("DELETE", record.getOperation());
    }
}
