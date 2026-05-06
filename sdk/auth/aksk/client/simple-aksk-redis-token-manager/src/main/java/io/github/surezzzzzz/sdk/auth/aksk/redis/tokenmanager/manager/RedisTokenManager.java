package io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.manager;

import io.github.surezzzzzz.sdk.auth.aksk.client.core.executor.TokenRefreshExecutor;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.manager.TokenManager;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.provider.SecurityContextProvider;
import io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.annotation.SimpleAkskRedisTokenManagerComponent;
import io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.configuration.SimpleAkskRedisTokenManagerProperties;
import io.github.surezzzzzz.sdk.cache.manager.SmartCacheManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

/**
 * Redis Token Manager
 *
 * <p>基于 {@link SmartCacheManager} 的分布式 Token 管理器。
 *
 * <p>特性：
 * <ul>
 *   <li><b>L1 缓存</b>：JVM 本地缓存（Caffeine），TTL 短（默认 2s），减少 Redis IO</li>
 *   <li><b>L2 缓存</b>：Redis 分布式缓存，多实例共享 token</li>
 *   <li><b>分布式锁</b>：SmartCacheManager 内置，防止多实例并发打 OAuth2 Server</li>
 *   <li><b>多实例 L1 一致性</b>：clearToken() 通过 Pub/Sub 广播 L1 失效，各实例同步清除</li>
 *   <li><b>L2 预刷新</b>：由 {@link io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.preload.TokenCachePreloadHandler} 处理</li>
 * </ul>
 *
 * <p>缓存流程：查 L1 → 查 L2 → 加锁换 token → 写回 L1+L2
 *
 * @author surezzzzzz
 */
@SimpleAkskRedisTokenManagerComponent
@Slf4j
public class RedisTokenManager implements TokenManager {

    private final SecurityContextProvider securityContextProvider;
    private final SmartCacheManager cacheManager;
    private final SimpleAkskRedisTokenManagerProperties properties;
    private final TokenRefreshExecutor tokenRefreshExecutor;

    public RedisTokenManager(
            SecurityContextProvider securityContextProvider,
            SmartCacheManager cacheManager,
            SimpleAkskRedisTokenManagerProperties properties,
            TokenRefreshExecutor tokenRefreshExecutor) {
        this.securityContextProvider = securityContextProvider;
        this.cacheManager = cacheManager;
        this.properties = properties;
        this.tokenRefreshExecutor = tokenRefreshExecutor;
    }

    @Override
    public String getToken() {
        String securityContext = securityContextProvider.getSecurityContext();
        String cacheKey = generateCacheKey(securityContext);
        String cacheName = properties.getRedis().getToken().getCacheName();

        return cacheManager.get(cacheName, cacheKey, () -> {
            log.debug("Token cache miss, fetching from OAuth2 Server: key={}", cacheKey);
            return tokenRefreshExecutor.fetchTokenFromServer(securityContext, null);
        });
    }

    @Override
    public void clearToken() {
        String securityContext = securityContextProvider.getSecurityContext();
        String cacheKey = generateCacheKey(securityContext);
        String cacheName = properties.getRedis().getToken().getCacheName();
        // strong 模式下 evict 会通过 Pub/Sub 广播，各实例同步清除 L1
        cacheManager.evict(cacheName, cacheKey);
        log.debug("Token cleared: key={}", cacheKey);
    }

    /**
     * 生成缓存 Key
     *
     * @param securityContext 安全上下文（JSON 字符串或 null）
     * @return 缓存 Key
     */
    private String generateCacheKey(String securityContext) {
        return StringUtils.hasText(securityContext)
                ? String.valueOf(securityContext.hashCode())
                : "default";
    }
}

