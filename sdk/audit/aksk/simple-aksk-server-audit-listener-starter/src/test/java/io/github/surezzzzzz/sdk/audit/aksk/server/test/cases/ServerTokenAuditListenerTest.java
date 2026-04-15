package io.github.surezzzzzz.sdk.audit.aksk.server.test.cases;

import io.github.surezzzzzz.sdk.audit.aksk.server.model.ServerTokenAuditRecord;
import io.github.surezzzzzz.sdk.audit.aksk.server.test.ServerTokenAuditListenerTestApplication;
import io.github.surezzzzzz.sdk.audit.aksk.server.test.TestServerTokenAuditHandler;
import io.github.surezzzzzz.sdk.auth.aksk.server.event.TokenEventType;
import io.github.surezzzzzz.sdk.auth.aksk.server.event.TokenIntrospectedEvent;
import io.github.surezzzzzz.sdk.auth.aksk.server.event.TokenIssuedEvent;
import io.github.surezzzzzz.sdk.auth.aksk.server.event.TokenRevokedEvent;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Server Token 审计监听器测试
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@Slf4j
@SpringBootTest(classes = ServerTokenAuditListenerTestApplication.class)
public class ServerTokenAuditListenerTest {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private TestServerTokenAuditHandler testHandler;

    private static final Instant ISSUED_AT = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant EXPIRES_AT = Instant.parse("2026-01-01T01:00:00Z");

    @BeforeEach
    public void setUp() {
        testHandler.reset();
    }

    @Test
    public void testIssuedEvent() throws InterruptedException {
        log.info("========== 测试：TokenIssuedEvent ==========");

        Set<String> scopes = new HashSet<>(Arrays.asList("read", "write"));
        eventPublisher.publishEvent(new TokenIssuedEvent(
                this,
                "AKP-platform-client", "platform",
                null, null,
                "token-value-issued",
                scopes,
                ISSUED_AT, EXPIRES_AT
        ));

        boolean received = testHandler.latch.await(5, TimeUnit.SECONDS);
        assertTrue(received, "Handler should receive the event");

        ServerTokenAuditRecord record = testHandler.records.get(0);
        assertEquals(TokenEventType.ISSUED, record.getEventType());
        assertEquals("AKP-platform-client", record.getClientId());
        assertEquals("platform", record.getClientType());
        assertNull(record.getUserId());
        assertNull(record.getUsername());
        assertEquals("token-value-issued", record.getTokenValue());
        assertTrue(record.getScopes().containsAll(scopes));
        assertEquals(ISSUED_AT, record.getIssuedAt());
        assertEquals(EXPIRES_AT, record.getExpiresAt());
        assertNull(record.getActive(), "active should be null for non-introspect events");
        assertNotNull(record.getEventTime());

        log.info("testIssuedEvent passed: {}", record);
    }

    @Test
    public void testRevokedEvent() throws InterruptedException {
        log.info("========== 测试：TokenRevokedEvent（user 级） ==========");

        eventPublisher.publishEvent(new TokenRevokedEvent(
                this,
                "AKU-user-client", "user",
                "user-123", "testuser",
                "token-value-revoked",
                new HashSet<>(Arrays.asList("read")),
                ISSUED_AT, EXPIRES_AT
        ));

        boolean received = testHandler.latch.await(5, TimeUnit.SECONDS);
        assertTrue(received);

        ServerTokenAuditRecord record = testHandler.records.get(0);
        assertEquals(TokenEventType.REVOKED, record.getEventType());
        assertEquals("AKU-user-client", record.getClientId());
        assertEquals("user", record.getClientType());
        assertEquals("user-123", record.getUserId());
        assertEquals("testuser", record.getUsername());
        assertNull(record.getActive());

        log.info("testRevokedEvent passed: {}", record);
    }

    @Test
    public void testIntrospectedActiveEvent() throws InterruptedException {
        log.info("========== 测试：TokenIntrospectedEvent（active=true） ==========");

        eventPublisher.publishEvent(new TokenIntrospectedEvent(
                this,
                "AKP-platform-client", "platform",
                null, null,
                "token-value-active",
                new HashSet<>(Arrays.asList("read")),
                ISSUED_AT, EXPIRES_AT,
                true
        ));

        boolean received = testHandler.latch.await(5, TimeUnit.SECONDS);
        assertTrue(received);

        ServerTokenAuditRecord record = testHandler.records.get(0);
        assertEquals(TokenEventType.INTROSPECTED, record.getEventType());
        assertTrue(record.getActive(), "active should be true");

        log.info("testIntrospectedActiveEvent passed: {}", record);
    }

    @Test
    public void testIntrospectedInactiveEvent() throws InterruptedException {
        log.info("========== 测试：TokenIntrospectedEvent（active=false，token 已失效） ==========");

        eventPublisher.publishEvent(new TokenIntrospectedEvent(
                this,
                "AKP-platform-client", "platform",
                null, null,
                "token-value-expired",
                new HashSet<>(Arrays.asList("read")),
                ISSUED_AT, EXPIRES_AT,
                false
        ));

        boolean received = testHandler.latch.await(5, TimeUnit.SECONDS);
        assertTrue(received);

        ServerTokenAuditRecord record = testHandler.records.get(0);
        assertEquals(TokenEventType.INTROSPECTED, record.getEventType());
        assertFalse(record.getActive(), "active should be false for expired token");

        log.info("testIntrospectedInactiveEvent passed: {}", record);
    }
}
