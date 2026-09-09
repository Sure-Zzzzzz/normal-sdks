package io.github.surezzzzzz.sdk.auth.iam.server.event;

import java.time.Instant;
import java.util.Set;

/**
 * IAM Token 颁发事件（/oauth2/token 签发新增授权记录时发布）。
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
