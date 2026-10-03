package io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.manager;

import io.github.surezzzzzz.sdk.auth.aksk.client.core.constant.ClientErrorCode;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.constant.ClientErrorMessage;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.exception.TokenFetchException;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.executor.TokenRefreshExecutor;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.manager.TokenManager;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.provider.SecurityContextProvider;
import io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.annotation.SimpleAkskRedisTokenManagerComponent;
import io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.configuration.SimpleAkskRedisTokenManagerProperties;
import io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.constant.SimpleAkskRedisTokenManagerConstant;
import io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.model.TokenWithExpiry;
import io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.support.CacheKeyHelper;
import io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.support.TokenCacheTtlHelper;
import io.github.surezzzzzz.sdk.cache.configuration.SmartCacheProperties;
import io.github.surezzzzzz.sdk.cache.manager.SmartCacheManager;
import io.github.surezzzzzz.sdk.cache.support.KeyHelper;
import io.github.surezzzzzz.sdk.lock.redis.SimpleRedisLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Redis Token Manager
 *
 * <p>基于 {@link SmartCacheManager} 的分布式 Token 管理器。
 *
 * <p>特性：
 * <ul>
 *   <li><b>L1 缓存</b>：JVM 本地缓存（Caffeine），TTL 短（默认 2s），减少 Redis IO</li>
 *   <li><b>L2 缓存</b>：Redis 分布式缓存，多实例共享 token</li>
 *   <li><b>分布式锁</b>：防止多实例并发打 OAuth2 Server</li>
 *   <li><b>多实例 L1 一致性</b>：clearToken() 通过 Pub/Sub 广播 L1 失效，各实例同步清除</li>
 *   <li><b>L2 预刷新</b>：由 smart-cache 内置 preload 机制触发，Redis TTL = beforeExpireSeconds 时异步刷新</li>
 * </ul>
 *
 * <p>缓存流程：L1 → L2 → 抢分布式锁（防止击穿） → fetch → 写回 L1 + L2
 *
 * @author surezzzzzz
 */
@SimpleAkskRedisTokenManagerComponent
@RequiredArgsConstructor
@Slf4j
public class RedisTokenManager implements TokenManager {

    /**
     * 固定分片本地锁：在请求分布式锁前先收敛同一 JVM 内的相同缓存键，避免高并发线程重复等待和刷新。
     */
    private static final Object[] LOCAL_LOCKS = createLocalLocks();
    private final SecurityContextProvider securityContextProvider;
    private final SmartCacheManager cacheManager;
    private final SimpleAkskRedisTokenManagerProperties properties;
    private final SmartCacheProperties smartCacheProperties;
    private final TokenRefreshExecutor tokenRefreshExecutor;
    private final SimpleRedisLock redisLock;

    private static Object[] createLocalLocks() {
        Object[] locks = new Object[SimpleAkskRedisTokenManagerConstant.LOCAL_LOCK_STRIPE_COUNT];
        for (int i = 0; i < locks.length; i++) {
            locks[i] = new Object();
        }
        return locks;
    }

    @Override
    public String getToken() {
        String securityContext = securityContextProvider.getSecurityContext();
        String cacheKey = generateCacheKey(securityContext);
        String cacheName = properties.getRedis().getToken().getCacheName();

        // 类型化读取：smart-cache 2.x 对 Object.class 读取做 trusted-packages 白名单校验，
        // 内部模型必须显式指定类型，否则反序列化被拒、L2 恒 miss
        TokenWithExpiry cached = getCachedToken(cacheName, cacheKey);
        if (cached != null) {
            return cached.getToken();
        }

        return fetchWithLocalLock(securityContext, cacheName, cacheKey);
    }

    /**
     * 在同一 JVM 内先收敛同一缓存键的刷新请求。
     *
     * <p>分布式锁用于实例间互斥，本地锁用于避免同一实例内所有竞争线程各自等待 L2 并在超时后重复刷新。
     */
    private String fetchWithLocalLock(String securityContext, String cacheName, String cacheKey) {
        synchronized (getLocalLock(cacheKey)) {
            TokenWithExpiry fromCache = getCachedToken(cacheName, cacheKey);
            if (fromCache != null) {
                return fromCache.getToken();
            }
            return fetchWithDistributedLock(securityContext, cacheName, cacheKey);
        }
    }

    /**
     * 跨实例收敛 Token 刷新请求。
     */
    private String fetchWithDistributedLock(String securityContext, String cacheName, String cacheKey) {
        String lockKey = buildLockKey(cacheName, cacheKey);
        String requestId = UUID.randomUUID().toString();
        boolean locked = false;

        try {
            int lockTimeout = smartCacheProperties.getLock() != null
                    ? smartCacheProperties.getLock().getTimeoutSeconds()
                    : SimpleAkskRedisTokenManagerConstant.DEFAULT_LOCK_TIMEOUT_SECONDS;
            try {
                locked = redisLock.tryLock(lockKey, requestId, lockTimeout, TimeUnit.SECONDS);
            } catch (Exception e) {
                // 对齐 SmartCacheManager.loadWithLock 的降级语义：锁不可用时固定本地锁已完成同 JVM 收敛。
                log.warn("获取分布式锁失败，使用当前实例锁兜底");
                return fetchAndCacheToken(securityContext, cacheName, cacheKey);
            }

            if (locked) {
                // 双重检查 L2
                TokenWithExpiry fromL2 = getCachedToken(cacheName, cacheKey);
                if (fromL2 != null) {
                    return fromL2.getToken();
                }

                // 抢到锁，真正从 server 拿 token
                return fetchAndCacheToken(securityContext, cacheName, cacheKey);
            } else {
                // 没抢到锁，轮询 L2 等待其他实例写入
                return waitForTokenFromL2(securityContext, cacheName, cacheKey, lockTimeout);
            }
        } finally {
            if (locked) {
                try {
                    redisLock.unlock(lockKey, requestId);
                } catch (Exception e) {
                    log.warn("解锁失败");
                }
            }
        }
    }

