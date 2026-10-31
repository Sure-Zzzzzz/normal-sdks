package io.github.surezzzzzz.sdk.audit.license.test.cases;

import io.github.surezzzzzz.sdk.audit.license.constant.SmartLicenseAuditConstant;
import io.github.surezzzzzz.sdk.audit.license.listener.LicenseAuditEventListener;
import io.github.surezzzzzz.sdk.audit.license.model.LicenseAuditRecord;
import io.github.surezzzzzz.sdk.audit.license.test.LicenseAuditListenerTestApplication;
import io.github.surezzzzzz.sdk.audit.license.test.TestLicenseAuditHandler;
import io.github.surezzzzzz.sdk.audit.license.test.TestLicenseAuditTraceIdProvider;
import io.github.surezzzzzz.sdk.license.server.event.LicenseAuditEvents;
import io.github.surezzzzzz.sdk.license.server.event.RevocationPackageCompleted;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * License 审计监听器端到端测试：同 JVM 发布稳定领域事件，
 * 验证审计记录投影与链路追踪注入。
 *
 * @author surezzzzzz
 * @since 2.0.0
 */
@Slf4j
@SpringBootTest(classes = LicenseAuditListenerTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class LicenseAuditListenerEndToEndTest {

    private static final String PRINCIPAL_ID = "iam:zhang-san";
    private static final String REQUEST_ID = "request-example";
    private static final String GRANT_ID = "gr-example-001";
    private static final String CUSTOMER_ID = "cu-example";
    private static final String PRODUCT_ID = "prod-example";
    private static final String CREDENTIAL_ID = "cred-example-001";
    private static final String KID = "lic-example-es256-v1";
    private static final String TRACE_ID = "trace-example-001";
    private static final long AWAIT_SECONDS = 5L;

    @Autowired
    private ApplicationEventPublisher eventPublisher;
    @Autowired
    private TestLicenseAuditHandler testAuditHandler;
    @Autowired
    private TestLicenseAuditTraceIdProvider traceIdProvider;
    @Autowired
    private ApplicationContext applicationContext;

    /**
     * 重置观测状态，保持用例互不影响。
     */
    @BeforeEach
    void resetObservations() {
        testAuditHandler.reset();
        traceIdProvider.reset();
        traceIdProvider.nextTraceId = TRACE_ID;
    }

    @Test
    void listenerRegistersWithHandlerBeanPresent() {
        assertNotNull(applicationContext.getBean(LicenseAuditEventListener.class),
                "宿主注册 LicenseAuditHandler 时监听器必须注册");
    }

    @Test
    void shouldProjectGrantCreatedEventIntoAuditRecord() throws Exception {
        eventPublisher.publishEvent(new LicenseAuditEvents.GrantCreated(
                PRINCIPAL_ID, REQUEST_ID, GRANT_ID, CUSTOMER_ID, PRODUCT_ID,
                "PERSON", "ONLINE", "mv-example"));

        assertTrue(testAuditHandler.latch.await(AWAIT_SECONDS, TimeUnit.SECONDS), "授权创建事件必须到达审计处理器");
        LicenseAuditRecord record = testAuditHandler.records.get(0);
        assertEquals(SmartLicenseAuditConstant.EVENT_TYPE_GRANT_CREATED, record.getEventType());
        assertEquals(GRANT_ID, record.getGrantId());
        assertEquals(CUSTOMER_ID, record.getCustomerId());
        assertEquals(PRODUCT_ID, record.getProductId());
        assertEquals("PERSON", record.getSubjectKind());
        assertEquals("ONLINE", record.getMode());
        assertEquals("mv-example", record.getModelVersionId());
        assertEquals(PRINCIPAL_ID, record.getPrincipalId());
        assertEquals(TRACE_ID, record.getTraceId());
    }

    @Test
    void shouldProjectGrantStateChangedEvent() throws Exception {
        eventPublisher.publishEvent(new LicenseAuditEvents.GrantStateChanged(
                PRINCIPAL_ID, REQUEST_ID, GRANT_ID, "ENABLED", "SUSPENDED"));

        assertTrue(testAuditHandler.latch.await(AWAIT_SECONDS, TimeUnit.SECONDS), "状态变更事件必须到达审计处理器");
        LicenseAuditRecord record = testAuditHandler.records.get(0);
        assertEquals(SmartLicenseAuditConstant.EVENT_TYPE_GRANT_STATE_CHANGED, record.getEventType());
        assertEquals("ENABLED", record.getFromState());
        assertEquals("SUSPENDED", record.getToState());
    }

    @Test
    void shouldProjectCredentialIssuedEventWithoutSecrets() throws Exception {
        eventPublisher.publishEvent(new LicenseAuditEvents.CredentialIssued(
                PRINCIPAL_ID, REQUEST_ID, GRANT_ID, CREDENTIAL_ID, KID));

        assertTrue(testAuditHandler.latch.await(AWAIT_SECONDS, TimeUnit.SECONDS), "签发就绪事件必须到达审计处理器");
        LicenseAuditRecord record = testAuditHandler.records.get(0);
        assertEquals(SmartLicenseAuditConstant.EVENT_TYPE_CREDENTIAL_ISSUED, record.getEventType());
        assertEquals(CREDENTIAL_ID, record.getCredentialId());
        assertEquals(KID, record.getKid());
        assertEquals(Integer.valueOf(LicenseAuditEvents.EVENT_VERSION), record.getEventVersion());
    }

    @Test
    void shouldProjectRevocationCreatedEvent() throws Exception {
        eventPublisher.publishEvent(new LicenseAuditEvents.RevocationCreated(
                PRINCIPAL_ID, REQUEST_ID, "rv-example", "GRANT", GRANT_ID, "CONTRACT_END"));

        assertTrue(testAuditHandler.latch.await(AWAIT_SECONDS, TimeUnit.SECONDS), "吊销创建事件必须到达审计处理器");
        LicenseAuditRecord record = testAuditHandler.records.get(0);
        assertEquals(SmartLicenseAuditConstant.EVENT_TYPE_REVOCATION_CREATED, record.getEventType());
        assertEquals("GRANT", record.getTargetKind());
        assertEquals(GRANT_ID, record.getTargetId());
        assertEquals("CONTRACT_END", record.getReasonCategory());
    }

    @Test
    void shouldObserveCommittedQuotaWithoutInventingAnotherConsumption() throws Exception {
        eventPublisher.publishEvent(new io.github.surezzzzzz.sdk.license.server.event.QuotaOperationCompleted(
                PRINCIPAL_ID, REQUEST_ID, GRANT_ID, "reservation-example", "CONSUMPTION", "ACTIVE", 17L,
                io.github.surezzzzzz.sdk.license.server.event.QuotaOperationCompleted.Operation.RESERVATION_OBSERVED));
        assertTrue(testAuditHandler.latch.await(AWAIT_SECONDS, TimeUnit.SECONDS), "额度终态观察必须到达审计处理器");
        LicenseAuditRecord record = testAuditHandler.records.get(0);
        assertEquals(SmartLicenseAuditConstant.EVENT_TYPE_QUOTA_OPERATION_COMPLETED, record.getEventType());
        assertEquals("reservation-example", record.getReservationId());
        assertEquals("CONSUMPTION", record.getQuotaKind());
        assertEquals("ACTIVE", record.getToState());
        assertEquals(Long.valueOf(17L), record.getReservationFence());
        assertEquals("RESERVATION_OBSERVED", record.getQuotaOperation());
        assertEquals(PRINCIPAL_ID, record.getPrincipalId());
        assertEquals(REQUEST_ID, record.getRequestId());
        assertNull(record.getCredentialId());
        assertNull(record.getKid());
    }

    @Test
    void shouldProjectReadyRevocationPackageIntoDedicatedSafeFields() throws Exception {
        assertRevocationPackageAudit("READY", null);
    }

    @Test
    void shouldProjectFailedRevocationPackageWithoutCredentialsOrTargets() throws Exception {
        assertRevocationPackageAudit("FAILED", "IllegalStateException");
    }

    /** 两种终态复用投影断言；事件元数据精确透传，安全字段保持缺省。 */
    private void assertRevocationPackageAudit(String state, String reasonCategory) throws Exception {
        RevocationPackageCompleted event = new RevocationPackageCompleted(
                "rp-example", "rop-example", PRODUCT_ID, 23L, state, reasonCategory);
        eventPublisher.publishEvent(event);
        assertTrue(testAuditHandler.latch.await(AWAIT_SECONDS, TimeUnit.SECONDS), "吊销包终态必须到达审计处理器");
        LicenseAuditRecord record = testAuditHandler.records.get(0);
        assertEquals(SmartLicenseAuditConstant.EVENT_TYPE_REVOCATION_PACKAGE_COMPLETED, record.getEventType());
        assertEquals(event.getEventId(), record.getEventId());
        assertEquals(Integer.valueOf(event.getEventVersion()), record.getEventVersion());
        assertEquals(Long.valueOf(event.getOccurredAt().toEpochMilli()), record.getTimestamp());
        assertEquals("rp-example", record.getPackageId());
        assertEquals("rop-example", record.getOperationId());
        assertEquals(PRODUCT_ID, record.getProductId());
        assertEquals(Long.valueOf(23L), record.getPackageSequence());
        assertEquals(state, record.getToState());
        assertEquals(reasonCategory, record.getReasonCategory());
        assertEquals(TRACE_ID, record.getTraceId());
        assertNull(record.getPrincipalId());
        assertNull(record.getRequestId());
        assertNull(record.getGrantId());
        assertNull(record.getCredentialId());
        assertNull(record.getKid());
        assertNull(record.getTargetId());
        assertNull(record.getTargetKind());
    }

    @Test
    void shouldProjectModelPublishedEvent() throws Exception {
        eventPublisher.publishEvent(new LicenseAuditEvents.ModelPublished(
                PRINCIPAL_ID, REQUEST_ID, "mv-example", PRODUCT_ID, 3L));

        assertTrue(testAuditHandler.latch.await(AWAIT_SECONDS, TimeUnit.SECONDS), "模型发布事件必须到达审计处理器");
        LicenseAuditRecord record = testAuditHandler.records.get(0);
        assertEquals(SmartLicenseAuditConstant.EVENT_TYPE_MODEL_PUBLISHED, record.getEventType());
        assertEquals(Long.valueOf(3L), record.getModelVersion());
        assertEquals(PRODUCT_ID, record.getProductId());
    }
}
