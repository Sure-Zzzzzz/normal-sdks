package io.github.surezzzzzz.sdk.audit.limiter.test.cases;

import io.github.surezzzzzz.sdk.audit.limiter.handler.SmartRedisLimiterTypedAuditHandler;
import io.github.surezzzzzz.sdk.audit.limiter.listener.SmartRedisLimiterAuditEventListener;
import io.github.surezzzzzz.sdk.audit.limiter.model.SmartRedisLimiterTypedAuditRecord;
import io.github.surezzzzzz.sdk.audit.limiter.support.SmartRedisLimiterTypedAuditRecordHelper;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterDataDimension;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterManagementOperation;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterRuleSelector;
import io.github.surezzzzzz.sdk.limiter.redis.smart.event.SmartRedisLimiterTypedManagementEvent;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.SmartRedisLimiterTypedManagementEventPayload;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 类型化管理事件审计接入测试：受控摘要映射 + 监听器分发 + Handler 异常隔离
 *
 * @author surezzzzzz
 */
@Slf4j
public class SmartRedisLimiterTypedAuditListenerTest {

    private static SmartRedisLimiterTypedManagementEventPayload payload(Map<String, Object> attributes) {
        return new SmartRedisLimiterTypedManagementEventPayload(
                SmartRedisLimiterManagementOperation.CREATE,
                "mock-service", "mock-resource",
                SmartRedisLimiterDataDimension.CUSTOMER,
                SmartRedisLimiterRuleSelector.EXACT,
                "d1g35t000000000000000000000000000000000000000000000000000000d1g3",
                42L, 7L,
                SmartRedisLimiterConstant.TYPED_EVENT_RESULT_SUCCESS,
                null, Instant.parse("2026-10-06T12:00:00Z"),
                attributes == null ? new HashMap<String, Object>() : attributes);
    }

    @Test
    public void testRecordMappingCarriesControlledDigestOnly() {
        Map<String, Object> attributes = new HashMap<>();
        Map<String, Object> identity = new HashMap<>();
        identity.put("schema", SmartRedisLimiterConstant.OPERATOR_IDENTITY_SCHEMA);
        attributes.put(SmartRedisLimiterConstant.OPERATOR_IDENTITY_ATTRIBUTE_KEY,
                "resource:v1:sha256:abc");
        SmartRedisLimiterTypedAuditRecord record =
                new SmartRedisLimiterTypedAuditRecordHelper().map(payload(attributes));
        assertEquals("CREATE", record.getOperation());
        assertEquals("CUSTOMER", record.getDimension());
        assertEquals("EXACT", record.getSelector());
        assertEquals(Long.valueOf(42L), record.getRuleId());
        assertEquals(Long.valueOf(7L), record.getRevision());
        assertEquals("resource:v1:sha256:abc", record.getOperatorDigest());
        assertNull(record.getReason(), "成功事件不带原因");
        log.info("受控摘要审计记录: operation={}, dimension={}, objectDigest={}",
                record.getOperation(), record.getDimension(), record.getObjectDigest());
    }

    @Test
    public void testListenerDispatchesToTypedHandler() {
        AtomicReference<SmartRedisLimiterTypedAuditRecord> received = new AtomicReference<>();
        SmartRedisLimiterAuditEventListener listener = new SmartRedisLimiterAuditEventListener(
                null, null,
                new io.github.surezzzzzz.sdk.audit.limiter.listener.SmartRedisLimiterAuditHandlerDispatcher(
                        Collections.<io.github.surezzzzzz.sdk.audit.limiter.handler.SmartRedisLimiterAuditHandler>emptyList()),
                Collections.singletonList(new SmartRedisLimiterTypedAuditHandler() {
                    @Override
                    public void handle(SmartRedisLimiterTypedAuditRecord record) {
                        received.set(record);
                    }

                    @Override
                    public String getName() {
                        return "capturing-handler";
                    }
                }));
        listener.onTypedManagementEvent(new SmartRedisLimiterTypedManagementEvent(this, payload(null)));
        assertEquals("mock-service", received.get().getServiceCode(), "类型化事件应分发到 Handler");
    }

    @Test
    public void testHandlerFailureDoesNotBreakOthers() {
        AtomicReference<SmartRedisLimiterTypedAuditRecord> received = new AtomicReference<>();
        SmartRedisLimiterAuditEventListener listener = new SmartRedisLimiterAuditEventListener(
                null, null,
                new io.github.surezzzzzz.sdk.audit.limiter.listener.SmartRedisLimiterAuditHandlerDispatcher(
                        Collections.<io.github.surezzzzzz.sdk.audit.limiter.handler.SmartRedisLimiterAuditHandler>emptyList()),
                java.util.Arrays.asList(
                        new SmartRedisLimiterTypedAuditHandler() {
                            @Override
                            public void handle(SmartRedisLimiterTypedAuditRecord record) {
                                throw new IllegalStateException("boom");
                            }
                        },
                        new SmartRedisLimiterTypedAuditHandler() {
                            @Override
                            public void handle(SmartRedisLimiterTypedAuditRecord record) {
                                received.set(record);
                            }
                        }));
        listener.onTypedManagementEvent(new SmartRedisLimiterTypedManagementEvent(this, payload(null)));
        assertEquals("mock-service", received.get().getServiceCode(), "前序 Handler 异常不得中断后续分发");
    }
}
