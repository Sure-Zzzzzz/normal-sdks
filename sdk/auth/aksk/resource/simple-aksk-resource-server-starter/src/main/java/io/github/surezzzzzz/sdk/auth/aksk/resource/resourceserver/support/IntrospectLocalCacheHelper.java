package io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.support;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.annotation.SimpleAkskResourceServerComponent;
import io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.configuration.SimpleAkskResourceServerProperties;
import io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.model.IntrospectResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.PostConstruct;
import java.util.concurrent.TimeUnit;

/**
 * Introspect 本地缓存 Helper
 *
 * <p>在 resource-server-starter 侧缓存 introspect 结果，消除热路径的 HTTP 往返。
 * 缓存默认开启，TTL 默认 3s，撤销感知延迟 = TTL。
 * 可通过 {@code introspect.local-cache.enabled=false} 关闭。
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleAkskResourceServerComponent
@RequiredArgsConstructor
public class IntrospectLocalCacheHelper {

    private final SimpleAkskResourceServerProperties properties;

    private Cache<String, IntrospectResult> cache;

    @PostConstruct
    public void init() {
        SimpleAkskResourceServerProperties.Introspect.LocalCacheConfig config =
                properties.getIntrospect().getLocalCache();
        if (!config.isEnabled()) {
            log.debug("Introspect local cache is disabled");
            return;
        }
        cache = Caffeine.newBuilder()
                .expireAfterWrite(config.getExpireSeconds(), TimeUnit.SECONDS)
                .maximumSize(config.getMaxSize())
                .build();
        log.info("Introspect local cache initialized: expireSeconds={}, maxSize={}",
                config.getExpireSeconds(), config.getMaxSize());
    }

    /**
     * 从本地缓存获取 introspect 结果
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
     * 写入本地缓存
     *
     * @param token  token value
     * @param result introspect 结果
     */
    public void put(String token, IntrospectResult result) {
        if (cache == null) {
            return;
        }
        cache.put(token, result);
    }

    /**
     * 是否启用本地缓存
     *
     * @return true 表示缓存已初始化并启用
     */
    public boolean isEnabled() {
        return cache != null;
    }
}
