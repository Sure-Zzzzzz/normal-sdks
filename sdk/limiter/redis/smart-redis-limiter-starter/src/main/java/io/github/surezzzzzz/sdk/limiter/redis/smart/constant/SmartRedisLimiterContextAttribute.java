package io.github.surezzzzzz.sdk.limiter.redis.smart.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author: Sure.
 * @description 智能Redis限流器上下文属性键枚举
 * @Date: 2024/12/XX XX:XX
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
    MATCHED_PATH_PATTERN("matchedPathPattern", "匹配到的路径模式");

    private final String key;
    private final String desc;
}