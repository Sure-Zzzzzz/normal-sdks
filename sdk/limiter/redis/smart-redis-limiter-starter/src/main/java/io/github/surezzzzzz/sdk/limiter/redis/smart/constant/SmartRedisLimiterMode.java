package io.github.surezzzzzz.sdk.limiter.redis.smart.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author: Sure.
 * @description 智能限流模式枚举
 * @Date: 2024/12/XX XX:XX
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
}
