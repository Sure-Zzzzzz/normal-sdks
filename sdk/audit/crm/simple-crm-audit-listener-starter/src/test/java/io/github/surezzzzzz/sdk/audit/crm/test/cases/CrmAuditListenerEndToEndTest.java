package io.github.surezzzzzz.sdk.audit.crm.test.cases;

import io.github.surezzzzzz.sdk.audit.crm.listener.CrmAuditEventListener;
import io.github.surezzzzzz.sdk.audit.crm.model.CrmAuditRecord;
import io.github.surezzzzzz.sdk.audit.crm.test.CrmAuditListenerTestApplication;
import io.github.surezzzzzz.sdk.audit.crm.test.FailingCrmAuditHandler;
import io.github.surezzzzzz.sdk.audit.crm.test.TestCrmAuditHandler;
import io.github.surezzzzzz.sdk.audit.crm.test.TestCrmAuditTraceIdProvider;
import io.github.surezzzzzz.sdk.crm.server.core.domain.event.CrmAuditEvent;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CRM 审计监听器事件投影集成测试：契约事件必须完整投影为审计记录；
 * 单个处理器异常不影响其余处理器。本测试直接发布事件，仅覆盖 listener，
 * 不替代 server 的事务提交、回滚和幂等发布测试。
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@Slf4j
@SpringBootTest(classes = CrmAuditListenerTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import({TestCrmAuditHandler.class, FailingCrmAuditHandler.class, TestCrmAuditTraceIdProvider.class})
class CrmAuditListenerEndToEndTest {

    private static final String EVENT_ID = "evt-crm-example-001";

    private static final String OWNER = "iam:audit-example-owner";

    private static final String RESOURCE_ID = "customer-example-001";

    private static final String CORRELATION_ID = "corr-example-001";

    private static final Instant OCCURRED_AT = Instant.parse("2026-10-08T00:00:00Z");

    private static final long AWAIT_SECONDS = 5L;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private TestCrmAuditHandler testAuditHandler;

    @Autowired
    private ApplicationContext applicationContext;

    /**
     * 重置观测状态，保持用例互不影响。
     */
    @BeforeEach
    void resetObservations() {
        TestCrmAuditTraceIdProvider.setFail(false);
        FailingCrmAuditHandler.INVOCATIONS.set(0);
        testAuditHandler.reset();
    }

    @Test
    void shouldProjectAuditEventIntoAuditRecord() throws Exception {
        eventPublisher.publishEvent(new CrmAuditEvent(EVENT_ID, CrmAuditEvent.CURRENT_EVENT_VERSION,
                OWNER, "customer_create", "customer", RESOURCE_ID, 1L, CORRELATION_ID, null,
                OCCURRED_AT));

        assertTrue(testAuditHandler.latch.await(AWAIT_SECONDS, TimeUnit.SECONDS), "审计事件必须到达审计处理器");
        CrmAuditRecord record = testAuditHandler.records.get(0);
        log.info("验证审计投影：eventType={}，owner={}，resourceId={}", record.getEventType(),
                record.getOwner(), record.getResourceId());
        assertEquals("customer_create", record.getEventType(), "审计类型必须取自审计行动作码");
        assertEquals(EVENT_ID, record.getEventId(), "审计记录必须保留事件标识");
        assertEquals(Integer.valueOf(CrmAuditEvent.CURRENT_EVENT_VERSION), record.getEventVersion(),
                "审计记录必须保留事件版本");
        assertEquals(Long.valueOf(OCCURRED_AT.toEpochMilli()), record.getTimestamp(),
                "审计记录必须保留毫秒发生时间");
        assertEquals(OWNER, record.getOwner(), "审计记录必须保留归属坐标");
        assertEquals("customer", record.getResourceTypeCode(), "审计记录必须保留资源类型码");
        assertEquals(RESOURCE_ID, record.getResourceId(), "审计记录必须保留目标资源标识");
        assertEquals(Long.valueOf(1L), record.getAggregateVersion(), "审计记录必须保留聚合版本");
        assertEquals(CORRELATION_ID, record.getCorrelationId(), "审计记录必须保留命令关联标识");
        assertNull(record.getResultCode(), "成功审计不得携带结果码");
        assertEquals(TestCrmAuditTraceIdProvider.TRACE_ID, record.getTraceId(),
                "提供者在位时审计记录必须携带链路追踪ID");
    }

    @Test
    void shouldSurviveFailingHandlerAndStillReachOthers() throws Exception {
        assertNotNull(applicationContext.getBean(CrmAuditEventListener.class),
                "有处理器时监听器必须注册");
        eventPublisher.publishEvent(new CrmAuditEvent("evt-crm-example-002",
                CrmAuditEvent.CURRENT_EVENT_VERSION, OWNER, "quotation_confirm", "quotation",
                "quotation-example-002", 2L, "corr-example-002", null, OCCURRED_AT));

        assertTrue(testAuditHandler.latch.await(AWAIT_SECONDS, TimeUnit.SECONDS),
                "故障处理器不得阻断其余处理器");
        assertEquals(1, testAuditHandler.records.size(), "正常处理器必须收到且仅收到一条记录");
        assertEquals(1, FailingCrmAuditHandler.INVOCATIONS.get(), "故障处理器必须实际被调用");
        assertEquals("quotation_confirm", testAuditHandler.records.get(0).getEventType(),
                "审计类型必须取自审计行动作码");
    }

    @Test
    void shouldKeepAuditRecordWhenTraceProviderFails() throws Exception {
        TestCrmAuditTraceIdProvider.setFail(true);
        eventPublisher.publishEvent(new CrmAuditEvent("evt-crm-example-003",
                CrmAuditEvent.CURRENT_EVENT_VERSION, OWNER, "customer_update", "customer",
                RESOURCE_ID, 2L, CORRELATION_ID, null, OCCURRED_AT));

        assertTrue(testAuditHandler.latch.await(AWAIT_SECONDS, TimeUnit.SECONDS),
                "链路追踪扩展失败时审计仍必须到达处理器");
        assertNull(testAuditHandler.records.get(0).getTraceId(),
                "链路追踪扩展失败时应降级为空");
    }
}
