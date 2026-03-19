package io.github.surezzzzzz.sdk.cache.stats;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Cache Stats
 * <p>
 * 缓存统计数据模型
 * </p>
 *
 * @author Sure
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CacheStats {

    /**
     * 总命中率（L1 + L2）
     */
    private double hitRate;

    /**
     * L1 缓存命中率
     */
    private double l1HitRate;

    /**
     * L2 缓存命中率
     */
    private double l2HitRate;

    /**
     * 总请求数
     */
    private long totalRequests;

    /**
     * L1 命中数
     */
    private long l1HitCount;

    /**
     * L2 命中数
     */
    private long l2HitCount;

    /**
     * 未命中数
     */
    private long missCount;

    /**
     * 缓存大小（L1 + L2）
     */
    private long cacheSize;

    /**
     * L1 缓存大小
     */
    private long l1Size;

    /**
     * L2 缓存大小
     */
    private long l2Size;
}
