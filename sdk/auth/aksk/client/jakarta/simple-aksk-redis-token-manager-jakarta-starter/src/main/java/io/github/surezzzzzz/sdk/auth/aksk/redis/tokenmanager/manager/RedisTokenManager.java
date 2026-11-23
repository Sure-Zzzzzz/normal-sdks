package io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.manager;

import io.github.surezzzzzz.sdk.auth.aksk.client.core.constant.ClientErrorCode;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.constant.ClientErrorMessage;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.constant.SimpleAkskClientCoreConstant;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.exception.ConfigurationException;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.exception.TokenFetchException;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.executor.TokenRefreshExecutor;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.manager.ProfileTokenManager;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.manager.TokenManager;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.model.AkskClientProfile;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.provider.SecurityContextProvider;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.support.AkskClientProfileRegistry;
import io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.annotation.SimpleAkskRedisTokenManagerComponent;
import io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.configuration.SimpleAkskRedisTokenManagerProperties;
import io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.constant.SimpleAkskRedisTokenManagerConstant;
import io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.model.TokenWithExpiry;
import io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.support.CacheKeyHelper;
import io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.support.ProfileCacheKeyHelper;
import io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.support.TokenCacheTtlHelper;
import io.github.surezzzzzz.sdk.cache.configuration.SmartCacheProperties;
import io.github.surezzzzzz.sdk.cache.manager.SmartCacheManager;
import io.github.surezzzzzz.sdk.cache.support.KeyHelper;
import io.github.surezzzzzz.sdk.lock.redis.SimpleRedisLock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Redis Token Manager
 *
 * <p>基于 {@link SmartCacheManager} 的分布式 Token 管理器，支持默认 AKP 与命名 AKP Profile。
 *
 * <p>缓存流程：L1 → L2 → 抢分布式锁（防止击穿） → fetch → 写回 L1 + L2。
 *
 * @author surezzzzzz
 */
@SimpleAkskRedisTokenManagerComponent
@Slf4j
public class RedisTokenManager implements TokenManager, ProfileTokenManager {

    private static final Object[] LOCAL_LOCKS = createLocalLocks();

    private final SecurityContextProvider securityContextProvider;
    private final SmartCacheManager cacheManager;
    private final SimpleAkskRedisTokenManagerProperties properties;
    private final SmartCacheProperties smartCacheProperties;
    private final TokenRefreshExecutor tokenRefreshExecutor;
    private final SimpleRedisLock redisLock;
    private final AkskClientProfileRegistry profileRegistry;

    public RedisTokenManager(SecurityContextProvider securityContextProvider,
                             SmartCacheManager cacheManager,
                             SimpleAkskRedisTokenManagerProperties properties,
                             SmartCacheProperties smartCacheProperties,
                             TokenRefreshExecutor tokenRefreshExecutor,
                             SimpleRedisLock redisLock) {
        this(securityContextProvider, cacheManager, properties, smartCacheProperties,
                tokenRefreshExecutor, redisLock, null);
    }

    @Autowired
    public RedisTokenManager(SecurityContextProvider securityContextProvider,
                             SmartCacheManager cacheManager,
                             SimpleAkskRedisTokenManagerProperties properties,
                             SmartCacheProperties smartCacheProperties,
                             TokenRefreshExecutor tokenRefreshExecutor,
                             SimpleRedisLock redisLock,
                             AkskClientProfileRegistry profileRegistry) {
        this.securityContextProvider = securityContextProvider;
        this.cacheManager = cacheManager;
        this.properties = properties;
        this.smartCacheProperties = smartCacheProperties;
        this.tokenRefreshExecutor = tokenRefreshExecutor;
        this.redisLock = redisLock;
        this.profileRegistry = profileRegistry;
    }

    private static Object[] createLocalLocks() {
        Object[] locks = new Object[SimpleAkskRedisTokenManagerConstant.LOCAL_LOCK_STRIPE_COUNT];
        for (int i = 0; i < locks.length; i++) {
            locks[i] = new Object();
        }
        return locks;
    }

    @Override
    public String getToken() {
        return getToken(SimpleAkskClientCoreConstant.DEFAULT_PROFILE);
    }

