package io.github.surezzzzzz.sdk.auth.iam.server.event;

import java.time.Instant;
import java.util.Set;

/**
 * IAM Token 撤销事件（revoke 端点或授权记录失效标记时发布）。
 *
 * @author surezzzzzz
 */
public class TokenRevokedEvent extends AbstractTokenEvent {

    public TokenRevokedEvent(Object source, TokenEventCause cause,
                             String clientId, String clientType,
                             String userId, String username,
                             String tokenValue, Set<String> scopes,
                             Instant issuedAt, Instant expiresAt) {
        super(source, TokenEventType.REVOKED, cause,
                clientId, clientType, userId, username,
                tokenValue, scopes, issuedAt, expiresAt);
    }
}
