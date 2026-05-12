package io.github.surezzzzzz.sdk.limiter.redis.smart.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * SmartRedisLimiter 上下文属性键枚举
 *
 * @author surezzzzzz
 */
@Getter
@AllArgsConstructor
public enum SmartRedisLimiterContextAttribute {

    /**
     * 请求路径
     */
    REQUEST_PATH("requestPath", "请求路径"),

    /**
     * 请求方法（HTTP Method）
     */
    REQUEST_METHOD("requestMethod", "请求方法"),

    /**
     * 客户端IP
     */
    CLIENT_IP("clientIp", "客户端IP"),

    /**
     * 匹配到的路径模式
     */
    MATCHED_PATH_PATTERN("matchedPathPattern", "匹配到的路径模式"),

    /**
     * 限流检查耗时（纳秒）
     */
    DURATION_NANOS("durationNanos", "限流检查耗时"),

    /**
     * 是否触发降级
     */
    FALLBACK("fallback", "是否触发降级"),

    /**
     * 降级策略
     */
    FALLBACK_STRATEGY("fallbackStrategy", "降级策略");

    private final String key;
    private final String desc;
}
