package io.github.surezzzzzz.sdk.auth.aksk.server.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.time.Instant;
import java.util.Set;

/**
 * Abstract Token Event
 *
 * <p>Base class for all token lifecycle events.
 *
 * @author surezzzzzz
 */
@Getter
public abstract class AbstractTokenEvent extends ApplicationEvent {

    private final TokenEventType eventType;
    private final Instant eventTime;

    // ==================== 客户端信息 ====================

    private final String clientId;
    private final String clientType;
    private final String userId;
    private final String username;

    // ==================== Token 信息 ====================

    private final String tokenValue;
    private final Set<String> scopes;
    private final Instant issuedAt;
    private final Instant expiresAt;

    protected AbstractTokenEvent(Object source, TokenEventType eventType,
                                 String clientId, String clientType,
                                 String userId, String username,
                                 String tokenValue, Set<String> scopes,
                                 Instant issuedAt, Instant expiresAt) {
        super(source);
        this.eventType = eventType;
        this.eventTime = Instant.now();
        this.clientId = clientId;
        this.clientType = clientType;
        this.userId = userId;
        this.username = username;
        this.tokenValue = tokenValue;
        this.scopes = scopes;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
    }
}
