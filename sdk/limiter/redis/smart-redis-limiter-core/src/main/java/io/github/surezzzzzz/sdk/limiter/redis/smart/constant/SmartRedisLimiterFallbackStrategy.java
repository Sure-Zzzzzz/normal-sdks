package io.github.surezzzzzz.sdk.limiter.redis.smart.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * SmartRedisLimiter Redis异常降级策略枚举
 *
 * @author surezzzzzz
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

    /**
     * 常量：用于注解，避免硬编码
     */
    public static final String ALLOW_CODE = "allow";

    /**
     * 常量：用于注解，避免硬编码
     */
    public static final String DENY_CODE = "deny";

    private final String code;
    private final String desc;

    /**
     * 根据代码获取枚举
     *
     * @param code 策略代码
     * @return 枚举，如果不存在返回 ALLOW
     */
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

    /**
     * 判断代码是否有效
     *
     * @param code 策略代码
     * @return true 有效，false 无效
     */
    public static boolean isValid(String code) {
        for (SmartRedisLimiterFallbackStrategy strategy : values()) {
            if (strategy.code.equalsIgnoreCase(code)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取所有有效的代码
     *
     * @return 代码数组
     */
    public static String[] getAllCodes() {
        SmartRedisLimiterFallbackStrategy[] strategies = values();
        String[] codes = new String[strategies.length];
        for (int i = 0; i < strategies.length; i++) {
            codes[i] = strategies[i].code;
        }
        return codes;
    }

    @Override
    public String toString() {
        return code;
    }
}
