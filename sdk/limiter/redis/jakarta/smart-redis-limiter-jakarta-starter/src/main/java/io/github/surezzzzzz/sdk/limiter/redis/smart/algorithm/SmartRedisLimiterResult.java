package io.github.surezzzzzz.sdk.limiter.redis.smart.algorithm;

import lombok.Builder;
import lombok.Getter;

/**
 * 限流检查结果
 *
 * @author Sure.
 * @Date: 2026-05-11
 */
@Getter
@Builder
public class SmartRedisLimiterResult {

    /**
     * 是否通过
     */
    private final boolean passed;

    /**
     * 限流阈值（时间窗口内允许的最大请求数）
     */
    private final long limit;

    /**
     * 剩余配额
     */
    private final long remaining;

    /**
     * 窗口重置的 Unix 时间戳（秒）
     */
    private final long resetAt;

    /**
     * 是否为降级结果
     */
    private final boolean fallback;

    /**
     * 降级原因
     */
    private final String fallbackReason;

    /**
     * 路由 Key
     */
    private final String routeKey;

    /**
     * Redis datasource key
     */
    private final String datasourceKey;

    /**
     * Redis 模式
     */
    private final String redisMode;

    /**
     * 是否要求通过 redis-route 执行
     */
    private final boolean routeRequired;

    /**
     * 是否成功解析到 datasource
     */
    private final boolean routeResolved;
}
