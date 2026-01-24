package io.github.surezzzzzz.sdk.auth.aksk.server.service;

import io.github.surezzzzzz.sdk.auth.aksk.server.support.RedisKeyHelper;
import io.github.surezzzzzz.sdk.retry.task.executor.TaskRetryExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.lang.Nullable;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;

/**
 * Cached OAuth2 Authorization Service
 * 在JdbcOAuth2AuthorizationService基础上添加Redis缓存层
 *
 * @author surezzzzzz
 */
@Slf4j
public class CachedOAuth2AuthorizationService implements OAuth2AuthorizationService {

    private final OAuth2AuthorizationService delegate;

    @Autowired(required = false)
    private CacheManager cacheManager;

    @Autowired(required = false)
    private RedisKeyHelper redisKeyHelper;

    @Autowired
    private TaskRetryExecutor taskRetryExecutor;

    public CachedOAuth2AuthorizationService(OAuth2AuthorizationService delegate) {
        this.delegate = delegate;
    }

    /**
     * 保存授权信息到数据库，并写入缓存
     * <p>
     * Redis Key格式: sure-auth-aksk:{me}:oauth2:authorization::{id}
     * 示例: sure-auth-aksk:test-app:oauth2:authorization::{abc-123}
     */
    @Override
    public void save(OAuth2Authorization authorization) {
        // 保存到数据库
        delegate.save(authorization);

        // 如果CacheManager未配置（Redis被disable），跳过缓存
        if (cacheManager == null) {
            log.debug("CacheManager not configured, skip caching for authorization: {}", authorization.getId());
            return;
        }

        // 使用快速重试策略写入缓存
        try {
            taskRetryExecutor.executeWithFastRetry(() -> {
                Cache cache = cacheManager.getCache(RedisKeyHelper.CACHE_OAUTH2_AUTHORIZATION);
                if (cache != null) {
                    String key = redisKeyHelper.buildCacheKeyById(authorization.getId());
                    cache.put(key, authorization);
                    log.debug("Saved authorization to cache: {}", authorization.getId());
                } else {
                    log.warn("Cache '{}' not found, skip caching", RedisKeyHelper.CACHE_OAUTH2_AUTHORIZATION);
                }
                return null;
            });
        } catch (Exception e) {
            log.error("Failed to cache authorization after retries: {}", authorization.getId(), e);
        }
    }

    /**
     * 从数据库删除授权信息，并清除缓存
     * <p>
     * Redis Key格式: sure-auth-aksk:{me}:oauth2:authorization::{id}
     * 示例: sure-auth-aksk:test-app:oauth2:authorization::{abc-123}
     */
    @Override
    public void remove(OAuth2Authorization authorization) {
        // 从数据库删除
        delegate.remove(authorization);

        // 如果CacheManager未配置（Redis被disable），跳过缓存清除
        if (cacheManager == null) {
            log.debug("CacheManager not configured, skip cache eviction for authorization: {}", authorization.getId());
            return;
        }

        // 清除缓存
        try {
            Cache cache = cacheManager.getCache(RedisKeyHelper.CACHE_OAUTH2_AUTHORIZATION);
            if (cache != null) {
                String key = redisKeyHelper.buildCacheKeyById(authorization.getId());
                cache.evict(key);
                log.debug("Evicted authorization from cache: {}", authorization.getId());
            }
        } catch (Exception e) {
            log.error("Failed to evict authorization from cache: {}", authorization.getId(), e);
        }
    }

    /**
     * 根据ID查询授权信息，优先从缓存读取
     * <p>
     * Redis Key格式: sure-auth-aksk:{me}:oauth2:authorization::{id}
     * 示例: sure-auth-aksk:test-app:oauth2:authorization::{abc-123}
     */
    @Override
    public OAuth2Authorization findById(String id) {
        // 如果CacheManager未配置（Redis被disable），直接查数据库
        if (cacheManager == null) {
            return delegate.findById(id);
        }

        // 先查缓存
        try {
            Cache cache = cacheManager.getCache(RedisKeyHelper.CACHE_OAUTH2_AUTHORIZATION);
            if (cache != null) {
                String key = redisKeyHelper.buildCacheKeyById(id);
                Cache.ValueWrapper wrapper = cache.get(key);
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

        // 缓存未命中，查数据库
        OAuth2Authorization authorization = delegate.findById(id);
        if (authorization != null) {
            log.debug("Found authorization by id from database: {}", id);

            // 写入缓存
            try {
                Cache cache = cacheManager.getCache(RedisKeyHelper.CACHE_OAUTH2_AUTHORIZATION);
                if (cache != null) {
                    String key = redisKeyHelper.buildCacheKeyById(id);
                    cache.put(key, authorization);
                }
            } catch (Exception e) {
                log.error("Failed to cache authorization: {}", id, e);
            }
        }

        return authorization;
    }

    /**
     * 根据Token查询授权信息，优先从缓存读取
     * <p>
     * Redis Key格式: sure-auth-aksk:{me}:oauth2:authorization:token::{token}:{tokenType}
     * 示例: sure-auth-aksk:test-app:oauth2:authorization:token::{eyJhbGc...xyz}:access_token
     */
    @Override
    public OAuth2Authorization findByToken(String token, @Nullable OAuth2TokenType tokenType) {
        // 如果CacheManager未配置（Redis被disable），直接查数据库
        if (cacheManager == null) {
            return delegate.findByToken(token, tokenType);
        }

        // 先查缓存
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

        // 缓存未命中，查数据库
        OAuth2Authorization authorization = delegate.findByToken(token, tokenType);
        if (authorization != null) {
            log.debug("Found authorization by token from database, type: {}", tokenType);

            // 写入缓存
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
