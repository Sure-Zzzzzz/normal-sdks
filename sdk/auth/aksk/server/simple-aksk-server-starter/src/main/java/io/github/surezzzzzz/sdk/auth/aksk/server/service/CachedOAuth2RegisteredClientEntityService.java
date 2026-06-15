package io.github.surezzzzzz.sdk.auth.aksk.server.service;

import io.github.surezzzzzz.sdk.auth.aksk.server.entity.OAuth2RegisteredClientEntity;
import io.github.surezzzzzz.sdk.auth.aksk.server.repository.OAuth2RegisteredClientEntityRepository;
import io.github.surezzzzzz.sdk.auth.aksk.server.support.RedisKeyHelper;
import io.github.surezzzzzz.sdk.cache.manager.SmartCacheManager;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

/**
 * Cached OAuth2 Registered Client Entity Service
 *
 * <p>Wraps {@link OAuth2RegisteredClientEntityRepository} with SmartCache (L1+L2) layer.
 * When {@link SmartCacheManager} is available, uses L1(Caffeine) + L2(Redis) two-level cache.
 * Falls back to database-only mode otherwise.
 *
 * <p>Exposes only the hot-path method {@link #findByClientId(String)}.
 * Write-path cache invalidation is performed explicitly by {@code ClientManagementServiceImpl}
 * after each write operation.
 *
 * @author surezzzzzz
 */
@Slf4j
public class CachedOAuth2RegisteredClientEntityService {

    private final OAuth2RegisteredClientEntityRepository delegate;
    private final SmartCacheManager smartCacheManager;
    private final RedisKeyHelper redisKeyHelper;

    public CachedOAuth2RegisteredClientEntityService(
            OAuth2RegisteredClientEntityRepository delegate,
            SmartCacheManager smartCacheManager,
            RedisKeyHelper redisKeyHelper) {
        this.delegate = delegate;
        this.smartCacheManager = smartCacheManager;
        this.redisKeyHelper = redisKeyHelper;
    }

    /**
     * Find client entity by clientId with two-level cache.
     *
     * <p>Cache miss → loader reads from JPA → result (or null) written to L1/L2.
     * Cache hit → returns cached value without DB query.
     *
     * <p>SmartCache 1.1.x null caching policy: loader returning null writes only L1
     * NULL_PLACEHOLDER (not L2). This prevents null entries from consuming Redis memory.
     *
     * <p>Exception semantics: try-catch only handles SmartCache own failures (Redis down,
     * serialization error, etc.). If the loader itself throws a DataAccessException,
     * it propagates through SmartCache into the catch block, then delegate.findByClientId
     * is called again and still throws → same behavior as calling the raw JPA repository.
     *
     * @param clientId the client id
     * @return optional client entity
     */
    public Optional<OAuth2RegisteredClientEntity> findByClientId(String clientId) {
        if (smartCacheManager == null || redisKeyHelper == null) {
            return delegate.findByClientId(clientId);
        }
        try {
            // try-catch 仅处理 SmartCache 自身故障（Redis 宕机、序列化异常等）。
            // loader 内部 JPA 抛出的 DataAccessException 会被 SmartCache 包装传到这里，
            // 进入 catch 后 delegate.findByClientId 会再抛一次，自然冒泡给调用方——
            // 与改造前 entityRepository.findByClientId 行为一致（DB 闪断时 /oauth2/token 仍返回 500）。
            OAuth2RegisteredClientEntity entity = smartCacheManager.get(
                    RedisKeyHelper.CACHE_OAUTH2_CLIENT_ENTITY,
                    redisKeyHelper.buildCacheKeyById(clientId),
                    () -> delegate.findByClientId(clientId).orElse(null)
            );
            return Optional.ofNullable(entity);
        } catch (Exception e) {
            log.error("Failed to get client entity from smart cache: {}", clientId, e);
            return delegate.findByClientId(clientId);
        }
    }

    /**
     * Evict the cached entry for the given clientId.
     *
     * <p>Called by {@code ClientManagementServiceImpl} after each write operation
     * (create / update / delete) to ensure cache consistency.
     *
     * <p>No-op when SmartCacheManager is not available.
     *
     * @param clientId the client id to evict
     */
    public void evict(String clientId) {
        if (smartCacheManager == null || redisKeyHelper == null) {
            return;
        }
        try {
            smartCacheManager.evict(
                    RedisKeyHelper.CACHE_OAUTH2_CLIENT_ENTITY,
                    redisKeyHelper.buildCacheKeyById(clientId)
            );
        } catch (Exception e) {
            log.error("Failed to evict client entity from smart cache: {}", clientId, e);
        }
    }
}