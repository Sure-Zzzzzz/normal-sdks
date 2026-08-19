package io.github.surezzzzzz.sdk.auth.aksk.server.event;

import lombok.Getter;

import java.time.Instant;
import java.util.Set;

/**
 * Token 自省事件。
 *
 * <p>通过 {@code /oauth2/introspect} 自省 Token 时发布。
 *
 * @author surezzzzzz
 */
@Getter
public class TokenIntrospectedEvent extends AbstractTokenEvent {

    /**
     * 本次自省请求得到的即时有效性结论；仅对当前 {@code INTROSPECTED} 事件有效，
     * 不代表 Token 在后续时刻的永久状态。
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
