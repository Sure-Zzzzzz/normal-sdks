package io.github.surezzzzzz.sdk.auth.aksk.server.event;

import java.time.Instant;
import java.util.Set;

/**
 * Token Issued Event
 *
 * <p>Published when a new token is successfully issued via {@code /oauth2/token}.
 *
 * @author surezzzzzz
 */
public class TokenIssuedEvent extends AbstractTokenEvent {

    public TokenIssuedEvent(Object source,
                            String clientId, String clientType,
                            String userId, String username,
                            String tokenValue, Set<String> scopes,
                            Instant issuedAt, Instant expiresAt) {
        super(source, TokenEventType.ISSUED,
                clientId, clientType, userId, username,
                tokenValue, scopes, issuedAt, expiresAt);
    }
}
