package io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.support;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.annotation.SimpleAkskResourceServerComponent;
import io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.configuration.SimpleAkskResourceServerProperties;
import io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.constant.SimpleAkskResourceServerConstant;
import io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.model.IntrospectResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Introspect 本地缓存 Helper
 *
 * <p>在 resource-server-starter 侧缓存 introspect 结果，消除热路径的 HTTP 往返。
 * 主缓存默认开启，TTL 默认 3s，撤销感知延迟 = TTL。
 *
 * <p>可选开启兜底缓存（fallback.enabled=true），端点不可用时用兜底缓存放行。
 * 兜底缓存 TTL = expire-seconds × stale-ttl-multiplier，只对 active=true 的条目兜底。
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleAkskResourceServerComponent
@RequiredArgsConstructor
public class IntrospectLocalCacheHelper {

    private final SimpleAkskResourceServerProperties properties;

    private Cache<String, IntrospectResult> cache;
    private Cache<String, IntrospectResult> fallbackCache;

    private final AtomicLong fallbackHitCount = new AtomicLong(0);
    private volatile long lastStatsLogTime = 0;

    @PostConstruct
    public void init() {
        SimpleAkskResourceServerProperties.Introspect.LocalCacheConfig config =
                properties.getIntrospect().getLocalCache();

        if (!config.isEnabled()) {
            log.info("Introspect local cache is disabled");
            return;
        }

        cache = Caffeine.newBuilder()
                .expireAfterWrite(config.getExpireSeconds(), TimeUnit.SECONDS)
                .maximumSize(config.getMaxSize())
                .recordStats()
                .build();
        log.info("Introspect local cache initialized: expireSeconds={}, maxSize={}",
                config.getExpireSeconds(), config.getMaxSize());

        SimpleAkskResourceServerProperties.Introspect.LocalCacheConfig.FallbackConfig fallbackConfig =
                config.getFallback();

        if (!fallbackConfig.isEnabled()) {
            log.info("Introspect fallback cache is disabled");
            return;
        }

        int multiplier = fallbackConfig.getStaleTtlMultiplier();
        if (multiplier < SimpleAkskResourceServerConstant.MIN_STALE_TTL_MULTIPLIER) {
            log.warn("stale-ttl-multiplier={} is less than recommended minimum {}, fallback TTL may be too short",
                    multiplier, SimpleAkskResourceServerConstant.MIN_STALE_TTL_MULTIPLIER);
        }
        if (multiplier > SimpleAkskResourceServerConstant.WARN_STALE_TTL_MULTIPLIER_MAX) {
            log.warn("stale-ttl-multiplier={} exceeds recommended maximum {}, revoked tokens may be accepted for up to {}s during outage",
                    multiplier, SimpleAkskResourceServerConstant.WARN_STALE_TTL_MULTIPLIER_MAX,
                    config.getExpireSeconds() * multiplier);
        }

        long fallbackTtlSeconds = (long) config.getExpireSeconds() * multiplier;
        fallbackCache = Caffeine.newBuilder()
                .expireAfterWrite(fallbackTtlSeconds, TimeUnit.SECONDS)
                .maximumSize(fallbackConfig.getStaleMaxSize())
                .build();
        log.info("Introspect fallback cache initialized: ttlSeconds={}, maxSize={}",
                fallbackTtlSeconds, fallbackConfig.getStaleMaxSize());
    }

    /**
     * 从主缓存获取 introspect 结果
     *
     * @param token token value
     * @return 缓存结果，未命中或缓存未启用时返回 null
     */
    public IntrospectResult get(String token) {
        if (cache == null) {
            return null;
        }
        return cache.getIfPresent(token);
    }

    /**
     * 从兜底缓存获取结果，仅在降级路径使用
     *
     * @param token token value
     * @return 兜底缓存结果，未命中或兜底缓存未启用时返回 null
     */
    public IntrospectResult getFallback(String token) {
        if (fallbackCache == null) {
            return null;
        }
        return fallbackCache.getIfPresent(token);
    }

    /**
     * 写入主缓存和兜底缓存（含 active=false，使撤销信息尽快传播到兜底层）
     *
     * @param token  token value
     * @param result introspect 结果
     */
    public void put(String token, IntrospectResult result) {
        if (cache != null) {
            cache.put(token, result);
        }
        if (fallbackCache != null) {
            fallbackCache.put(token, result);
        }
    }

    /**
     * 是否启用主缓存
     *
     * @return true 表示主缓存已初始化并启用
     */
    public boolean isEnabled() {
        return cache != null;
    }

    /**
     * 是否启用兜底缓存
     *
     * @return true 表示兜底缓存已初始化并启用
     */
    public boolean isFallbackEnabled() {
        return fallbackCache != null;
    }

    /**
     * 记录一次兜底命中
     */
    public void incrementFallbackHit() {
        fallbackHitCount.incrementAndGet();
    }

    /**
     * 触发缓存同步清理，驱逐超出 maxSize 或已过期的条目
     *
     * <p>Caffeine 默认异步淘汰，此方法用于需要立即感知淘汰结果的场景（如测试）。
     */
    public void cleanUp() {
        if (cache != null) {
            cache.cleanUp();
        }
    }

    /**
     * miss 时按时间间隔打印统计日志
     */
    public void logStatsIfNeeded() {
        if (cache == null) {
            return;
        }
        long now = System.currentTimeMillis();
        int intervalMs = properties.getIntrospect().getLocalCache().getStatsLogIntervalSeconds() * 1000;
        if (now - lastStatsLogTime >= intervalMs) {
            lastStatsLogTime = now;
            CacheStats stats = cache.stats();
            log.info("Introspect cache stats: hitCount={}, missCount={}, hitRate={:.1f}%, evictionCount={}, fallbackHitCount={}",
                    stats.hitCount(), stats.missCount(), stats.hitRate() * 100,
                    stats.evictionCount(), fallbackHitCount.get());
        }
    }

    @PreDestroy
    public void destroy() {
        if (cache != null) {
            CacheStats stats = cache.stats();
            log.info("Final introspect cache stats: hitCount={}, missCount={}, hitRate={:.1f}%, evictionCount={}, fallbackHitCount={}",
                    stats.hitCount(), stats.missCount(), stats.hitRate() * 100,
                    stats.evictionCount(), fallbackHitCount.get());
        }
    }
}
