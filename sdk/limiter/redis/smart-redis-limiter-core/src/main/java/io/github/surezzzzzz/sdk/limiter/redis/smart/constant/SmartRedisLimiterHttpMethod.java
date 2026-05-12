package io.github.surezzzzzz.sdk.limiter.redis.smart.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * SmartRedisLimiter HTTP方法枚举
 *
 * @author surezzzzzz
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

    private final String code;
    private final String desc;

    /**
     * 根据代码获取枚举
     *
     * @param code HTTP方法代码
     * @return 枚举，如果不存在返回 ALL
     */
    public static SmartRedisLimiterHttpMethod fromCode(String code) {
        if (code == null || code.isEmpty() || "*".equals(code)) {
            return ALL;
        }

        for (SmartRedisLimiterHttpMethod httpMethod : values()) {
            if (httpMethod.code.equalsIgnoreCase(code)) {
                return httpMethod;
            }
        }

        return ALL;
    }

    /**
     * 判断代码是否有效
     *
     * @param code HTTP方法代码
     * @return true 有效，false 无效
     */
    public static boolean isValid(String code) {
        return fromCode(code) != ALL || "*".equals(code);
    }

    /**
     * 获取所有有效的代码
     *
     * @return 代码数组
     */
    public static String[] getAllCodes() {
        SmartRedisLimiterHttpMethod[] methods = values();
        String[] codes = new String[methods.length];
        for (int i = 0; i < methods.length; i++) {
            codes[i] = methods[i].code;
        }
        return codes;
    }

    /**
     * 判断是否匹配
     */
    public boolean matches(String requestMethod) {
        if (this == ALL) {
            return true;
        }
        return this.code.equalsIgnoreCase(requestMethod);
    }

    @Override
    public String toString() {
        return code;
    }
}
