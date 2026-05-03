package io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.manager;

import io.github.surezzzzzz.sdk.auth.aksk.client.core.configuration.SimpleAkskClientCoreProperties;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.constant.ClientErrorCode;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.constant.ClientErrorMessage;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.constant.SimpleAkskClientCoreConstant;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.exception.TokenLockException;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.executor.TokenRefreshExecutor.TokenStatus;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.manager.AbstractTokenManager;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.provider.SecurityContextProvider;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.strategy.TokenCacheStrategy;
import io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.annotation.SimpleAkskRedisTokenManagerComponent;
import io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.support.RedisKeyHelper;
import io.github.surezzzzzz.sdk.lock.redis.SimpleRedisLock;
import io.github.surezzzzzz.sdk.retry.task.executor.TaskRetryExecutor;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Redis Token Manager
 *
 * <p>基于 Redis 的分布式 Token 管理器，继承 {@link AbstractTokenManager} 复用通用流程，
 * 实现 {@link #fetchTokenWithLock} 提供分布式锁策略。
 *
 * <p>特性：
 * <ul>
 *   <li>使用分布式锁避免多实例并发获取 Token</li>
 *   <li>锁等待改为循环重试，最多 {@link SimpleAkskClientCoreConstant#LOCK_MAX_RETRY_TIMES} 次，避免栈溢出</li>
 *   <li>支持多实例共享 Token 缓存</li>
 * </ul>
 *
 * @author surezzzzzz
 */
@SimpleAkskRedisTokenManagerComponent
@Slf4j
public class RedisTokenManager extends AbstractTokenManager {

    private final SimpleRedisLock simpleRedisLock;
    private final RedisKeyHelper redisKeyHelper;

    public RedisTokenManager(
            SimpleAkskClientCoreProperties coreProperties,
            TokenCacheStrategy tokenCacheStrategy,
            SecurityContextProvider securityContextProvider,
            TaskRetryExecutor retryExecutor,
            SimpleRedisLock simpleRedisLock,
            RedisKeyHelper redisKeyHelper
    ) {
        super(coreProperties, tokenCacheStrategy, securityContextProvider, retryExecutor);
        this.simpleRedisLock = simpleRedisLock;
        this.redisKeyHelper = redisKeyHelper;
    }

    /**
     * 使用分布式锁获取 Token
     *
     * <p>策略：循环尝试抢锁，最多 {@link SimpleAkskClientCoreConstant#LOCK_MAX_RETRY_TIMES} 次：
     * <ul>
     *   <li>抢到锁：double-check 缓存，缓存有效则直接返回，否则从 OAuth2 Server 获取</li>
     *   <li>没抢到锁：等待 {@link SimpleAkskClientCoreConstant#LOCK_RETRY_SLEEP_MS} ms 后检查缓存，有则返回，无则继续循环</li>
     *   <li>超过最大重试次数：抛出 {@link TokenLockException}</li>
     * </ul>
     */
    @Override
    protected String fetchTokenWithLock(String cacheKey, String securityContext) {
        String lockKey = redisKeyHelper.buildTokenLockKey(cacheKey);

        try {
            for (int retry = 0; retry < SimpleAkskClientCoreConstant.LOCK_MAX_RETRY_TIMES; retry++) {
                String lockValue = UUID.randomUUID().toString();
                boolean locked = simpleRedisLock.tryLock(lockKey, lockValue,
                        SimpleAkskClientCoreConstant.DEFAULT_LOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS);

                if (!locked) {
                    log.debug("Failed to acquire lock (retry={}), waiting: key={}", retry, cacheKey);
                    Thread.sleep(SimpleAkskClientCoreConstant.LOCK_RETRY_SLEEP_MS);
                    String cached = tokenCacheStrategy.get(cacheKey);
                    if (cached != null) {
                        log.debug("Token fetched from cache after waiting: key={}", cacheKey);
                        return cached;
                    }
                    continue;
                }

                try {
                    // double-check 缓存
                    String token = tokenCacheStrategy.get(cacheKey);
                    if (token != null && tokenRefreshExecutor.checkTokenStatus(token) == TokenStatus.VALID) {
                        return token;
                    }
                    return fetchTokenFromServer(cacheKey, securityContext);
                } finally {
                    simpleRedisLock.unlock(lockKey, lockValue);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TokenLockException(
                    ClientErrorCode.TOKEN_LOCK_INTERRUPTED,
                    ClientErrorMessage.TOKEN_LOCK_INTERRUPTED, e);
        }

        throw new TokenLockException(
                ClientErrorCode.TOKEN_LOCK_FAILED,
                ClientErrorMessage.TOKEN_LOCK_FAILED);
    }
}
