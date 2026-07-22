package io.github.surezzzzzz.sdk.limiter.redis.smart.management.support;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Management 时间精度 Helper
 *
 * @author surezzzzzz
 */
public final class SmartRedisLimiterManagementTimeHelper {

    private SmartRedisLimiterManagementTimeHelper() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 获取当前 UTC 毫秒精度时间
     *
     * @return 当前时间
     */
    public static Instant nowMillis() {
        return Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }
}
