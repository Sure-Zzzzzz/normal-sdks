package io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.preload;

import io.github.surezzzzzz.sdk.auth.aksk.client.core.executor.TokenRefreshExecutor;
import io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.annotation.SimpleAkskRedisTokenManagerComponent;
import io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.configuration.SimpleAkskRedisTokenManagerProperties;
import io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.model.TokenWithExpiry;
import io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.support.TokenCacheTtlHelper;
import io.github.surezzzzzz.sdk.cache.CachePreloadHandler;
import io.github.surezzzzzz.sdk.cache.manager.SmartCacheManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

import java.util.Optional;

/**
 * Token Cache Preload Handler
 *
 * <p>接入 smart-cache 的 L2 预刷新机制。
 * 通过实现 {@link CachePreloadHandler}，在 Redis TTL 降至 beforeExpireSeconds 时触发异步刷新。
 *
 * <p>reload() 从 Redis 读取当前 TokenWithExpiry，提取 securityContext 后向 server 换取新 token，
 * 保证分布式多实例使用相同的 securityContext。
 *
 * <p>TTL 契约：reload() 只返回新值不写缓存，写回由框架 asyncPreload 统一 put；
 * 新值的服务端 TTL 经 {@link #RELOAD_EXPIRES_AT} 在同一线程内传给 {@link #getReloadTtlSeconds}，
 * 避免框架回退全局 l2.expire-seconds 覆盖服务端 expiresIn（详见 DESIGN.md §5.3）。
 *
 * @author surezzzzzz
 */
@SimpleAkskRedisTokenManagerComponent
public class TokenCachePreloadHandler implements CachePreloadHandler {

    /**
     * reload() 与 getReloadTtlSeconds() 由框架在同一 preload worker 线程内顺序调用，
     * 用 ThreadLocal 传递新值 expiresAt，避免框架写回时回退全局 TTL
     */
    private static final ThreadLocal<Long> RELOAD_EXPIRES_AT = new ThreadLocal<>();
    private final TokenRefreshExecutor tokenRefreshExecutor;
    private final SimpleAkskRedisTokenManagerProperties properties;
    private SmartCacheManager cacheManager;

    @Autowired
    public TokenCachePreloadHandler(
            TokenRefreshExecutor tokenRefreshExecutor,
            SimpleAkskRedisTokenManagerProperties properties,
            @Lazy SmartCacheManager cacheManager) {
        this.tokenRefreshExecutor = tokenRefreshExecutor;
        this.properties = properties;
        this.cacheManager = cacheManager;
    }

    /**
     * 判断是否由本 Handler 处理该缓存的预刷新
     *
     * @param cacheName 缓存名称
     * @return 仅当 cacheName 等于配置的值时才处理
     */
    @Override
    public boolean support(String cacheName) {
        return properties.getRedis().getToken().getCacheName().equals(cacheName);
    }

    /**
     * 判断是否需要预刷新
     *
     * <p>2.0.0 由框架根据 Redis TTL 自动判断，此方法返回 {@code Optional.empty()} 交由框架决定。
     *
     * @param cacheName 缓存名称
     * @param key       缓存键
     * @param value     当前缓存值（可能为 null）
     * @return 始终返回 empty，由框架 TTL 机制决定是否 preload
     */
    @Override
    public Optional<Boolean> needPreload(String cacheName, String key, Object value) {
        return Optional.empty();
    }

    /**
     * 预刷新后写 L2 的 TTL（秒）
     *
     * <p>返回 reload() 记录的 expiresAt − now（服务端 expiresIn 驱动），读后即清理。
     * 未记录时返回 0（回退全局 l2.expire-seconds，仅作兜底）。
     *
     * @param cacheName 缓存名称
     * @param key       缓存键
     * @return TTL 秒数，0 表示使用全局配置
     */
    @Override
    public int getReloadTtlSeconds(String cacheName, String key) {
        Long expiresAt = RELOAD_EXPIRES_AT.get();
        if (expiresAt == null) {
            return 0;
        }
        RELOAD_EXPIRES_AT.remove();
        int ttl = TokenCacheTtlHelper.calculate(expiresAt, System.currentTimeMillis() / 1000);
        // 预刷新刚完成却已过期时返回 1，避免框架回退到全局 TTL 并把失效 Token 缓存更久。
        return Math.max(ttl, 1);
    }

    /**
     * 重新加载 Token
     *
     * <p>从 Redis 读取当前缓存值（包含 securityContext），向 OAuth2 Server 换取新 token。
     * 由框架保证：此方法仅在 L2 存在有效值（TTL > 0）时触发，securityContext 必定可读。
     *
     * @param cacheName 缓存名称
     * @param key       缓存键
     * @return 新 TokenWithExpiry，写入 L2；返回 null 时不更新缓存
     */
    @Override
    public Object reload(String cacheName, String key) {
        // 从 Redis 读取当前缓存值，提取 securityContext，保证分布式一致性
        // 类型化读取：smart-cache 2.x 对 Object.class 读取做 trusted-packages 白名单校验，
        // 内部模型必须显式指定类型，否则反序列化被拒、L2 恒 miss
        TokenWithExpiry current = cacheManager.get(cacheName, key, TokenWithExpiry.class);
        String securityContext = current != null ? current.getSecurityContext() : null;

        long fetchTime = System.currentTimeMillis() / 1000;
        TokenWithExpiry[] holder = new TokenWithExpiry[1];
        tokenRefreshExecutor.fetchTokenFromServer(securityContext, (token, expiresIn) -> {
            holder[0] = new TokenWithExpiry(token, fetchTime + expiresIn, securityContext);
        });

        if (holder[0] != null) {
            // 只返回新值，写回交框架 asyncPreload 统一 put，TTL 经 ThreadLocal 传给 getReloadTtlSeconds()；
            // 此处若自行 put，框架随后的 put 会用全局 TTL 覆盖服务端 expiresIn（DESIGN.md §5.3）
            RELOAD_EXPIRES_AT.set(holder[0].getExpiresAt());
            return holder[0];
        }
        throw new io.github.surezzzzzz.sdk.auth.aksk.client.core.exception.TokenFetchException(
                io.github.surezzzzzz.sdk.auth.aksk.client.core.constant.ClientErrorCode.TOKEN_FETCH_FAILED,
                String.format(io.github.surezzzzzz.sdk.auth.aksk.client.core.constant.ClientErrorMessage.TOKEN_FETCH_FAILED,
                        io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.constant.ErrorMessage.TOKEN_CACHE_PROCESSING_FAILED));
    }
}
