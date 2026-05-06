package io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.preload;

import io.github.surezzzzzz.sdk.auth.aksk.client.core.executor.TokenRefreshExecutor;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.executor.TokenRefreshExecutor.TokenStatus;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.provider.SecurityContextProvider;
import io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.annotation.SimpleAkskRedisTokenManagerComponent;
import io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.configuration.SimpleAkskRedisTokenManagerProperties;
import io.github.surezzzzzz.sdk.cache.CachePreloadHandler;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

/**
 * Token Cache Preload Handler
 *
 * <p>基于 JWT 解析判断 token 是否即将过期，触发异步换 token。
 * 通过实现 {@link CachePreloadHandler} 接入 smart-cache 的 L2 预刷新机制。
 *
 * <p>判断逻辑：解析 JWT 的 exp claim，若剩余有效期 &le; {@code refreshBeforeExpire} 秒，
 * 则返回 {@code Optional.of(true)} 触发异步换 token，当前请求返回旧值不阻塞。
 *
 * @author surezzzzzz
 */
@SimpleAkskRedisTokenManagerComponent
@Slf4j
public class TokenCachePreloadHandler implements CachePreloadHandler {

    private final TokenRefreshExecutor tokenRefreshExecutor;
    private final SimpleAkskRedisTokenManagerProperties properties;
    private final SecurityContextProvider securityContextProvider;

    public TokenCachePreloadHandler(
            TokenRefreshExecutor tokenRefreshExecutor,
            SimpleAkskRedisTokenManagerProperties properties,
            SecurityContextProvider securityContextProvider) {
        this.tokenRefreshExecutor = tokenRefreshExecutor;
        this.properties = properties;
        this.securityContextProvider = securityContextProvider;
    }

    @Override
    public boolean support(String cacheName) {
        return properties.getRedis().getToken().getCacheName().equals(cacheName);
    }

    @Override
    public Optional<Boolean> needPreload(String cacheName, String key, Object cachedValue) {
        if (!(cachedValue instanceof String)) {
            return Optional.of(false);
        }
        TokenStatus status = tokenRefreshExecutor.checkTokenStatus((String) cachedValue);
        switch (status) {
            case EXPIRING_SOON:
                log.debug("Token EXPIRING_SOON, triggering async reload: key={}", key);
                return Optional.of(true);
            case VALID:
                return Optional.of(false);
            default: // EXPIRED, UNPARSABLE
                return Optional.of(false);
        }
    }

    @Override
    public Object reload(String cacheName, String key) {
        String securityContext = securityContextProvider.getSecurityContext();
        log.info("Preloading token: key={}", key);
        return tokenRefreshExecutor.fetchTokenFromServer(securityContext, null);
    }
}
