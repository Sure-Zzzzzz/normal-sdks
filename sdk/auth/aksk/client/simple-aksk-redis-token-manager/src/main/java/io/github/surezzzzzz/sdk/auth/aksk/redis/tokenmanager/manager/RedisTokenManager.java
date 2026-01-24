package io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.manager;

import io.github.surezzzzzz.sdk.auth.aksk.client.core.strategy.TokenCacheStrategy;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.configuration.SimpleAkskClientCoreProperties;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.constant.ClientErrorCode;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.constant.ClientErrorMessage;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.exception.TokenLockException;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.executor.TokenRefreshExecutor;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.provider.SecurityContextProvider;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.manager.TokenManager;
import io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.annotation.SimpleAkskRedisTokenManagerComponent;
import io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.support.RedisKeyHelper;
import io.github.surezzzzzz.sdk.lock.redis.SimpleRedisLock;
import io.github.surezzzzzz.sdk.retry.task.executor.TaskRetryExecutor;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Redis Token Manager
 * <p>
 * 基于 Redis 的分布式 Token 管理器
 * <p>
 * 特性：
 * <ul>
 *   <li>使用分布式锁避免并发获取 Token</li>
 *   <li>支持多实例共享 Token 缓存</li>
 *   <li>自动刷新过期 Token</li>
 * </ul>
 *
 * @author surezzzzzz
 */
@SimpleAkskRedisTokenManagerComponent
@Slf4j
public class RedisTokenManager implements TokenManager {

    private final SimpleAkskClientCoreProperties coreProperties;
    private final TokenCacheStrategy tokenCacheStrategy;
    private final SecurityContextProvider securityContextProvider;
    private final SimpleRedisLock simpleRedisLock;
    private final RedisKeyHelper redisKeyHelper;
    private final TokenRefreshExecutor tokenRefreshExecutor;

    public RedisTokenManager(
            SimpleAkskClientCoreProperties coreProperties,
            TokenCacheStrategy tokenCacheStrategy,
            SecurityContextProvider securityContextProvider,
            SimpleRedisLock simpleRedisLock,
            RedisKeyHelper redisKeyHelper,
            TaskRetryExecutor retryExecutor
    ) {
        this.coreProperties = coreProperties;
        this.tokenCacheStrategy = tokenCacheStrategy;
        this.securityContextProvider = securityContextProvider;
        this.simpleRedisLock = simpleRedisLock;
        this.redisKeyHelper = redisKeyHelper;
        // 实例化 TokenRefreshExecutor（不作为 Bean）
        this.tokenRefreshExecutor = new TokenRefreshExecutor(coreProperties, retryExecutor);
    }

    /**
     * 分布式锁超时时间（秒）
     */
    private static final int LOCK_TIMEOUT_SECONDS = 10;

    @Override
    public String getToken() {
        // 1. 获取 security_context
        String securityContext = securityContextProvider.getSecurityContext();

        // 2. 生成缓存 Key
        String cacheKey = tokenCacheStrategy.generateCacheKey(securityContext);

        // 3. 尝试从缓存获取
        String cachedToken = tokenCacheStrategy.get(cacheKey);

        // 4. 缓存为空，直接刷新
        if (cachedToken == null) {
            log.debug("Token cache miss, fetching new token: key={}", cacheKey);
            return fetchTokenWithLock(cacheKey, securityContext);
        }

        // 5. 检查 Token 状态
        TokenRefreshExecutor.TokenStatus status = tokenRefreshExecutor.checkTokenStatus(cachedToken);

        switch (status) {
            case EXPIRED:
            case UNPARSABLE:
                log.debug("Token {} (status={}), refreshing immediately: key={}",
                         status == TokenRefreshExecutor.TokenStatus.EXPIRED ? "expired" : "unparsable", status, cacheKey);
                return fetchTokenWithLock(cacheKey, securityContext);

            case EXPIRING_SOON:
                log.debug("Token expiring soon, refreshing in background: key={}", cacheKey);
                tokenRefreshExecutor.asyncRefreshToken(() -> fetchTokenWithLock(cacheKey, securityContext));
                return cachedToken;

            case VALID:
                log.debug("Token cache hit (valid): key={}", cacheKey);
                return cachedToken;

            default:
                return cachedToken;
        }
    }

    @Override
    public void clearToken() {
        String securityContext = securityContextProvider.getSecurityContext();
        String cacheKey = tokenCacheStrategy.generateCacheKey(securityContext);
        tokenCacheStrategy.remove(cacheKey);
        log.debug("Token cleared: key={}", cacheKey);
    }

    /**
     * 使用分布式锁获取 Token
     * <p>
     * 策略：
     * <ul>
     *   <li>抢到锁：获取 Token 并缓存</li>
     *   <li>没抢到锁：等待后从缓存读取，读不到则递归重试</li>
     * </ul>
     */
    private String fetchTokenWithLock(String cacheKey, String securityContext) {
        String lockKey = redisKeyHelper.buildTokenLockKey(cacheKey);
        String lockValue = UUID.randomUUID().toString();

        try {
            // 尝试获取分布式锁
            boolean locked = simpleRedisLock.tryLock(lockKey, lockValue, LOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!locked) {
                // 没抢到锁，短暂等待后递归重试
                log.debug("Failed to acquire lock, retrying after waiting: key={}", cacheKey);
                Thread.sleep(100);

                // 先尝试从缓存读取（可能其他实例已经缓存成功）
                String cachedToken = tokenCacheStrategy.get(cacheKey);
                if (cachedToken != null) {
                    log.debug("Token fetched from cache after waiting: key={}", cacheKey);
                    return cachedToken;
                }

                // 缓存中还没有，递归重试（再次尝试抢锁）
                return fetchTokenWithLock(cacheKey, securityContext);
            }

            try {
                // 获取锁成功，double-check 缓存
                String token = tokenCacheStrategy.get(cacheKey);
                if (token != null) {
                    TokenRefreshExecutor.TokenStatus status = tokenRefreshExecutor.checkTokenStatus(token);
                    if (status == TokenRefreshExecutor.TokenStatus.VALID) {
                        return token;
                    }
                }

                // 从 OAuth2 Server 获取新 Token
                return fetchTokenFromServer(cacheKey, securityContext);
            } finally {
                // 释放锁
                simpleRedisLock.unlock(lockKey, lockValue);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TokenLockException(ClientErrorCode.TOKEN_LOCK_INTERRUPTED, ClientErrorMessage.TOKEN_LOCK_INTERRUPTED, e);
        }
    }

    /**
     * 从 OAuth2 Server 获取 Token（带重试）
     */
    private String fetchTokenFromServer(String cacheKey, String securityContext) {
        return tokenRefreshExecutor.fetchTokenFromServer(
                securityContext,
                (accessToken, expiresIn) -> {
                    // 缓存 Token
                    tokenCacheStrategy.put(cacheKey, accessToken, expiresIn);
                    log.info("Token fetched and cached: key={}, expiresIn={}s", cacheKey, expiresIn);
                }
        );
    }
}
