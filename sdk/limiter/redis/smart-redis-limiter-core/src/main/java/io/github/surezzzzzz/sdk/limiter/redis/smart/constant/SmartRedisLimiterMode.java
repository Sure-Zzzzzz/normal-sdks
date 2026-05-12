package io.github.surezzzzzz.sdk.limiter.redis.smart.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * SmartRedisLimiter 限流模式枚举
 *
 * @author surezzzzzz
 */
@Getter
@AllArgsConstructor
public enum SmartRedisLimiterMode {

    /**
     * 注解模式
     */
    ANNOTATION("annotation", "注解模式"),

    /**
     * 拦截器模式
     */
    INTERCEPTOR("interceptor", "拦截器模式"),

    /**
     * 双模式（注解+拦截器）
     */
    BOTH("both", "双模式");

    private final String code;
    private final String desc;

    /**
     * 根据代码获取枚举
     *
     * @param code 模式代码
     * @return 枚举，如果不存在返回 BOTH
     */
    public static SmartRedisLimiterMode fromCode(String code) {
        if (code == null) {
            return BOTH;
        }
        for (SmartRedisLimiterMode mode : values()) {
            if (mode.code.equalsIgnoreCase(code)) {
                return mode;
            }
        }
        return BOTH;
    }

    /**
     * 判断代码是否有效
     *
     * @param code 模式代码
     * @return true 有效，false 无效
     */
    public static boolean isValid(String code) {
        for (SmartRedisLimiterMode mode : values()) {
            if (mode.code.equalsIgnoreCase(code)) {
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
        SmartRedisLimiterMode[] modes = values();
        String[] codes = new String[modes.length];
        for (int i = 0; i < modes.length; i++) {
            codes[i] = modes[i].code;
        }
        return codes;
    }

    /**
     * 是否启用注解模式
     */
    public boolean isAnnotationEnabled() {
        return this == ANNOTATION || this == BOTH;
    }

    /**
     * 是否启用拦截器模式
     */
    public boolean isInterceptorEnabled() {
        return this == INTERCEPTOR || this == BOTH;
    }

    @Override
    public String toString() {
        return code;
    }
}
