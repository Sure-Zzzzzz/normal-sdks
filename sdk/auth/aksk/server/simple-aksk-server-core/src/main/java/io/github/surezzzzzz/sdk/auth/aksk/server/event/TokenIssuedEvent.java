package io.github.surezzzzzz.sdk.auth.aksk.server.event;

import java.time.Instant;
import java.util.Set;

/**
 * Token 签发事件。
 *
 * <p>通过 {@code /oauth2/token} 成功签发新 Token 时发布。
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
