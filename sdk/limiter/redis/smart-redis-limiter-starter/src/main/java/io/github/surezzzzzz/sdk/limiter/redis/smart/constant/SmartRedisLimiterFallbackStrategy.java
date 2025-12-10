package io.github.surezzzzzz.sdk.limiter.redis.smart.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author: Sure.
 * @description Redis异常降级策略
 * @Date: 2024/12/XX XX:XX
 */
@Getter
@AllArgsConstructor
public enum SmartRedisLimiterFallbackStrategy {

    /**
     * 放行请求
     */
    ALLOW("allow", "Redis异常时放行请求"),

    /**
     * 拒绝请求
     */
    DENY("deny", "Redis异常时拒绝请求");

    private final String code;
    private final String desc;

    public static SmartRedisLimiterFallbackStrategy fromCode(String code) {
        if (code == null) {
            return ALLOW;
        }
        for (SmartRedisLimiterFallbackStrategy strategy : values()) {
            if (strategy.code.equalsIgnoreCase(code)) {
                return strategy;
            }
        }
        return ALLOW;
    }
}
