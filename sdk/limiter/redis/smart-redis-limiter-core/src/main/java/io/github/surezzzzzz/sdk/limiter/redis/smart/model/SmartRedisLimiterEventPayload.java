package io.github.surezzzzzz.sdk.limiter.redis.smart.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.support.SmartRedisLimiterAttributeSnapshotHelper;
import io.github.surezzzzzz.sdk.limiter.redis.smart.support.SmartRedisLimiterPolicyValidationHelper;
import lombok.Builder;
import lombok.Getter;

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
     * Redis 数据源 Key
     */
    private final String datasourceKey;

    /**
     * Redis 模式
     */
    private final String redisMode;

    /**
     * 是否要求通过 Redis Route 执行
     */
    private final boolean routeRequired;

    /**
     * 是否成功解析到数据源
     */
    private final boolean routeResolved;

    /**
     * Key 生成策略
     */
    private final String keyStrategy;

    /**
     * 限流算法
     */
    private final String algorithm;

    /**
     * 限流规则
     */
    private final String limitRules;

    /**
     * 是否通过限流检查
     */
    private final boolean passed;

    /**
     * 限流来源类型
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
     * 匹配路径模式
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
     * 窗口重置时间
     */
    private final long resetAt;

    /**
     * 限流检查耗时
     */
    private final long durationNanos;

    /**
     * 降级原因
     */
    private final String fallbackReason;

    /**
     * 稳定资源编码
     */
    private final String resourceCode;

    /**
     * 策略来源
     */
    private final String policySource;

    /**
     * 远程策略快照版本
     */
    private final Long policyRevision;

    /**
     * 兼容 2.0.0 事件载荷构造器
     */
    public SmartRedisLimiterEventPayload(String limitKey, String routeKey, String datasourceKey, String redisMode,
                                         boolean routeRequired, boolean routeResolved,
                                         String keyStrategy, String algorithm, String limitRules, boolean passed,
                                         String sourceType, String requestUri, String httpMethod, String clientIp,
                                         String matchedPathPattern, String methodName, String methodQualifiedName,
                                         Map<String, Object> attributes,
                                         long limit, long remaining, long resetAt, long durationNanos,
                                         String fallbackReason) {
        this(limitKey, routeKey, datasourceKey, redisMode, routeRequired, routeResolved,
                keyStrategy, algorithm, limitRules, passed, sourceType, requestUri, httpMethod, clientIp,
                matchedPathPattern, methodName, methodQualifiedName, attributes,
                limit, remaining, resetAt, durationNanos, fallbackReason,
                null, SmartRedisLimiterConstant.POLICY_SOURCE_LOCAL, null);
    }

    /**
     * 构造完整限流事件载荷
     *
     * @param limitKey            限流 Key
     * @param routeKey            路由 Key
     * @param datasourceKey       Redis 数据源 Key
     * @param redisMode           Redis 模式
     * @param routeRequired       是否要求通过 Redis Route 执行
     * @param routeResolved       是否成功解析到数据源
     * @param keyStrategy         Key 生成策略
     * @param algorithm           限流算法
     * @param limitRules          限流规则
     * @param passed              是否通过限流检查
     * @param sourceType          限流来源类型
     * @param requestUri          请求 URI
     * @param httpMethod          HTTP 方法
     * @param clientIp            客户端 IP
     * @param matchedPathPattern  匹配路径模式
     * @param methodName          方法名
     * @param methodQualifiedName 方法全限定名
     * @param attributes          扩展上下文
     * @param limit               限流阈值
     * @param remaining           剩余配额
     * @param resetAt             窗口重置时间
     * @param durationNanos       限流检查耗时
     * @param fallbackReason      降级原因
     * @param resourceCode        稳定资源编码
     * @param policySource        策略来源
     * @param policyRevision      远程策略快照版本
     * @throws io.github.surezzzzzz.sdk.limiter.redis.smart.exception.SmartRedisLimiterException 策略上下文非法时抛出
     */
    @Builder
    @JsonCreator
    public SmartRedisLimiterEventPayload(
            @JsonProperty(SmartRedisLimiterConstant.JSON_FIELD_LIMIT_KEY) String limitKey,
            @JsonProperty(SmartRedisLimiterConstant.JSON_FIELD_ROUTE_KEY) String routeKey,
            @JsonProperty(SmartRedisLimiterConstant.JSON_FIELD_DATASOURCE_KEY) String datasourceKey,
            @JsonProperty(SmartRedisLimiterConstant.JSON_FIELD_REDIS_MODE) String redisMode,
            @JsonProperty(SmartRedisLimiterConstant.JSON_FIELD_ROUTE_REQUIRED) boolean routeRequired,
            @JsonProperty(SmartRedisLimiterConstant.JSON_FIELD_ROUTE_RESOLVED) boolean routeResolved,
            @JsonProperty(SmartRedisLimiterConstant.JSON_FIELD_KEY_STRATEGY) String keyStrategy,
            @JsonProperty(SmartRedisLimiterConstant.JSON_FIELD_ALGORITHM) String algorithm,
            @JsonProperty(SmartRedisLimiterConstant.JSON_FIELD_LIMIT_RULES) String limitRules,
            @JsonProperty(SmartRedisLimiterConstant.JSON_FIELD_PASSED) boolean passed,
            @JsonProperty(SmartRedisLimiterConstant.JSON_FIELD_SOURCE_TYPE) String sourceType,
            @JsonProperty(SmartRedisLimiterConstant.JSON_FIELD_REQUEST_URI) String requestUri,
            @JsonProperty(SmartRedisLimiterConstant.JSON_FIELD_HTTP_METHOD) String httpMethod,
            @JsonProperty(SmartRedisLimiterConstant.JSON_FIELD_CLIENT_IP) String clientIp,
            @JsonProperty(SmartRedisLimiterConstant.JSON_FIELD_MATCHED_PATH_PATTERN) String matchedPathPattern,
            @JsonProperty(SmartRedisLimiterConstant.JSON_FIELD_METHOD_NAME) String methodName,
            @JsonProperty(SmartRedisLimiterConstant.JSON_FIELD_METHOD_QUALIFIED_NAME) String methodQualifiedName,
            @JsonProperty(SmartRedisLimiterConstant.JSON_FIELD_ATTRIBUTES) Map<String, Object> attributes,
            @JsonProperty(SmartRedisLimiterConstant.JSON_FIELD_LIMIT) long limit,
            @JsonProperty(SmartRedisLimiterConstant.JSON_FIELD_REMAINING) long remaining,
            @JsonProperty(SmartRedisLimiterConstant.JSON_FIELD_RESET_AT) long resetAt,
            @JsonProperty(SmartRedisLimiterConstant.JSON_FIELD_DURATION_NANOS) long durationNanos,
            @JsonProperty(SmartRedisLimiterConstant.JSON_FIELD_FALLBACK_REASON) String fallbackReason,
            @JsonProperty(SmartRedisLimiterConstant.JSON_FIELD_RESOURCE_CODE) String resourceCode,
            @JsonProperty(SmartRedisLimiterConstant.JSON_FIELD_POLICY_SOURCE) String policySource,
            @JsonProperty(SmartRedisLimiterConstant.JSON_FIELD_POLICY_REVISION) Long policyRevision) {
        String resolvedPolicySource = policySource == null
                ? SmartRedisLimiterConstant.POLICY_SOURCE_LOCAL
                : policySource;
        String normalizedResourceCode = resourceCode == null
                ? null
                : SmartRedisLimiterPolicyValidationHelper.normalizeResourceCode(resourceCode);
        SmartRedisLimiterPolicyValidationHelper.validatePolicyContext(
                resolvedPolicySource, normalizedResourceCode, policyRevision);

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
        this.attributes = SmartRedisLimiterAttributeSnapshotHelper.snapshotStrict(attributes);
        this.limit = limit;
        this.remaining = remaining;
        this.resetAt = resetAt;
        this.durationNanos = durationNanos;
        this.fallbackReason = fallbackReason;
        this.resourceCode = normalizedResourceCode;
        this.policySource = resolvedPolicySource;
        this.policyRevision = policyRevision;
    }

}
