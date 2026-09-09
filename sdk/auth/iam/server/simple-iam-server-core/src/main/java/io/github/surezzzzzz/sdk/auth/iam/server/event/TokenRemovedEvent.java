package io.github.surezzzzzz.sdk.auth.iam.server.event;

import java.time.Instant;
import java.util.Set;

/**
 * IAM Token 删除事件（授权记录物理移除时发布）。
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
