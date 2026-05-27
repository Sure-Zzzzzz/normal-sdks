package io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.preload;

import io.github.surezzzzzz.sdk.auth.aksk.client.core.executor.TokenRefreshExecutor;
import io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.annotation.SimpleAkskRedisTokenManagerComponent;
import io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.configuration.SimpleAkskRedisTokenManagerProperties;
import io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.model.TokenWithExpiry;
import io.github.surezzzzzz.sdk.cache.CachePreloadHandler;
import io.github.surezzzzzz.sdk.cache.configuration.SmartCacheProperties;
import io.github.surezzzzzz.sdk.cache.manager.SmartCacheManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

import java.util.Optional;

/**
 * Token Cache Preload Handler
 *
 * <p>接入 smart-cache 的 L2 预刷新机制。
 * 通过实现 {@link CachePreloadHandler}，在 Redis TTL 降至 beforeExpireSeconds 时触发异步刷新。
 *
 * <p>reload() 从 Redis 读取当前 TokenWithExpiry，提取 securityContext 后向 server 换取新 token，
 * 保证分布式多实例使用相同的 securityContext。
 *
 * @author surezzzzzz
 */
@SimpleAkskRedisTokenManagerComponent
@Slf4j
public class TokenCachePreloadHandler implements CachePreloadHandler {

    private final TokenRefreshExecutor tokenRefreshExecutor;
    private final SimpleAkskRedisTokenManagerProperties properties;
    private final SmartCacheProperties smartCacheProperties;
    private SmartCacheManager cacheManager;

    @Autowired
    public TokenCachePreloadHandler(
            TokenRefreshExecutor tokenRefreshExecutor,
            SimpleAkskRedisTokenManagerProperties properties,
            SmartCacheProperties smartCacheProperties,
            @Lazy SmartCacheManager cacheManager) {
        this.tokenRefreshExecutor = tokenRefreshExecutor;
        this.properties = properties;
        this.smartCacheProperties = smartCacheProperties;
        this.cacheManager = cacheManager;
    }

    /**
     * 判断是否由本 Handler 处理该缓存的预刷新
     *
     * @param cacheName 缓存名称
     * @return 仅当 cacheName 等于配置的值时才处理
     */
    @Override
    public boolean support(String cacheName) {
        return properties.getRedis().getToken().getCacheName().equals(cacheName);
    }

    /**
     * 判断是否需要预刷新
     *
     * <p>2.0.0 由框架根据 Redis TTL 自动判断，此方法返回 {@code Optional.empty()} 交由框架决定。
     *
     * @param cacheName 缓存名称
     * @param key       缓存键
     * @param value     当前缓存值（可能为 null）
     * @return 始终返回 empty，由框架 TTL 机制决定是否 preload
     */
    @Override
    public Optional<Boolean> needPreload(String cacheName, String key, Object value) {
        return Optional.empty();
    }

    /**
     * 获取预刷新后写入缓存的 TTL（秒）
     *
     * <p>2.0.0 返回 0，表示 TTL 在 reload() 中由 expiresAt 动态计算，框架不干预。
     *
     * @param cacheName 缓存名称
     * @param key       缓存键
     * @return 0，TTL 由 reload() 内的 put() 调用自行计算
     */
    @Override
    public int getReloadTtlSeconds(String cacheName, String key) {
        return 0;
    }

    /**
     * 重新加载 Token
     *
     * <p>从 Redis 读取当前缓存值（包含 securityContext），向 OAuth2 Server 换取新 token。
     * 由框架保证：此方法仅在 L2 存在有效值（TTL > 0）时触发，securityContext 必定可读。
     *
     * @param cacheName 缓存名称
     * @param key       缓存键
     * @return 新 TokenWithExpiry，写入 L2；返回 null 时不更新缓存
     */
    @Override
    public Object reload(String cacheName, String key) {
        // 从 Redis 读取当前缓存值，提取 securityContext，保证分布式一致性
        TokenWithExpiry current = cacheManager.get(cacheName, key);
        String securityContext = current != null ? current.getSecurityContext() : null;

        log.info("Preloading token: key={}", key);

        long fetchTime = System.currentTimeMillis() / 1000;
        TokenWithExpiry[] holder = new TokenWithExpiry[1];
        tokenRefreshExecutor.fetchTokenFromServer(securityContext, (token, expiresIn) -> {
            holder[0] = new TokenWithExpiry(token, fetchTime + expiresIn, securityContext);
        });

        if (holder[0] != null) {
            int ttl = (int) (holder[0].getExpiresAt() - System.currentTimeMillis() / 1000);
            if (ttl <= 0) {
                ttl = smartCacheProperties.getL2().getExpireSeconds();
            }
            cacheManager.put(cacheName, key, holder[0], ttl);
            return holder[0];
        }
        return null;
    }
}