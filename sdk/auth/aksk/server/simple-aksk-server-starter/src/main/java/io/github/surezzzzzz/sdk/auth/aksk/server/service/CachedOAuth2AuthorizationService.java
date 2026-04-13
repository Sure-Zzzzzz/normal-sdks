package io.github.surezzzzzz.sdk.auth.aksk.server.service;

import io.github.surezzzzzz.sdk.auth.aksk.server.support.RedisKeyHelper;
import io.github.surezzzzzz.sdk.retry.task.executor.TaskRetryExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.lang.Nullable;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;

/**
 * Cached OAuth2 Authorization Service
 *
 * <p>Wraps {@link OAuth2AuthorizationService} with a Redis cache layer.
 * Only active when Redis is enabled.
 *
 * @author surezzzzzz
 */
@Slf4j
public class CachedOAuth2AuthorizationService implements OAuth2AuthorizationService {

    private final OAuth2AuthorizationService delegate;
    private final CacheManager cacheManager;
    private final RedisKeyHelper redisKeyHelper;
    private final TaskRetryExecutor taskRetryExecutor;

    public CachedOAuth2AuthorizationService(OAuth2AuthorizationService delegate,
                                            CacheManager cacheManager,
                                            RedisKeyHelper redisKeyHelper,
                                            TaskRetryExecutor taskRetryExecutor) {
        this.delegate = delegate;
        this.cacheManager = cacheManager;
        this.redisKeyHelper = redisKeyHelper;
        this.taskRetryExecutor = taskRetryExecutor;
    }

    @Override
    public void save(OAuth2Authorization authorization) {
        delegate.save(authorization);

        if (cacheManager == null) {
            return;
        }

        OAuth2Authorization.Token<OAuth2AccessToken> accessToken =
                authorization.getToken(OAuth2AccessToken.class);
        boolean isRevoked = accessToken != null && accessToken.isInvalidated();

        // 写入 ID 缓存
        try {
            taskRetryExecutor.executeWithFastRetry(() -> {
                Cache idCache = cacheManager.getCache(RedisKeyHelper.CACHE_OAUTH2_AUTHORIZATION);
                if (idCache != null) {
                    idCache.put(redisKeyHelper.buildCacheKeyById(authorization.getId()), authorization);
                    log.debug("Saved authorization to cache: {}", authorization.getId());
                }
                return null;
            });
        } catch (Exception e) {
            log.error("Failed to cache authorization after retries: {}", authorization.getId(), e);
        }

        // token 被撤销时 evict token 缓存，避免 introspect 拿到旧数据
        if (isRevoked) {
            try {
                Cache tokenCache = cacheManager.getCache(RedisKeyHelper.CACHE_OAUTH2_AUTHORIZATION_TOKEN);
                if (tokenCache != null) {
                    String tokenValue = accessToken.getToken().getTokenValue();
                    tokenCache.evict(redisKeyHelper.buildCacheKeyByToken(tokenValue, null));
                    tokenCache.evict(redisKeyHelper.buildCacheKeyByToken(tokenValue, OAuth2TokenType.ACCESS_TOKEN.getValue()));
                    log.debug("Evicted token cache for revoked authorization: {}", authorization.getId());
                }
            } catch (Exception e) {
                log.warn("Failed to evict token cache for revoked authorization: {}", authorization.getId(), e);
            }
        }
    }

    @Override
    public void remove(OAuth2Authorization authorization) {
        delegate.remove(authorization);

        if (cacheManager == null) {
            return;
        }

        try {
            Cache cache = cacheManager.getCache(RedisKeyHelper.CACHE_OAUTH2_AUTHORIZATION);
            if (cache != null) {
                cache.evict(redisKeyHelper.buildCacheKeyById(authorization.getId()));
                log.debug("Evicted authorization from cache: {}", authorization.getId());
            }
        } catch (Exception e) {
            log.error("Failed to evict authorization from cache: {}", authorization.getId(), e);
        }
    }

    @Override
    public OAuth2Authorization findById(String id) {
        if (cacheManager == null) {
            return delegate.findById(id);
        }

        try {
            Cache cache = cacheManager.getCache(RedisKeyHelper.CACHE_OAUTH2_AUTHORIZATION);
            if (cache != null) {
                Cache.ValueWrapper wrapper = cache.get(redisKeyHelper.buildCacheKeyById(id));
                if (wrapper != null) {
                    OAuth2Authorization cached = (OAuth2Authorization) wrapper.get();
                    if (cached != null) {
                        log.debug("Found authorization by id from cache: {}", id);
                        return cached;
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to get authorization from cache: {}", id, e);
        }

        OAuth2Authorization authorization = delegate.findById(id);
        if (authorization != null) {
            log.debug("Found authorization by id from database: {}", id);
            try {
                Cache cache = cacheManager.getCache(RedisKeyHelper.CACHE_OAUTH2_AUTHORIZATION);
                if (cache != null) {
                    cache.put(redisKeyHelper.buildCacheKeyById(id), authorization);
                }
            } catch (Exception e) {
                log.error("Failed to cache authorization: {}", id, e);
            }
        }

        return authorization;
    }

    @Override
    public OAuth2Authorization findByToken(String token, @Nullable OAuth2TokenType tokenType) {
        if (cacheManager == null) {
            return delegate.findByToken(token, tokenType);
        }

        try {
            Cache cache = cacheManager.getCache(RedisKeyHelper.CACHE_OAUTH2_AUTHORIZATION_TOKEN);
            if (cache != null) {
                String key = redisKeyHelper.buildCacheKeyByToken(token, tokenType != null ? tokenType.getValue() : null);
                Cache.ValueWrapper wrapper = cache.get(key);
                if (wrapper != null) {
                    OAuth2Authorization cached = (OAuth2Authorization) wrapper.get();
                    if (cached != null) {
                        log.debug("Found authorization by token from cache, type: {}", tokenType);
                        return cached;
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to get authorization from cache by token, type: {}", tokenType, e);
        }

        OAuth2Authorization authorization = delegate.findByToken(token, tokenType);
        if (authorization != null) {
            log.debug("Found authorization by token from database, type: {}", tokenType);
            try {
                Cache cache = cacheManager.getCache(RedisKeyHelper.CACHE_OAUTH2_AUTHORIZATION_TOKEN);
                if (cache != null) {
                    String key = redisKeyHelper.buildCacheKeyByToken(token, tokenType != null ? tokenType.getValue() : null);
                    cache.put(key, authorization);
                }
            } catch (Exception e) {
                log.error("Failed to cache authorization by token, type: {}", tokenType, e);
            }
        }

        return authorization;
    }
}
