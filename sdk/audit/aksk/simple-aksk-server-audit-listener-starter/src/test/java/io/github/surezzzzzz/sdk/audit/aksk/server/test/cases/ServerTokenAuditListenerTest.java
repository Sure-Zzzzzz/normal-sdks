package io.github.surezzzzzz.sdk.audit.aksk.server.test.cases;

import io.github.surezzzzzz.sdk.audit.aksk.server.handler.ServerTokenAuditHandler;
import io.github.surezzzzzz.sdk.audit.aksk.server.listener.ServerTokenAuditEventListener;
import io.github.surezzzzzz.sdk.audit.aksk.server.model.ServerTokenAuditRecord;
import io.github.surezzzzzz.sdk.audit.aksk.server.test.ServerTokenAuditListenerTestApplication;
import io.github.surezzzzzz.sdk.audit.aksk.server.test.TestServerTokenAuditHandler;
import io.github.surezzzzzz.sdk.auth.aksk.server.event.*;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Server Token 审计监听器测试。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = ServerTokenAuditListenerTestApplication.class)
class ServerTokenAuditListenerTest {

    private static final Instant ISSUED_AT = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant EXPIRES_AT = Instant.parse("2026-01-01T01:00:00Z");

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private TestServerTokenAuditHandler testHandler;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        testHandler.reset();
    }

    @Test
    void shouldConsumeNonTransactionalEventAndOmitTokenValue() {
        eventPublisher.publishEvent(new TokenIssuedEvent(
                this, "client-id", "platform", null, null,
                "sensitive-value", Collections.singleton("read"), ISSUED_AT, EXPIRES_AT));

        assertEquals(1, testHandler.records.size());
        ServerTokenAuditRecord record = testHandler.records.get(0);
        assertEquals(TokenEventType.ISSUED, record.getEventType());
        assertEquals(TokenEventCause.UNSPECIFIED, record.getCause());
        assertEquals("client-id", record.getClientId());
        assertEquals(Collections.singleton("read"), record.getScopes());
        assertNull(record.getTokenValue());
        assertNull(record.getActive());
        assertNotNull(record.getEventTime());
    }

    @Test
    void shouldHandleCommittedTransactionOnly() {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(new TokenRevokedEvent(
                this, TokenEventCause.APPLICATION_AUTHORIZATION_REVOKED,
                "client-id", "platform", null, null,
                "sensitive-value", Collections.singleton("read"), ISSUED_AT, EXPIRES_AT)));

        assertEquals(1, testHandler.records.size());
        assertEquals(TokenEventCause.APPLICATION_AUTHORIZATION_REVOKED, testHandler.records.get(0).getCause());

        testHandler.reset();
        Boolean rollbackCompleted = transactionTemplate.execute(status -> {
            eventPublisher.publishEvent(new TokenRevokedEvent(
                    this, TokenEventCause.APPLICATION_AUTHORIZATION_REVOKED,
                    "client-id", "platform", null, null,
                    "sensitive-value", Collections.singleton("read"), ISSUED_AT, EXPIRES_AT));
            status.setRollbackOnly();
            return Boolean.TRUE;
        });
        assertTrue(Boolean.TRUE.equals(rollbackCompleted));

        assertTrue(testHandler.records.isEmpty());
    }

    @Test
    void shouldContinueToNextHandlerWhenHandlerFails() {
        List<ServerTokenAuditRecord> handledRecords = new ArrayList<ServerTokenAuditRecord>();
        ServerTokenAuditHandler failingHandler = record -> {
            throw new IllegalStateException("handler failed");
        };
        ServerTokenAuditHandler succeedingHandler = handledRecords::add;
        ServerTokenAuditEventListener listener = new ServerTokenAuditEventListener(
                Arrays.asList(failingHandler, succeedingHandler));

        listener.onTokenEvent(new TokenRevokedEvent(
                this, TokenEventCause.APPLICATION_AUTHORIZATION_REPLACED,
                "client-id", "platform", null, null,
                "sensitive-value", Collections.singleton("read"), ISSUED_AT, EXPIRES_AT));

        assertEquals(1, handledRecords.size());
        assertEquals(TokenEventCause.APPLICATION_AUTHORIZATION_REPLACED, handledRecords.get(0).getCause());
        assertNull(handledRecords.get(0).getTokenValue());
    }

    @Test
    void shouldRetainExplicitRevocationCauseAndOmitTokenValue() {
        eventPublisher.publishEvent(new TokenRevokedEvent(
                this, TokenEventCause.APPLICATION_AUTHORIZATION_REPLACED,
                "client-id", "user", "user-id", "username",
                "sensitive-value", Collections.singleton("read"), ISSUED_AT, EXPIRES_AT));

        assertEquals(1, testHandler.records.size());
        ServerTokenAuditRecord record = testHandler.records.get(0);
        assertEquals(TokenEventType.REVOKED, record.getEventType());
        assertEquals(TokenEventCause.APPLICATION_AUTHORIZATION_REPLACED, record.getCause());
        assertEquals("user-id", record.getUserId());
        assertEquals("username", record.getUsername());
        assertNull(record.getTokenValue());
        assertNull(record.getActive());
    }

    @Test
    void shouldRetainIntrospectionActiveStateAndOmitTokenValue() {
        eventPublisher.publishEvent(new TokenIntrospectedEvent(
                this, "client-id", "platform", null, null,
                "sensitive-value", Collections.singleton("read"), ISSUED_AT, EXPIRES_AT, false));

        log.info("验证无效 Token 自省记录仅保留 active=false 与非敏感元数据");
        assertEquals(1, testHandler.records.size());
        ServerTokenAuditRecord record = testHandler.records.get(0);
        assertEquals(TokenEventType.INTROSPECTED, record.getEventType());
        assertEquals(TokenEventCause.UNSPECIFIED, record.getCause());
        assertFalse(record.getActive());
        assertNull(record.getTokenValue());
    }

    @Test
    void shouldConsumeRemovedEventAndOmitTokenValue() {
        eventPublisher.publishEvent(new TokenRemovedEvent(
                this, "client-id", "platform", null, null,
                "sensitive-value", Collections.singleton("read"), ISSUED_AT, EXPIRES_AT));

        log.info("验证删除事件保留生命周期类型且不下发 Token 原文");
        assertEquals(1, testHandler.records.size());
        ServerTokenAuditRecord record = testHandler.records.get(0);
        assertEquals(TokenEventType.REMOVED, record.getEventType());
        assertEquals(TokenEventCause.UNSPECIFIED, record.getCause());
        assertNull(record.getActive());
        assertNull(record.getTokenValue());
    }

    @Test
    void shouldPreserveEveryRevocationCauseAndOmitTokenValue() {
        for (TokenEventCause cause : TokenEventCause.values()) {
            testHandler.reset();
            eventPublisher.publishEvent(new TokenRevokedEvent(
                    this, cause, "client-id", "platform", null, null,
                    "sensitive-value", Collections.singleton("read"), ISSUED_AT, EXPIRES_AT));

            assertEquals(1, testHandler.records.size(), "每个撤销原因都应产生一条审计记录");
            ServerTokenAuditRecord record = testHandler.records.get(0);
            assertEquals(TokenEventType.REVOKED, record.getEventType());
            assertEquals(cause, record.getCause(), "审计监听器不得转换撤销原因");
            assertNull(record.getTokenValue(), "审计记录不得包含 Token 原文");
            assertNull(record.getActive(), "撤销事件不应携带自省状态");
        }
        log.info("验证全部撤销原因均按 Core 契约原样传递且不下发 Token 原文");
    }
}