    /**
     * 按Profile获取Token
     *
     * <p>命名Profile使用独立凭据与缓存key；{@code default}或空值等价于{@link #getToken()}。
     * 未配置默认凭据时取默认Profile、或请求不存在的Profile，抛出配置异常，不回落到其他AKP。
     *
     * @param profile Profile名称，空值按default处理
     * @return 访问令牌
     */
    @Override
    public String getToken(String profile) {
        String cacheName = properties.getRedis().getToken().getCacheName();
        if (profileRegistry == null) {
            String securityContext = securityContextProvider.getSecurityContext();
            String cacheKey = CacheKeyHelper.generate(securityContext);
            return getOrCreateToken(null, null, securityContext, cacheName, cacheKey, true);
        }
        AkskClientProfile target = profileRegistry.resolve(profile);
        String credentialHash = profileRegistry.credentialHash(target);
        String securityContext = securityContextProvider.getSecurityContext();
        String cacheKey = ProfileCacheKeyHelper.generate(target.getProfile(), credentialHash, securityContext);
        return getOrCreateToken(target, credentialHash, securityContext, cacheName, cacheKey, false);
    }

    @Override
    public void clearToken() {
        clearToken(SimpleAkskClientCoreConstant.DEFAULT_PROFILE);
    }

    /**
     * 清理指定Profile的Token缓存，不影响其他Profile
     *
     * @param profile Profile名称，空值按default处理
     */
    @Override
    public void clearToken(String profile) {
        String cacheName = properties.getRedis().getToken().getCacheName();
        String displayProfile = StringUtils.hasText(profile) ? profile : SimpleAkskClientCoreConstant.DEFAULT_PROFILE;
        String cacheKey;
        if (profileRegistry == null) {
            cacheKey = CacheKeyHelper.generate(securityContextProvider.getSecurityContext());
        } else {
            AkskClientProfile target = profileRegistry.resolve(profile);
            String credentialHash = profileRegistry.credentialHash(target);
            cacheKey = ProfileCacheKeyHelper.generate(
                    target.getProfile(), credentialHash, securityContextProvider.getSecurityContext());
        }
        try {
            cacheManager.evict(cacheName, cacheKey);
        } catch (RuntimeException exception) {
            log.debug("Token 缓存清理失败，阶段: evict, profile: {}", displayProfile);
            throw tokenFetchException();
        }
        log.debug("Token 缓存已清理, profile: {}", displayProfile);
    }

    /**
     * 判断Profile是否已配置（含default）
     *
     * @param profile Profile名称
     * @return 已配置返回true
     */
    @Override
    public boolean containsProfile(String profile) {
        return profileRegistry != null && profileRegistry.contains(profile);
    }

    private AkskClientProfileRegistry requireRegistry(String profile) {
        if (profileRegistry == null) {
            throw new ConfigurationException(
                    ClientErrorCode.CONFIG_MISSING,
                    String.format(ClientErrorMessage.CONFIG_MISSING, "AKP Profile Registry"));
        }
        return profileRegistry;
    }

    private String getOrCreateToken(AkskClientProfile profile, String credentialHash,
                                    String securityContext, String cacheName, String cacheKey,
                                    boolean legacyMetadata) {
        TokenWithExpiry cached = getCachedToken(cacheName, cacheKey);
        if (cached != null && isCacheValid(cached, profile, credentialHash, legacyMetadata)) {
            return cached.getToken();
        }
        return fetchWithLocalLock(profile, credentialHash, securityContext, cacheName, cacheKey, legacyMetadata);
    }

    private boolean isCacheValid(TokenWithExpiry cached, AkskClientProfile profile,
                                 String credentialHash, boolean legacyMetadata) {
        if (legacyMetadata) {
            return true;
        }
        String expectedProfile = profile == null ? SimpleAkskClientCoreConstant.DEFAULT_PROFILE : profile.getProfile();
        return expectedProfile.equals(cached.getProfile())
                && credentialHash != null && credentialHash.equals(cached.getCredentialHash());
    }

    private String fetchWithLocalLock(AkskClientProfile profile, String credentialHash,
                                      String securityContext, String cacheName, String cacheKey,
                                      boolean legacyMetadata) {
        synchronized (getLocalLock(cacheKey)) {
            TokenWithExpiry fromCache = getCachedToken(cacheName, cacheKey);
            if (fromCache != null && isCacheValid(fromCache, profile, credentialHash, legacyMetadata)) {
                return fromCache.getToken();
            }
            return fetchWithDistributedLock(profile, credentialHash, securityContext,
                    cacheName, cacheKey, legacyMetadata);
        }
    }

