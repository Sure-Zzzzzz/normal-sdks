package io.github.surezzzzzz.sdk.limiter.redis.smart.model;

import lombok.Builder;
import lombok.Getter;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * SmartRedisLimiter 事件载荷
 *
 * @author surezzzzzz
 */
@Getter
public final class SmartRedisLimiterEventPayload {

    /**
     * 限流 Key
     */
    private final String limitKey;

    /**
     * 路由 Key
     */
    private final String routeKey;

    /**
     * Redis datasource key
     */
    private final String datasourceKey;

    /**
     * Redis 模式：standalone / cluster / unknown
     */
    private final String redisMode;

    /**
     * 是否要求通过 redis-route 执行
     */
    private final boolean routeRequired;

    /**
     * 是否成功解析到 datasource
     */
    private final boolean routeResolved;

    /**
     * 限流策略代码
     */
    private final String keyStrategy;

    /**
     * 限流算法类型
     */
    private final String algorithm;

    /**
     * 限流规则
     */
    private final String limitRules;

    /**
     * 限流结果
     */
    private final boolean passed;

    /**
     * 限流来源标识：INTERCEPTOR / ASPECT
     */
    private final String sourceType;

    /**
     * 请求 URI
     */
    private final String requestUri;

    /**
     * HTTP 方法
     */
    private final String httpMethod;

    /**
     * 客户端 IP
     */
    private final String clientIp;

    /**
     * 匹配到的路径模式
     */
    private final String matchedPathPattern;

    /**
     * 方法名
     */
    private final String methodName;

    /**
     * 方法全限定名
     */
    private final String methodQualifiedName;

    /**
     * 扩展上下文
     */
    private final Map<String, Object> attributes;

    /**
     * 限流阈值
     */
    private final long limit;

    /**
     * 剩余配额
     */
    private final long remaining;

    /**
     * 窗口重置时间（Unix 秒）
     */
    private final long resetAt;

    /**
     * 限流检查耗时（纳秒）
     */
    private final long durationNanos;

    /**
     * 降级原因
     */
    private final String fallbackReason;

    @Builder
    public SmartRedisLimiterEventPayload(String limitKey, String routeKey, String datasourceKey, String redisMode,
                                         boolean routeRequired, boolean routeResolved,
                                         String keyStrategy, String algorithm, String limitRules, boolean passed,
                                         String sourceType, String requestUri, String httpMethod, String clientIp,
                                         String matchedPathPattern, String methodName, String methodQualifiedName,
                                         Map<String, Object> attributes,
                                         long limit, long remaining, long resetAt, long durationNanos,
                                         String fallbackReason) {
        this.limitKey = limitKey;
        this.routeKey = routeKey;
        this.datasourceKey = datasourceKey;
        this.redisMode = redisMode;
        this.routeRequired = routeRequired;
        this.routeResolved = routeResolved;
        this.keyStrategy = keyStrategy;
        this.algorithm = algorithm;
        this.limitRules = limitRules;
        this.passed = passed;
        this.sourceType = sourceType;
        this.requestUri = requestUri;
        this.httpMethod = httpMethod;
        this.clientIp = clientIp;
        this.matchedPathPattern = matchedPathPattern;
        this.methodName = methodName;
        this.methodQualifiedName = methodQualifiedName;
        this.attributes = attributes == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
        this.limit = limit;
        this.remaining = remaining;
        this.resetAt = resetAt;
        this.durationNanos = durationNanos;
        this.fallbackReason = fallbackReason;
    }
}
