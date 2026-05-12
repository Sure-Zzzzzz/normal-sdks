package io.github.surezzzzzz.sdk.limiter.redis.smart.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * SmartRedisLimiter Key生成策略枚举
 *
 * @author surezzzzzz
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
     *
     * @param code 策略代码
     * @return 枚举，如果不存在返回 null
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
     * 判断代码是否有效
     *
     * @param code 策略代码
     * @return true 有效，false 无效
     */
    public static boolean isValid(String code) {
        return fromCode(code) != null;
    }

    /**
     * 获取所有有效的代码
     *
     * @return 代码数组
     */
    public static String[] getAllCodes() {
        SmartRedisLimiterKeyStrategy[] strategies = values();
        String[] codes = new String[strategies.length];
        for (int i = 0; i < strategies.length; i++) {
            codes[i] = strategies[i].code;
        }
        return codes;
    }

    /**
     * 获取Bean名称（支持自定义）
     */
    public static String getBeanName(String code) {
        SmartRedisLimiterKeyStrategy strategy = fromCode(code);
        return strategy != null ? strategy.beanName : code;
    }

    @Override
    public String toString() {
        return code;
    }
}
