package io.github.surezzzzzz.sdk.limiter.redis.smart.model;

import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.support.SmartRedisLimiterPolicyValidationHelper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * SmartRedisLimiter 限流审计记录
 *
 * <p>事件监听器将 {@link io.github.surezzzzzz.sdk.limiter.redis.smart.event.SmartRedisLimiterEvent}
 * 转换为 Record 后，交给 Handler 处理。
 *
 * @author surezzzzzz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SmartRedisLimiterRecord {

    // ==================== 用户信息（来自 Provider）====================

    /**
     * 客户端ID
     */
    private String clientId;

    /**
     * 客户端类型
     */
    private String clientType;

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 用户名
     */
    private String username;

    // ==================== 限流上下文 ====================

    /**
     * 限流 Key
     */
    private String limitKey;

    /**
     * 限流策略代码（Key生成策略：method/path/ip/path-pattern）
     */
    private String keyStrategy;

    /**
     * 限流算法类型（fixed/sliding）
     */
    private String algorithm;

    /**
     * 限流规则（JSON 字符串）
     */
    private String limitRules;

    /**
     * 限流结果：true=通过，false=触发限流
     */
    private boolean passed;

    // ==================== 路由信息 ====================

    /**
     * 路由 Key
     */
    private String routeKey;

    /**
     * Redis datasource key
     */
    private String datasourceKey;

    /**
     * Redis 模式：standalone / cluster / unknown
     */
    private String redisMode;

    /**
     * 是否要求通过 redis-route 执行
     */
    private boolean routeRequired;

    /**
     * 是否成功解析到 datasource
     */
    private boolean routeResolved;

    /**
     * 降级原因
     */
    private String fallbackReason;

    // ==================== 来源信息 ====================

    /**
     * 来源：INTERCEPTOR / ASPECT
     */
    private String source;

    // ==================== 请求信息（仅 Interceptor 模式）====================

    /**
     * 请求 URI
     */
    private String requestUri;

    /**
     * HTTP 方法
     */
    private String httpMethod;

    /**
     * 客户端 IP
     */
    private String clientIp;

    /**
     * 匹配到的路径模式
     */
    private String matchedPathPattern;

    // ==================== 方法信息（仅 Aspect 模式）====================

    /**
     * 方法名
     */
    private String methodName;

    /**
     * 方法全限定名
     */
    private String methodQualifiedName;

    // ==================== 限流详情 ====================

    /**
     * 限流阈值
     */
    private long limit;

    /**
     * 剩余配额
     */
    private long remaining;

    /**
     * 窗口重置时间（Unix 秒）
     */
    private long resetAt;

    /**
     * 限流检查耗时（纳秒）
     */
    private long durationNanos;

    // ==================== 元数据 ====================

    /**
     * 事件时间戳
     */
    private Long timestamp;

    /**
     * 链路追踪 ID
     */
    private String traceId;

    /**
     * 扩展字段
     */
    private Map<String, String> extra;

    // ==================== 动态策略信息 ====================

    /**
     * 稳定资源编码
     */
    private String resourceCode;

    /**
     * 策略来源：local / remote
     */
    @Builder.Default
    private String policySource = SmartRedisLimiterConstant.POLICY_SOURCE_LOCAL;

    /**
     * 远程策略快照版本
     */
    private Long policyRevision;

    /**
     * 校验动态策略上下文
     *
     * <p>Record 保持可变 DTO 兼容性，调用方应在交给审计 Handler 前执行本方法。
     *
     * @throws io.github.surezzzzzz.sdk.limiter.redis.smart.exception.SmartRedisLimiterException 策略上下文非法时抛出
     */
    public void validatePolicyContext() {
        String resolvedPolicySource = policySource == null
                ? SmartRedisLimiterConstant.POLICY_SOURCE_LOCAL
                : policySource;
        String normalizedResourceCode = resourceCode == null
                ? null
                : SmartRedisLimiterPolicyValidationHelper.normalizeResourceCode(resourceCode);
        SmartRedisLimiterPolicyValidationHelper.validatePolicyContext(
                resolvedPolicySource, normalizedResourceCode, policyRevision);
        this.policySource = resolvedPolicySource;
        this.resourceCode = normalizedResourceCode;
    }

    /**
     * 兼容 2.0.0 全参构造器
     */
    public SmartRedisLimiterRecord(String clientId, String clientType, String userId, String username,
                                   String limitKey, String keyStrategy, String algorithm, String limitRules,
                                   boolean passed, String routeKey, String datasourceKey, String redisMode,
                                   boolean routeRequired, boolean routeResolved, String fallbackReason,
                                   String source, String requestUri, String httpMethod, String clientIp,
                                   String matchedPathPattern, String methodName, String methodQualifiedName,
                                   long limit, long remaining, long resetAt, long durationNanos,
                                   Long timestamp, String traceId, Map<String, String> extra) {
        this(clientId, clientType, userId, username, limitKey, keyStrategy, algorithm, limitRules,
                passed, routeKey, datasourceKey, redisMode, routeRequired, routeResolved, fallbackReason,
                source, requestUri, httpMethod, clientIp, matchedPathPattern, methodName, methodQualifiedName,
                limit, remaining, resetAt, durationNanos, timestamp, traceId, extra,
                null, SmartRedisLimiterConstant.POLICY_SOURCE_LOCAL, null);
    }
}
