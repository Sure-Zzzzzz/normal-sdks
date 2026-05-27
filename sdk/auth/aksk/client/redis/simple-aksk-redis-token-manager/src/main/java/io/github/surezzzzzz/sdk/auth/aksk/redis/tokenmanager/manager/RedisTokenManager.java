package io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.manager;

import io.github.surezzzzzz.sdk.auth.aksk.client.core.executor.TokenRefreshExecutor;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.manager.TokenManager;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.provider.SecurityContextProvider;
import io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.annotation.SimpleAkskRedisTokenManagerComponent;
import io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.configuration.SimpleAkskRedisTokenManagerProperties;
import io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.model.TokenWithExpiry;
import io.github.surezzzzzz.sdk.cache.configuration.SmartCacheProperties;
import io.github.surezzzzzz.sdk.cache.manager.SmartCacheManager;
import io.github.surezzzzzz.sdk.lock.redis.SimpleRedisLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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

    private final SecurityContextProvider securityContextProvider;
    private final SmartCacheManager cacheManager;
    private final SimpleAkskRedisTokenManagerProperties properties;
    private final SmartCacheProperties smartCacheProperties;
    private final TokenRefreshExecutor tokenRefreshExecutor;
    private final SimpleRedisLock redisLock;

    /**
     * 本地锁，防止同一实例内并发打 server
     */
    private final ConcurrentHashMap<String, Object> localLocks = new ConcurrentHashMap<>();

    /**
     * L2 轮询间隔（毫秒）
     */
    private static final int L2_POLL_INTERVAL_MS = 500;

    /**
     * 分布式锁超时 fallback 值（秒），当 SmartCacheProperties.getLock() 为 null 时使用
     */
    private static final int DEFAULT_LOCK_TIMEOUT_SECONDS = 30;

    @Override
    public String getToken() {
        String securityContext = securityContextProvider.getSecurityContext();
        String cacheKey = generateCacheKey(securityContext);
        String cacheName = properties.getRedis().getToken().getCacheName();

        // 先查缓存（L1 → L2），命中直接返回 token
        TokenWithExpiry cached = cacheManager.get(cacheName, cacheKey);
        if (cached != null) {
            return cached.getToken();
        }

        // cache miss，抢分布式锁，防止击穿
        String lockKey = buildLockKey(cacheName, cacheKey);
        String requestId = UUID.randomUUID().toString();
        boolean locked = false;

        try {
            int lockTimeout = smartCacheProperties.getLock() != null
                    ? smartCacheProperties.getLock().getTimeoutSeconds()
                    : DEFAULT_LOCK_TIMEOUT_SECONDS;
            locked = redisLock.tryLock(lockKey, requestId, lockTimeout, TimeUnit.SECONDS);

            if (locked) {
                // 双重检查 L2
                TokenWithExpiry fromL2 = cacheManager.get(cacheName, cacheKey);
                if (fromL2 != null) {
                    return fromL2.getToken();
                }

                // 抢到锁，真正从 server 拿 token
                return fetchAndCacheToken(securityContext, cacheName, cacheKey);
            } else {
                // 没抢到锁，轮询 L2 等待其他实例写入
                return waitForTokenFromL2(cacheName, cacheKey, lockTimeout);
            }
        } finally {
            if (locked) {
                try {
                    redisLock.unlock(lockKey, requestId);
                } catch (Exception e) {
                    log.warn("解锁失败: {}", e.getMessage());
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
            int ttl = (int) (holder[0].getExpiresAt() - System.currentTimeMillis() / 1000);
            if (ttl <= 0) {
                ttl = smartCacheProperties.getL2().getExpireSeconds();
            }
            cacheManager.put(cacheName, cacheKey, holder[0], ttl);
            return holder[0].getToken();
        }
        return null;
    }

    /**
     * 轮询 L2，等待其他实例写入 token
     */
    private String waitForTokenFromL2(String cacheName, String cacheKey, int lockTimeout) {
        int retryCount = (int) (lockTimeout * 1000L / L2_POLL_INTERVAL_MS);
        for (int i = 0; i < retryCount; i++) {
            try {
                Thread.sleep(L2_POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            TokenWithExpiry cached = cacheManager.get(cacheName, cacheKey);
            if (cached != null) {
                return cached.getToken();
            }
        }
        log.warn("等待 L2 token 超时，使用本地锁兜底");
        return fetchWithLocalLock(securityContextProvider.getSecurityContext(), cacheName, cacheKey);
    }

    /**
     * 本地锁兜底（防止分布式锁失效时击穿）
     */
    private String fetchWithLocalLock(String securityContext, String cacheName, String cacheKey) {
        Object localLock = localLocks.computeIfAbsent(cacheKey, k -> new Object());
        synchronized (localLock) {
            // 双重检查 L2
            TokenWithExpiry fromL2 = cacheManager.get(cacheName, cacheKey);
            if (fromL2 != null) {
                return fromL2.getToken();
            }
            return fetchAndCacheToken(securityContext, cacheName, cacheKey);
        }
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

    /**
     * 生成分布式锁 Key
     *
     * <p>格式：{keyPrefix}-lock:{cacheName}:{me}:{cacheKey}
     * 包含 me（实例标识），避免多实例共用 Redis 时锁冲突。
     */
    private String buildLockKey(String cacheName, String cacheKey) {
        return smartCacheProperties.getKeyPrefix() + "-lock:"
                + cacheName + ":"
                + smartCacheProperties.getMe() + ":"
                + cacheKey;
    }

}