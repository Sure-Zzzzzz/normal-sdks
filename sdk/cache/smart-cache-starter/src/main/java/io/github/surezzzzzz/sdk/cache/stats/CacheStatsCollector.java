package io.github.surezzzzzz.sdk.cache.stats;

import io.github.surezzzzzz.sdk.cache.annotation.SmartCacheComponent;
import io.github.surezzzzzz.sdk.cache.constant.SmartCacheConstant;
import io.github.surezzzzzz.sdk.cache.layer.L1Cache;
import io.github.surezzzzzz.sdk.cache.layer.L2Cache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Cache Stats Collector
 * <p>
 * 缓存统计收集器
 * </p>
 *
 * @author Sure
 */
@Slf4j
@SmartCacheComponent
@ConditionalOnProperty(prefix = SmartCacheConstant.CONFIG_PREFIX + ".stats", name = SmartCacheConstant.PROPERTY_ENABLED,
        havingValue = SmartCacheConstant.PROPERTY_VALUE_TRUE, matchIfMissing = true)
public class CacheStatsCollector {

    @Autowired(required = false)
    private L1Cache l1Cache;

    @Autowired(required = false)
    private L2Cache l2Cache;

    // 使用 LongAdder 代替 AtomicLong，在高并发场景下性能更好
    private final Map<String, LongAdder> l1HitCountMap = new ConcurrentHashMap<>();
    private final Map<String, LongAdder> l2HitCountMap = new ConcurrentHashMap<>();
    private final Map<String, LongAdder> missCountMap = new ConcurrentHashMap<>();

    /**
     * 记录 L1 命中
     */
    public void recordL1Hit(String cacheName) {
        l1HitCountMap.computeIfAbsent(cacheName, k -> new LongAdder()).increment();
    }

    /**
     * 记录 L2 命中
     */
    public void recordL2Hit(String cacheName) {
        l2HitCountMap.computeIfAbsent(cacheName, k -> new LongAdder()).increment();
    }

    /**
     * 记录未命中
     */
    public void recordMiss(String cacheName) {
        missCountMap.computeIfAbsent(cacheName, k -> new LongAdder()).increment();
    }

    /**
     * 获取统计信息
     */
    public CacheStats getStats(String cacheName) {
        // 原子性地获取所有计数器的快照
        long l1HitCount = l1HitCountMap.getOrDefault(cacheName, new LongAdder()).sum();
        long l2HitCount = l2HitCountMap.getOrDefault(cacheName, new LongAdder()).sum();
        long missCount = missCountMap.getOrDefault(cacheName, new LongAdder()).sum();

        // 使用快照值计算统计信息，确保一致性
        long totalRequests = l1HitCount + l2HitCount + missCount;

        double hitRate = totalRequests > 0 ? (l1HitCount + l2HitCount) * 100.0 / totalRequests : 0;
        double l1HitRate = totalRequests > 0 ? l1HitCount * 100.0 / totalRequests : 0;
        double l2HitRate = totalRequests > 0 ? l2HitCount * 100.0 / totalRequests : 0;

        // 获取缓存大小时也可能有并发问题，但影响较小
        long l1Size = l1Cache != null ? l1Cache.size(cacheName) : 0;
        long l2Size = l2Cache != null ? l2Cache.size(cacheName) : 0;

        return CacheStats.builder()
                .hitRate(hitRate)
                .l1HitRate(l1HitRate)
                .l2HitRate(l2HitRate)
                .totalRequests(totalRequests)
                .l1HitCount(l1HitCount)
                .l2HitCount(l2HitCount)
                .missCount(missCount)
                .cacheSize(l1Size + l2Size)
                .l1Size(l1Size)
                .l2Size(l2Size)
                .build();
    }

    /**
     * 重置指定缓存的统计信息
     *
     * @param cacheName 缓存名称
     */
    public void resetStats(String cacheName) {
        l1HitCountMap.remove(cacheName);
        l2HitCountMap.remove(cacheName);
        missCountMap.remove(cacheName);
    }
}