    /**
     * 从 server 获取 token 并写入缓存
     */
    private String fetchAndCacheToken(String securityContext, String cacheName, String cacheKey) {
        long fetchTime = System.currentTimeMillis() / 1000;
        TokenWithExpiry[] holder = new TokenWithExpiry[1];
        tokenRefreshExecutor.fetchTokenFromServer(securityContext, (token, expiresIn) -> {
            holder[0] = new TokenWithExpiry(token, fetchTime + expiresIn, securityContext);
        });

        if (holder[0] != null) {
            int ttl = TokenCacheTtlHelper.calculate(holder[0].getExpiresAt(), System.currentTimeMillis() / 1000);
            if (ttl <= 0) {
                throw tokenFetchException();
            }
            putCachedToken(cacheName, cacheKey, holder[0], ttl);
            return holder[0].getToken();
        }
        throw tokenFetchException();
    }

    /**
     * 轮询 L2，等待其他实例写入 token
     */
    private String waitForTokenFromL2(String securityContext, String cacheName, String cacheKey, int lockTimeout) {
        int retryCount = (int) (lockTimeout * 1000L / SimpleAkskRedisTokenManagerConstant.L2_POLL_INTERVAL_MS);
        for (int i = 0; i < retryCount; i++) {
            try {
                Thread.sleep(SimpleAkskRedisTokenManagerConstant.L2_POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            TokenWithExpiry cached = getCachedToken(cacheName, cacheKey);
            if (cached != null) {
                return cached.getToken();
            }
        }
        log.warn("等待 L2 token 超时，使用当前实例锁刷新");
        return fetchAndCacheToken(securityContext, cacheName, cacheKey);
    }

    @Override
    public void clearToken() {
        String securityContext = securityContextProvider.getSecurityContext();
        String cacheKey = generateCacheKey(securityContext);
        String cacheName = properties.getRedis().getToken().getCacheName();
        // strong 模式下 evict 会通过 Pub/Sub 广播，各实例同步清除 L1
        try {
            cacheManager.evict(cacheName, cacheKey);
        } catch (RuntimeException exception) {
            log.debug("Token 缓存清理失败，阶段: evict");
            throw tokenFetchException();
        }
        log.debug("Token 缓存已清理");
    }

    /**
     * 生成缓存 Key
     *
     * @param securityContext 安全上下文（JSON 字符串或 null）
     * @return 缓存 Key
     */
    private String generateCacheKey(String securityContext) {
        return CacheKeyHelper.generate(securityContext);
    }

    /**
     * 生成分布式锁 Key
     *
     * <p>复用 smart-cache 的 {@link KeyHelper#buildLockKey}，与 SmartCacheManager 的锁键同源，
     * 格式 {keyPrefix}-lock:{cacheName}:{me}:{cacheKey}（me 为应用组标识：同组互斥、跨组隔离）。
     */
    private String buildLockKey(String cacheName, String cacheKey) {
        return KeyHelper.buildLockKey(
                smartCacheProperties.getKeyPrefix(),
                cacheName,
                smartCacheProperties.getMe(),
                cacheKey
        );
    }

    /**
     * 读取缓存并将基础设施异常收口为 Token 获取失败，避免调用方误发无凭据请求。
     */
    private TokenWithExpiry getCachedToken(String cacheName, String cacheKey) {
        try {
            return cacheManager.get(cacheName, cacheKey, TokenWithExpiry.class);
        } catch (RuntimeException exception) {
            log.debug("Token 缓存读取失败，阶段: get");
            throw tokenFetchException();
        }
    }

    /**
     * 写入缓存并将基础设施异常收口为 Token 获取失败，避免返回未被可靠缓存的 Token。
     */
    private void putCachedToken(String cacheName, String cacheKey, TokenWithExpiry tokenWithExpiry, int ttl) {
        try {
            cacheManager.put(cacheName, cacheKey, tokenWithExpiry, ttl);
        } catch (RuntimeException exception) {
            log.debug("Token 缓存写入失败，阶段: put");
            throw tokenFetchException();
        }
    }

    private TokenFetchException tokenFetchException() {
        return new TokenFetchException(
                ClientErrorCode.TOKEN_FETCH_FAILED,
                String.format(ClientErrorMessage.TOKEN_FETCH_FAILED, ErrorMessage.TOKEN_CACHE_PROCESSING_FAILED));
    }

    private Object getLocalLock(String cacheKey) {
        return LOCAL_LOCKS[Math.floorMod(cacheKey.hashCode(), LOCAL_LOCKS.length)];
    }

}
