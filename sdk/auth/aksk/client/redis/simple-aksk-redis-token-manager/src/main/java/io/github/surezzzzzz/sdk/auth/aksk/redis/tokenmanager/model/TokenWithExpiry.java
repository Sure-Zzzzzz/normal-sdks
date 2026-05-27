package io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Token 及其元数据
 *
 * <p>包含 token、expiresAt、securityContext，随 token 一起存 Redis。
 * reload() 时从 Redis 读取 securityContext，保证分布式一致性。
 *
 * <p>需要 @NoArgsConstructor 用于 Jackson 反序列化
 *
 * @author surezzzzzz
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class TokenWithExpiry {
    private String token;
    /**
     * Token 绝对过期时间（epoch 秒），由 fetchTime + expiresIn 计算得出。
     * 用于 preload 时计算剩余 TTL，以及展示/审计。
     */
    private long expiresAt;
    private String securityContext;
}