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
}
