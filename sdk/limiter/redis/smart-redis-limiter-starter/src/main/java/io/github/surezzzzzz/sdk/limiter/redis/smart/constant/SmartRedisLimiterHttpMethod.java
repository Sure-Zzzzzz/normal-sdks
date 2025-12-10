package io.github.surezzzzzz.sdk.limiter.redis.smart.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author: Sure.
 * @description HTTP方法枚举
 * @Date: 2024/12/XX XX:XX
 */
@Getter
@AllArgsConstructor
public enum SmartRedisLimiterHttpMethod {

    /**
     * 所有方法
     */
    ALL("*", "所有方法"),

    /**
     * GET请求
     */
    GET("GET", "查询"),

    /**
     * POST请求
     */
    POST("POST", "创建"),

    /**
     * PUT请求
     */
    PUT("PUT", "更新"),

    /**
     * DELETE请求
     */
    DELETE("DELETE", "删除"),

    /**
     * PATCH请求
     */
    PATCH("PATCH", "部分更新"),

    /**
     * HEAD请求
     */
    HEAD("HEAD", "获取头信息"),

    /**
     * OPTIONS请求
     */
    OPTIONS("OPTIONS", "获取支持的方法");

    private final String method;
    private final String desc;

    /**
     * 根据方法名获取枚举
     */
    public static SmartRedisLimiterHttpMethod fromMethod(String method) {
        if (method == null || method.isEmpty() || "*".equals(method)) {
            return ALL;
        }

        for (SmartRedisLimiterHttpMethod httpMethod : values()) {
            if (httpMethod.method.equalsIgnoreCase(method)) {
                return httpMethod;
            }
        }

        return ALL;
    }

    /**
     * 判断是否匹配
     */
    public boolean matches(String requestMethod) {
        if (this == ALL) {
            return true;
        }
        return this.method.equalsIgnoreCase(requestMethod);
    }
}
