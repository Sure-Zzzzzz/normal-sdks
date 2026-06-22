package io.github.surezzzzzz.sdk.auth.aksk.server.service;

import io.github.surezzzzzz.sdk.auth.aksk.server.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.aksk.server.constant.ServerErrorMessage;
import io.github.surezzzzzz.sdk.auth.aksk.server.exception.SimpleAkskServerException;
import io.github.surezzzzzz.sdk.auth.aksk.server.support.RedisKeyHelper;
import io.github.surezzzzzz.sdk.cache.manager.SmartCacheManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;

/**
 * Cached OAuth2 Authorization Service
 *
 * <p>Wraps {@link OAuth2AuthorizationService} with SmartCache (L1+L2) layer.
 * SmartCache is a required AKSK Server dependency.
 *
 * @author surezzzzzz
 */
@Slf4j
public class CachedOAuth2AuthorizationService implements OAuth2AuthorizationService {

    private final OAuth2AuthorizationService delegate;
    private final SmartCacheManager smartCacheManager;
    private final RedisKeyHelper redisKeyHelper;

    public CachedOAuth2AuthorizationService(OAuth2AuthorizationService delegate,
                                            SmartCacheManager smartCacheManager,
                                            RedisKeyHelper redisKeyHelper) {
        this.delegate = delegate;
        this.smartCacheManager = smartCacheManager;
        this.redisKeyHelper = redisKeyHelper;
    }

    @Override
    public void save(OAuth2Authorization authorization) {
        delegate.save(authorization);

        OAuth2Authorization.Token<OAuth2AccessToken> accessToken =
                authorization.getToken(OAuth2AccessToken.class);
        boolean isRevoked = accessToken != null && accessToken.isInvalidated();

        // 写入 SmartCache（L1+L2）
        try {
            smartCacheManager.put(
                    RedisKeyHelper.CACHE_OAUTH2_AUTHORIZATION,
                    redisKeyHelper.buildCacheKeyById(authorization.getId()),
                    authorization
            );
            log.debug("Saved authorization to smart cache: {}", authorization.getId());
        } catch (Exception e) {
            log.error("Failed to cache authorization: {}", authorization.getId(), e);
            throw new SimpleAkskServerException(ErrorCode.CACHE_OPERATION_FAILED,
                    String.format(ServerErrorMessage.CACHE_OPERATION_FAILED, authorization.getId()), e);
        }

        // token 被撤销时清除 token 缓存，避免 introspect 拿到旧数据
        if (isRevoked) {
            evictTokenCache(accessToken.getToken().getTokenValue());
        }
    }

    @Override
    public void remove(OAuth2Authorization authorization) {
        delegate.remove(authorization);

        try {
            smartCacheManager.evict(
                    RedisKeyHelper.CACHE_OAUTH2_AUTHORIZATION,
                    redisKeyHelper.buildCacheKeyById(authorization.getId())
            );
            OAuth2Authorization.Token<OAuth2AccessToken> accessToken =
                    authorization.getToken(OAuth2AccessToken.class);
            if (accessToken != null) {
                evictTokenCache(accessToken.getToken().getTokenValue());
            }
            log.debug("Evicted authorization from smart cache: {}", authorization.getId());
        } catch (Exception e) {
            log.error("Failed to evict authorization from smart cache: {}", authorization.getId(), e);
            throw new SimpleAkskServerException(ErrorCode.CACHE_OPERATION_FAILED,
                    String.format(ServerErrorMessage.CACHE_OPERATION_FAILED, authorization.getId()), e);
        }
    }

    @Override
    public OAuth2Authorization findById(String id) {
        try {
            return smartCacheManager.get(
                    RedisKeyHelper.CACHE_OAUTH2_AUTHORIZATION,
                    redisKeyHelper.buildCacheKeyById(id),
                    () -> {
                        OAuth2Authorization authorization = delegate.findById(id);
                        if (authorization != null) {
                            log.debug("Found authorization by id from database: {}", id);
                        }
                        return authorization;
                    }
            );
        } catch (Exception e) {
            log.error("Failed to get authorization from smart cache: {}", id, e);
            throw new SimpleAkskServerException(ErrorCode.CACHE_OPERATION_FAILED,
                    String.format(ServerErrorMessage.CACHE_OPERATION_FAILED, id), e);
        }
    }

    @Override
    public OAuth2Authorization findByToken(String token, OAuth2TokenType tokenType) {
        try {
            String key = redisKeyHelper.buildCacheKeyByToken(token, tokenType != null ? tokenType.getValue() : null);
            return smartCacheManager.get(
                    RedisKeyHelper.CACHE_OAUTH2_AUTHORIZATION_TOKEN,
                    key,
                    () -> {
                        OAuth2Authorization authorization = delegate.findByToken(token, tokenType);
                        if (authorization != null) {
                            log.debug("Found authorization by token from database, type: {}", tokenType);
                        }
                        return authorization;
                    }
            );
        } catch (Exception e) {
            log.error("Failed to get authorization from smart cache by token, type: {}", tokenType, e);
            throw new SimpleAkskServerException(ErrorCode.CACHE_OPERATION_FAILED,
                    String.format(ServerErrorMessage.CACHE_OPERATION_FAILED, tokenType), e);
        }
    }

    /**
     * 通过 SmartCacheManager 清除 token 相关缓存（L1+L2+Pub/Sub）
     */
    public void evictTokenCache(String tokenValue) {
        try {
            String nullTypeKey = redisKeyHelper.buildCacheKeyByToken(tokenValue, null);
            String accessTokenTypeKey = redisKeyHelper.buildCacheKeyByToken(tokenValue, OAuth2TokenType.ACCESS_TOKEN.getValue());
            log.info("[Pub/Sub] Publishing eviction message for token: {}, nullTypeKey: {}, accessTokenTypeKey: {}",
                    tokenValue.substring(0, Math.min(20, tokenValue.length())), nullTypeKey, accessTokenTypeKey);
            smartCacheManager.evict(
                    RedisKeyHelper.CACHE_OAUTH2_AUTHORIZATION_TOKEN,
                    nullTypeKey
            );
            smartCacheManager.evict(
                    RedisKeyHelper.CACHE_OAUTH2_AUTHORIZATION_TOKEN,
                    accessTokenTypeKey
            );
            log.info("[Pub/Sub] Eviction message published successfully for token: {}",
                    tokenValue.substring(0, Math.min(20, tokenValue.length())));
        } catch (Exception e) {
            log.error("[Pub/Sub] Failed to evict token cache via smart cache", e);
            throw new SimpleAkskServerException(ErrorCode.CACHE_OPERATION_FAILED,
                    String.format(ServerErrorMessage.CACHE_OPERATION_FAILED, tokenValue), e);
        }
    }
}
