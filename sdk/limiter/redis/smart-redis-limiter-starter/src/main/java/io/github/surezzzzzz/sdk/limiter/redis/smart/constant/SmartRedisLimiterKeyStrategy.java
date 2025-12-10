package io.github.surezzzzzz.sdk.limiter.redis.smart.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author: Sure.
 * @description Key生成策略枚举
 * @Date: 2024/12/XX XX:XX
 */
@Getter
@AllArgsConstructor
public enum SmartRedisLimiterKeyStrategy {

    /**
     * 方法级别
     */
    METHOD("method", "smartRedisLimiterMethodKeyGenerator", "方法级别"),

    /**
     * 路径级别（独立限流）
     */
    PATH("path", "smartRedisLimiterPathKeyGenerator", "路径级别-独立限流"),

    /**
     * 路径模式级别（共享限流）
     */
    PATH_PATTERN("path-pattern", "smartRedisLimiterPathPatternKeyGenerator", "路径模式级别-共享限流"),

    /**
     * IP级别
     */
    IP("ip", "smartRedisLimiterIpKeyGenerator", "IP级别");

    /**
     * 策略代码
     */
    private final String code;

    /**
     * Bean名称
     */
    private final String beanName;

    /**
     * 描述
     */
    private final String desc;

    /**
     * 根据代码获取策略
     */
    public static SmartRedisLimiterKeyStrategy fromCode(String code) {
        if (code == null || code.isEmpty()) {
            return METHOD;
        }
        for (SmartRedisLimiterKeyStrategy strategy : values()) {
            if (strategy.code.equalsIgnoreCase(code)) {
                return strategy;
            }
        }
        return null;
    }

    /**
     * 获取Bean名称（支持自定义）
     */
    public static String getBeanName(String code) {
        SmartRedisLimiterKeyStrategy strategy = fromCode(code);
        return strategy != null ? strategy.beanName : code;
    }
}
