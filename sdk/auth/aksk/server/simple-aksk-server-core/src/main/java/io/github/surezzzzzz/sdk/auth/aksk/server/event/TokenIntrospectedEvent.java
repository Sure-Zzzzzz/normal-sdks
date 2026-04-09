package io.github.surezzzzzz.sdk.auth.aksk.server.event;

import lombok.Getter;

import java.time.Instant;
import java.util.Set;

/**
 * Token Introspected Event
 *
 * <p>Published when a token is introspected via {@code /oauth2/introspect}.
 *
 * @author surezzzzzz
 */
@Getter
public class TokenIntrospectedEvent extends AbstractTokenEvent {

    /**
     * token 是否有效
     */
    private final boolean active;

    public TokenIntrospectedEvent(Object source,
                                  String clientId, String clientType,
                                  String userId, String username,
                                  String tokenValue, Set<String> scopes,
                                  Instant issuedAt, Instant expiresAt,
                                  boolean active) {
        super(source, TokenEventType.INTROSPECTED,
                clientId, clientType, userId, username,
                tokenValue, scopes, issuedAt, expiresAt);
        this.active = active;
    }
}
