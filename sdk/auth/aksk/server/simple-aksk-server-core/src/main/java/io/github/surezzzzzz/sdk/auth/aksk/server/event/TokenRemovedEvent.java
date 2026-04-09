package io.github.surezzzzzz.sdk.auth.aksk.server.event;

import java.time.Instant;
import java.util.Set;

/**
 * Token Removed Event
 *
 * <p>Published when a token is removed by Spring Authorization Server internally.
 *
 * @author surezzzzzz
 */
public class TokenRemovedEvent extends AbstractTokenEvent {

    public TokenRemovedEvent(Object source,
                             String clientId, String clientType,
                             String userId, String username,
                             String tokenValue, Set<String> scopes,
                             Instant issuedAt, Instant expiresAt) {
        super(source, TokenEventType.REMOVED,
                clientId, clientType, userId, username,
                tokenValue, scopes, issuedAt, expiresAt);
    }
}
