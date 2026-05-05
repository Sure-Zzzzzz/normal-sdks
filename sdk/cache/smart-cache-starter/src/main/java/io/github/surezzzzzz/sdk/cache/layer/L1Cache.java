package io.github.surezzzzzz.sdk.cache.layer;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.surezzzzzz.sdk.cache.annotation.SmartCacheComponent;
import io.github.surezzzzzz.sdk.cache.configuration.SmartCacheProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import javax.annotation.PreDestroy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * L1 Cache (Caffeine)
 * <p>
 * 本地缓存封装
 * </p>
 *
 * @author Sure
 */
@Slf4j
@SmartCacheComponent
@ConditionalOnClass(Caffeine.class)
@ConditionalOnProperty(prefix = "io.github.surezzzzzz.sdk.cache.l1", name = "enabled", havingValue = "true", matchIfMissing = true)
public class L1Cache {

    private final SmartCacheProperties properties;
    private final Map<String, Cache<String, Object>> cacheMap = new ConcurrentHashMap<>();
    private final AtomicInteger threadCounter = new AtomicInteger(0);
    private final Executor refreshExecutor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "l1-cache-refresh-" + threadCounter.incrementAndGet());
        t.setDaemon(true);
        return t;
    });

    @Autowired(required = false)
    private L2Cache l2Cache;

    public L1Cache(SmartCacheProperties properties) {
        this.properties = properties;
    }

    /**
     * 获取或创建缓存实例
     */
    private Cache<String, Object> getCache(String cacheName) {
        return cacheMap.computeIfAbsent(cacheName, name -> {
            SmartCacheProperties.L1Config l1Config = properties != null && properties.getL1() != null
                    ? properties.getL1()
                    : new SmartCacheProperties.L1Config();

            Caffeine<Object, Object> builder = Caffeine.newBuilder()
                    .maximumSize(l1Config.getMaxSize())
                    .expireAfterWrite(l1Config.getExpireSeconds(), TimeUnit.SECONDS)
                    .recordStats();

            // 如果配置了刷新时间，启用异步刷新
            if (l1Config.getRefreshSeconds() > 0 && l1Config.getRefreshSeconds() < l1Config.getExpireSeconds()) {
                builder.refreshAfterWrite(l1Config.getRefreshSeconds(), TimeUnit.SECONDS);
                builder.executor(refreshExecutor);
                // 创建 LoadingCache 以支持异步刷新
                return builder.build(key -> {
                    // 异步刷新时，从 L2 加载数据
                    if (l2Cache != null) {
                        try {
                            Object value = l2Cache.get(cacheName, key);
                            if (value != null) {
                                log.debug("L1 cache async refresh from L2: cacheName={}, key={}", cacheName, key);
                                return value;
                            }
                        } catch (Exception e) {
                            log.warn("L1 cache async refresh from L2 failed: cacheName={}, key={}, error={}",
                                    cacheName, key, e.getMessage());
                        }
                    }
                    // L2 也没有或加载失败，返回 null 让 Caffeine 保留旧值
                    // 注意：Caffeine 的 refreshAfterWrite 在 loader 返回 null 时会保留旧值
                    log.debug("L1 cache async refresh: no data from L2, keeping old value: cacheName={}, key={}",
                            cacheName, key);
                    return null;
                });
            }

            return builder.build();
        });
    }

    /**
     * 获取缓存值
     */
    public <T> T get(String cacheName, String key) {
        Cache<String, Object> cache = getCache(cacheName);
        Object value = cache.getIfPresent(key);
        return value != null ? (T) value : null;
    }

    /**
     * 获取缓存值，如果不存在则加载
     */
    public <T> T get(String cacheName, String key, Function<String, T> loader) {
        Cache<String, Object> cache = getCache(cacheName);
        Object value = cache.get(key, k -> loader.apply(k));
        return value != null ? (T) value : null;
    }

    /**
     * 设置缓存值
     */
    public void put(String cacheName, String key, Object value) {
        if (value == null) {
            return;
        }
        Cache<String, Object> cache = getCache(cacheName);
        cache.put(key, value);
    }

    /**
     * 删除缓存值
     */
    public void evict(String cacheName, String key) {
        Cache<String, Object> cache = getCache(cacheName);
        cache.invalidate(key);
    }

    /**
     * 清空缓存
     */
    public void clear(String cacheName) {
        Cache<String, Object> cache = cacheMap.get(cacheName);
        if (cache != null) {
            cache.invalidateAll();
        }
    }

    /**
     * 获取缓存大小
     */
    public long size(String cacheName) {
        Cache<String, Object> cache = cacheMap.get(cacheName);
        return cache != null ? cache.estimatedSize() : 0;
    }

    /**
     * 获取缓存统计信息
     */
    public com.github.benmanes.caffeine.cache.stats.CacheStats getStats(String cacheName) {
        Cache<String, Object> cache = cacheMap.get(cacheName);
        return cache != null ? cache.stats() : null;
    }

    /**
     * 批量获取缓存值
     *
     * @param cacheName 缓存名称
     * @param keys      缓存键列表
     * @return 缓存值 Map
     */
    public <T> Map<String, T> getAll(String cacheName, List<String> keys) {
        Map<String, T> result = new HashMap<>();
        if (keys == null || keys.isEmpty()) {
            return result;
        }
        Cache<String, Object> cache = getCache(cacheName);
        Map<String, Object> allPresent = cache.getAllPresent(keys);
        for (String key : keys) {
            Object value = allPresent.get(key);
            if (value != null) {
                result.put(key, (T) value);
            }
        }
        return result;
    }

    /**
     * 批量设置缓存值
     *
     * @param cacheName 缓存名称
     * @param entries   缓存键值对
     */
    public void putAll(String cacheName, Map<String, Object> entries) {
        if (entries == null || entries.isEmpty()) {
            return;
        }
        Cache<String, Object> cache = getCache(cacheName);
        Map<String, Object> nonNullEntries = new HashMap<>();
        entries.forEach((key, value) -> {
            if (value != null) {
                nonNullEntries.put(key, value);
            }
        });
        cache.putAll(nonNullEntries);
    }

    /**
     * 销毁时关闭线程池，释放资源
     * 使用 @PreDestroy 确保在 Spring 容器关闭时执行
     * 注意：如果其他 Bean 依赖 L1Cache，需要确保它们先于 L1Cache 销毁
     */
    @PreDestroy
    public void destroy() {
        log.info("L1 Cache destroying...");

        // 先清理所有缓存，停止新的刷新任务
        cacheMap.values().forEach(Cache::invalidateAll);
        cacheMap.clear();

        // 关闭线程池
        if (refreshExecutor instanceof ExecutorService) {
            ExecutorService executorService = (ExecutorService) refreshExecutor;
            executorService.shutdown();
            try {
                // 等待正在执行的任务完成
                if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                    log.warn("L1 Cache refresh executor did not terminate in time, forcing shutdown");
                    executorService.shutdownNow();
                    // 等待强制关闭完成
                    if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                        log.error("L1 Cache refresh executor did not terminate after forced shutdown");
                    }
                }
            } catch (InterruptedException e) {
                log.error("L1 Cache destroy interrupted, forcing shutdown", e);
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        log.info("L1 Cache destroyed successfully");
    }
}
