package io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.strategy;

import io.github.surezzzzzz.sdk.auth.aksk.client.core.strategy.TokenCacheStrategy;
import io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.annotation.SimpleAkskRedisTokenManagerComponent;
import io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.support.RedisKeyHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;

import java.util.concurrent.TimeUnit;

/**
 * 基于 Redis 的 Token 缓存策略
 * <p>
 * 优点：
 * <ul>
 *   <li>多副本共享缓存</li>
 *   <li>自动过期</li>
 *   <li>高性能</li>
 * </ul>
 * <p>
 * 缺点：
 * <ul>
 *   <li>需要 Redis 依赖</li>
 * </ul>
 * <p>
 * 特点：
 * <ul>
 *   <li>使用专用的 StringRedisTemplate（避免序列化冲突）</li>
 *   <li>支持多应用隔离（通过 me 参数）</li>
 *   <li>异常优雅降级（返回 null，触发重新换 Token）</li>
 * </ul>
 *
 * @author surezzzzzz
 */
@SimpleAkskRedisTokenManagerComponent
@Slf4j
@RequiredArgsConstructor
public class RedisTokenCacheStrategy implements TokenCacheStrategy {

    private final StringRedisTemplate stringRedisTemplate;
    private final RedisKeyHelper redisKeyHelper;

    @Override
    public String generateCacheKey(String securityContext) {
        if (!StringUtils.hasText(securityContext)) {
            return redisKeyHelper.buildDefaultTokenKey();
        }
        int hash = securityContext.hashCode();
        return redisKeyHelper.buildTokenKey(hash);
    }

    @Override
    public String get(String cacheKey) {
        try {
            String value = stringRedisTemplate.opsForValue().get(cacheKey);
            if (value != null) {
                log.debug("Token cache hit in Redis: key={}", cacheKey);
            } else {
                log.debug("Token cache miss in Redis: key={}", cacheKey);
            }
            return value;
        } catch (Exception e) {
            log.error("Failed to get token from Redis, key: , fallback to re-fetch", cacheKey, e);
            return null;  // 降级：返回 null，触发重新换 Token
        }
    }

    @Override
    public void put(String cacheKey, String token, long expiresInSeconds) {
        try {
            // 提前 30 秒过期（避免边界情况）
            long ttl = Math.max(expiresInSeconds - 30, 60);
            stringRedisTemplate.opsForValue().set(cacheKey, token, ttl, TimeUnit.SECONDS);
            log.debug("Token cached in Redis: key={}, ttl={}s", cacheKey, ttl);
        } catch (Exception e) {
            log.error("Failed to put token to Redis, key: {}", cacheKey, e);
            // 不抛异常，继续执行（Token 仍然可用，只是没有缓存）
        }
    }

    @Override
    public void remove(String cacheKey) {
        try {
            stringRedisTemplate.delete(cacheKey);
            log.debug("Token removed from Redis: key={}", cacheKey);
        } catch (Exception e) {
            log.warn("Failed to remove token from Redis, key: {}", cacheKey, e);
        }
    }
}
