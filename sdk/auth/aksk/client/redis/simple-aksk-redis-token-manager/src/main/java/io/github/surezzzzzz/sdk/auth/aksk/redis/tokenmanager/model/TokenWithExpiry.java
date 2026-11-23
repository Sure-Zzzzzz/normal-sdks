package io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Token 及其元数据
 *
 * <p>包含 token、expiresAt、securityContext，随 token 一起存 Redis。
 * reload() 时从 Redis 读取 securityContext，保证分布式一致性。
 *
 * <p>需要 @NoArgsConstructor + @Setter 用于 Jackson 反序列化（smart-cache 2.x 的
 * 类型化读取走 ObjectMapper.convertValue，依赖可变 POJO，与其 SmartCacheSerializerTest
 * 的 DTO 约定一致）
 *
 * @author surezzzzzz
 */
@Getter
@Setter
@NoArgsConstructor
public class TokenWithExpiry {
    /**
     * AccessToken 字符串
     */
    private String token;
    /**
     * Token 绝对过期时间（epoch 秒），由 fetchTime + expiresIn 计算得出。
     * 用于 preload 时计算剩余 TTL，以及展示/审计。
     */
    private long expiresAt;
    /**
     * 安全上下文（JSON 字符串或 null）。
     * 随 token 一起存 Redis，保证 preload reload() 时分布式各实例使用相同 securityContext。
     */
    private String securityContext;

    /**
     * Profile 名称；旧缓存值为 null 时只允许默认链路使用
     */
    private String profile;

    /**
     * 凭据身份哈希；与缓存 key 和 Profile Registry 使用同一算法
     */
    private String credentialHash;

    public TokenWithExpiry(String token, long expiresAt, String securityContext) {
        this.token = token;
        this.expiresAt = expiresAt;
        this.securityContext = securityContext;
    }

    /**
     * 创建带Profile元数据的缓存值
     *
     * @param token           访问令牌
     * @param expiresAt       过期时间（epoch秒）
     * @param securityContext 安全上下文，可为null
     * @param profile         Profile名称
     * @param credentialHash  凭据身份哈希，不含secret与clientId明文
     */
    public TokenWithExpiry(String token, long expiresAt, String securityContext,
                           String profile, String credentialHash) {
        this.token = token;
        this.expiresAt = expiresAt;
        this.securityContext = securityContext;
        this.profile = profile;
        this.credentialHash = credentialHash;
    }
}
