package io.github.surezzzzzz.sdk.limiter.redis.smart.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.Map;

/**
 * SmartRedisLimiter 限流事件
 *
 * <p>在 {@code SmartRedisLimiterInterceptor}（拦截器模式）或
 * {@code SmartRedisLimiterAspect}（注解模式）限流结果产生后发布，
 * 用于审计、监控等下游消费。
 *
 * <p>INTERCEPTOR 和 ASPECT 两种来源共享同一个 Event 类，各自对应字段可为空：
 * <ul>
 *   <li>INTERCEPTOR 模式：requestUri、httpMethod、clientIp、matchedPathPattern 有值，methodName/methodQualifiedName 为 null</li>
 *   <li>ASPECT 模式：methodName、methodQualifiedName 有值，requestUri 等字段为 null</li>
 * </ul>
 *
 * @author surezzzzzz
 */
@Getter
public class SmartRedisLimiterEvent extends ApplicationEvent {

    private static final long serialVersionUID = 1L;

    /**
     * 限流 Key
     */
    private final String limitKey;

    /**
     * 限流策略代码（Key生成策略：method/path/ip/path-pattern）
     */
    private final String keyStrategy;

    /**
     * 限流算法类型（fixed/sliding）
     */
    private final String algorithm;

    /**
     * 限流规则（JSON 字符串，用于记录）
     */
    private final String limitRules;

    /**
     * 限流结果：true=通过，false=触发限流
     */
    private final boolean passed;

    /**
     * 来源：INTERCEPTOR / ASPECT
     */
    private final String source;

    /**
     * 请求 URI（仅 Interceptor 模式有值）
     */
    private final String requestUri;

    /**
     * HTTP 方法（仅 Interceptor 模式有值）
     */
    private final String httpMethod;

    /**
     * 客户端 IP（仅 Interceptor 模式有值）
     */
    private final String clientIp;

    /**
     * 匹配到的路径模式（仅 Interceptor 模式有值）
     */
    private final String matchedPathPattern;

    /**
     * 方法名（仅 Aspect 模式有值）
     */
    private final String methodName;

    /**
     * 方法全限定名（仅 Aspect 模式有值）
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

    public SmartRedisLimiterEvent(Object source, String limitKey, String keyStrategy, String algorithm,
                                  String limitRules, boolean passed, String sourceType,
                                  String requestUri, String httpMethod, String clientIp, String matchedPathPattern,
                                  String methodName, String methodQualifiedName,
                                  Map<String, Object> attributes,
                                  long limit, long remaining, long resetAt, long durationNanos) {
        super(source);
        this.limitKey = limitKey;
        this.keyStrategy = keyStrategy;
        this.algorithm = algorithm;
        this.limitRules = limitRules;
        this.passed = passed;
        this.source = sourceType;
        this.requestUri = requestUri;
        this.httpMethod = httpMethod;
        this.clientIp = clientIp;
        this.matchedPathPattern = matchedPathPattern;
        this.methodName = methodName;
        this.methodQualifiedName = methodQualifiedName;
        this.attributes = attributes;
        this.limit = limit;
        this.remaining = remaining;
        this.resetAt = resetAt;
        this.durationNanos = durationNanos;
    }
}