    private String fetchWithDistributedLock(AkskClientProfile profile, String credentialHash,
                                            String securityContext, String cacheName, String cacheKey,
                                            boolean legacyMetadata) {
        String lockKey = buildLockKey(cacheName, cacheKey);
        String requestId = UUID.randomUUID().toString();
        boolean locked = false;

        try {
            int lockTimeout = smartCacheProperties.getLock() != null
                    ? smartCacheProperties.getLock().getTimeoutSeconds()
                    : SimpleAkskRedisTokenManagerConstant.DEFAULT_LOCK_TIMEOUT_SECONDS;
            try {
                locked = redisLock.tryLock(lockKey, requestId, lockTimeout, TimeUnit.SECONDS);
            } catch (Exception exception) {
                log.warn("获取分布式锁失败，使用当前实例锁兜底, profile: {}", profileName(profile));
                return fetchAndCacheToken(profile, credentialHash, securityContext,
                        cacheName, cacheKey, legacyMetadata);
            }

            if (locked) {
                TokenWithExpiry fromL2 = getCachedToken(cacheName, cacheKey);
                if (fromL2 != null && isCacheValid(fromL2, profile, credentialHash, legacyMetadata)) {
                    return fromL2.getToken();
                }
                return fetchAndCacheToken(profile, credentialHash, securityContext,
                        cacheName, cacheKey, legacyMetadata);
            }
            return waitForTokenFromL2(profile, credentialHash, securityContext, cacheName, cacheKey,
                    lockTimeout, legacyMetadata);
        } finally {
            if (locked) {
                try {
                    redisLock.unlock(lockKey, requestId);
                } catch (Exception exception) {
                    log.warn("解锁失败, profile: {}", profileName(profile));
                }
            }
        }
    }

    private String fetchAndCacheToken(AkskClientProfile profile, String credentialHash,
                                      String securityContext, String cacheName, String cacheKey,
                                      boolean legacyMetadata) {
        long fetchTime = System.currentTimeMillis() / 1000;
        TokenWithExpiry[] holder = new TokenWithExpiry[1];
        if (profile == null) {
            tokenRefreshExecutor.fetchTokenFromServer(securityContext, (token, expiresIn) ->
                    holder[0] = new TokenWithExpiry(token, fetchTime + expiresIn, securityContext));
        } else {
            tokenRefreshExecutor.fetchTokenFromServer(profile, securityContext, (token, expiresIn) ->
                    holder[0] = new TokenWithExpiry(token, fetchTime + expiresIn, securityContext,
                            profile.getProfile(), credentialHash));
        }

        if (holder[0] == null) {
            throw tokenFetchException();
        }
        int ttl = TokenCacheTtlHelper.calculate(holder[0].getExpiresAt(), System.currentTimeMillis() / 1000);
        if (ttl <= 0) {
            throw tokenFetchException();
        }
        putCachedToken(cacheName, cacheKey, holder[0], ttl);
        log.debug("Token 已刷新并缓存, profile: {}, expiresIn: {}s", profileName(profile), ttl);
        return holder[0].getToken();
    }

    private String waitForTokenFromL2(AkskClientProfile profile, String credentialHash,
                                      String securityContext, String cacheName, String cacheKey,
                                      int lockTimeout, boolean legacyMetadata) {
        int retryCount = (int) (lockTimeout * 1000L / SimpleAkskRedisTokenManagerConstant.L2_POLL_INTERVAL_MS);
        for (int i = 0; i < retryCount; i++) {
            try {
                Thread.sleep(SimpleAkskRedisTokenManagerConstant.L2_POLL_INTERVAL_MS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                break;
            }
            TokenWithExpiry cached = getCachedToken(cacheName, cacheKey);
            if (cached != null && isCacheValid(cached, profile, credentialHash, legacyMetadata)) {
                return cached.getToken();
            }
        }
        log.warn("等待 L2 token 超时，使用当前实例锁刷新, profile: {}", profileName(profile));
        return fetchAndCacheToken(profile, credentialHash, securityContext, cacheName, cacheKey, legacyMetadata);
    }

    private String buildLockKey(String cacheName, String cacheKey) {
        return KeyHelper.buildLockKey(
                smartCacheProperties.getKeyPrefix(),
                cacheName,
                smartCacheProperties.getMe(),
                cacheKey
        );
    }

    private TokenWithExpiry getCachedToken(String cacheName, String cacheKey) {
        try {
            return cacheManager.get(cacheName, cacheKey, TokenWithExpiry.class);
        } catch (RuntimeException exception) {
            log.debug("Token 缓存读取失败，阶段: get");
            throw tokenFetchException();
        }
    }

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

    private String profileName(AkskClientProfile profile) {
        return profile == null ? SimpleAkskClientCoreConstant.DEFAULT_PROFILE : profile.getProfile();
    }
}
