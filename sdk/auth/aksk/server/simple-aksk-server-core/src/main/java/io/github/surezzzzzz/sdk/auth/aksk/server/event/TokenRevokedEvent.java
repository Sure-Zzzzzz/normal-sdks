package io.github.surezzzzzz.sdk.auth.aksk.server.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.time.Instant;

/**
 * Token Revoked Event
 *
 * <p>token 被撤销时由 server-starter 发布，revocation-starter 监听后写 Redis 黑名单。
 *
 * @author surezzzzzz
 */
@Getter
public class TokenRevokedEvent extends ApplicationEvent {

    /**
     * 被撤销的 token 原始值
     */
    private final String tokenValue;

    /**
     * token 过期时间，用于计算黑名单 TTL
     */
    private final Instant expiresAt;

    public TokenRevokedEvent(Object source, String tokenValue, Instant expiresAt) {
        super(source);
        this.tokenValue = tokenValue;
        this.expiresAt = expiresAt;
    }
}
