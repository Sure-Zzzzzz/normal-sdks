package io.github.surezzzzzz.sdk.auth.aksk.server.service;

import io.github.surezzzzzz.sdk.auth.aksk.server.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.aksk.server.constant.ServerErrorMessage;
import io.github.surezzzzzz.sdk.auth.aksk.server.entity.OAuth2RegisteredClientEntity;
import io.github.surezzzzzz.sdk.auth.aksk.server.exception.SimpleAkskServerException;
import io.github.surezzzzzz.sdk.auth.aksk.server.repository.OAuth2RegisteredClientEntityRepository;
import io.github.surezzzzzz.sdk.auth.aksk.server.support.RedisKeyHelper;
import io.github.surezzzzzz.sdk.cache.manager.SmartCacheManager;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

/**
 * Cached OAuth2 Registered Client Entity Service
 *
 * <p>Wraps {@link OAuth2RegisteredClientEntityRepository} with SmartCache (L1+L2) layer.
 * SmartCache is a required AKSK Server dependency.
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
     * <p>Exception semantics: SmartCache/Redis failures are not downgraded to database-only mode.
     *
     * @param clientId the client id
     * @return optional client entity
     */
    public Optional<OAuth2RegisteredClientEntity> findByClientId(String clientId) {
        try {
            OAuth2RegisteredClientEntity entity = smartCacheManager.get(
                    RedisKeyHelper.CACHE_OAUTH2_CLIENT_ENTITY,
                    redisKeyHelper.buildCacheKeyById(clientId),
                    () -> delegate.findByClientId(clientId).orElse(null)
            );
            return Optional.ofNullable(entity);
        } catch (Exception e) {
            log.error("Failed to get client entity from smart cache: {}", clientId, e);
            throw new SimpleAkskServerException(ErrorCode.CACHE_OPERATION_FAILED,
                    String.format(ServerErrorMessage.CACHE_OPERATION_FAILED, clientId), e);
        }
    }

    /**
     * Evict the cached entry for the given clientId.
     *
     * <p>Called by {@code ClientManagementServiceImpl} after each write operation
     * (create / update / delete) to ensure cache consistency.
     *
     * @param clientId the client id to evict
     */
    public void evict(String clientId) {
        try {
            smartCacheManager.evict(
                    RedisKeyHelper.CACHE_OAUTH2_CLIENT_ENTITY,
                    redisKeyHelper.buildCacheKeyById(clientId)
            );
        } catch (Exception e) {
            log.error("Failed to evict client entity from smart cache: {}", clientId, e);
            throw new SimpleAkskServerException(ErrorCode.CACHE_OPERATION_FAILED,
                    String.format(ServerErrorMessage.CACHE_OPERATION_FAILED, clientId), e);
        }
    }
}