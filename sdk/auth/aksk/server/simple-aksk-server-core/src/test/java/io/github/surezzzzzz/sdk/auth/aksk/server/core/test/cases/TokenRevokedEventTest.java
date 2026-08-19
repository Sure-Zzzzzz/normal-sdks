package io.github.surezzzzzz.sdk.auth.aksk.server.core.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.server.event.TokenEventCause;
import io.github.surezzzzzz.sdk.auth.aksk.server.event.TokenEventType;
import io.github.surezzzzzz.sdk.auth.aksk.server.event.TokenRevokedEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Token 撤销事件契约测试。
 *
 * @author surezzzzzz
 */
class TokenRevokedEventTest {

    @Test
    void shouldUseUnspecifiedCauseForLegacyConstructor() {
        TokenRevokedEvent event = new TokenRevokedEvent(this,
                "client-id", "platform", null, null,
                "token", Collections.singleton("read"),
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T01:00:00Z"));

        assertEquals(TokenEventType.REVOKED, event.getEventType());
        assertEquals(TokenEventCause.UNSPECIFIED, event.getCause());
    }

    @Test
    void shouldRetainExplicitRevocationCause() {
        TokenRevokedEvent event = new TokenRevokedEvent(this,
                TokenEventCause.APPLICATION_AUTHORIZATION_REPLACED,
                "client-id", "platform", null, null,
                "token", Collections.singleton("read"),
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T01:00:00Z"));

        assertEquals(TokenEventType.REVOKED, event.getEventType());
        assertEquals(TokenEventCause.APPLICATION_AUTHORIZATION_REPLACED, event.getCause());
    }
}
