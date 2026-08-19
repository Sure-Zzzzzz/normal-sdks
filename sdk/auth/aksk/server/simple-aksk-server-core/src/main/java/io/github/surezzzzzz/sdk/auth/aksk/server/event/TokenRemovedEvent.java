package io.github.surezzzzzz.sdk.auth.aksk.server.event;

import java.time.Instant;
import java.util.Set;

/**
 * Token 删除事件。
 *
 * <p>Spring Authorization Server 内部删除 Token 时发布。
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
