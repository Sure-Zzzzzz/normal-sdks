package io.github.surezzzzzz.sdk.limiter.redis.smart.event;

import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.SmartRedisLimiterEventPayload;
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
public class SmartRedisLimiterEvent extends ApplicationEvent {

    private static final long serialVersionUID = 2L;

    /**
     * 事件载荷
     */
    private final SmartRedisLimiterEventPayload payload;

    /**
     * 构造限流事件
     *
     * @param source  事件发布者
     * @param payload 事件载荷
     */
    public SmartRedisLimiterEvent(Object source, SmartRedisLimiterEventPayload payload) {
        super(source);
        if (payload == null) {
            throw new IllegalArgumentException("payload must not be null");
        }
        this.payload = payload;
    }

    /**
     * 兼容 1.x 直接 new Event 的调用方；新代码必须使用 payload 构造器。
     *
     * @param source              事件发布者
     * @param limitKey            限流 Key
     * @param keyStrategy         Key 生成策略
     * @param algorithm           限流算法
     * @param limitRules          限流规则
     * @param passed              是否通过
     * @param sourceType          限流来源标识
     * @param requestUri          请求 URI
     * @param httpMethod          HTTP 方法
     * @param clientIp            客户端 IP
     * @param matchedPathPattern  匹配路径模式
     * @param methodName          方法名
     * @param methodQualifiedName 方法全限定名
     * @param attributes          扩展上下文
     * @param limit               限流阈值
     * @param remaining           剩余配额
     * @param resetAt             重置时间
     * @param durationNanos       检查耗时
     */
    @Deprecated
    public SmartRedisLimiterEvent(Object source, String limitKey, String keyStrategy, String algorithm,
                                  String limitRules, boolean passed, String sourceType,
                                  String requestUri, String httpMethod, String clientIp, String matchedPathPattern,
                                  String methodName, String methodQualifiedName,
                                  Map<String, Object> attributes,
                                  long limit, long remaining, long resetAt, long durationNanos) {
        this(source, SmartRedisLimiterEventPayload.builder()
                .limitKey(limitKey)
                .routeKey(limitKey)
                .datasourceKey(null)
                .redisMode(SmartRedisLimiterConstant.REDIS_MODE_UNKNOWN)
                .routeRequired(false)
                .routeResolved(false)
                .keyStrategy(keyStrategy)
                .algorithm(algorithm)
                .limitRules(limitRules)
                .passed(passed)
                .sourceType(sourceType)
                .requestUri(requestUri)
                .httpMethod(httpMethod)
                .clientIp(clientIp)
                .matchedPathPattern(matchedPathPattern)
                .methodName(methodName)
                .methodQualifiedName(methodQualifiedName)
                .attributes(attributes)
                .limit(limit)
                .remaining(remaining)
                .resetAt(resetAt)
                .durationNanos(durationNanos)
                .build());
    }

    /**
     * 获取事件载荷
     *
     * @return 事件载荷
     */
    public SmartRedisLimiterEventPayload getPayload() {
        return payload;
    }

    /**
     * 获取限流来源标识
     *
     * @return 限流来源标识
     */
    public String getSourceType() {
        return payload.getSourceType();
    }

    /**
     * 兼容 1.x：历史版本已将 ApplicationEvent.getSource() 协变覆盖为限流来源字符串。
     *
     * @return 限流来源标识
     */
    @Override
    public String getSource() {
        return payload.getSourceType();
    }

    /**
     * 获取原始事件发布者
     *
     * @return 原始事件发布者
     */
    public Object getRawSource() {
        return super.getSource();
    }

    public String getLimitKey() {
        return payload.getLimitKey();
    }

    public String getKeyStrategy() {
        return payload.getKeyStrategy();
    }

    public String getAlgorithm() {
        return payload.getAlgorithm();
    }

    public String getLimitRules() {
        return payload.getLimitRules();
    }

    public boolean isPassed() {
        return payload.isPassed();
    }

    public String getRequestUri() {
        return payload.getRequestUri();
    }

    public String getHttpMethod() {
        return payload.getHttpMethod();
    }

    public String getClientIp() {
        return payload.getClientIp();
    }

    public String getMatchedPathPattern() {
        return payload.getMatchedPathPattern();
    }

    public String getMethodName() {
        return payload.getMethodName();
    }

    public String getMethodQualifiedName() {
        return payload.getMethodQualifiedName();
    }

    public Map<String, Object> getAttributes() {
        return payload.getAttributes();
    }

    public long getLimit() {
        return payload.getLimit();
    }

    public long getRemaining() {
        return payload.getRemaining();
    }

    public long getResetAt() {
        return payload.getResetAt();
    }

    public long getDurationNanos() {
        return payload.getDurationNanos();
    }

    public String getRouteKey() {
        return payload.getRouteKey();
    }

    public String getDatasourceKey() {
        return payload.getDatasourceKey();
    }

    public String getRedisMode() {
        return payload.getRedisMode();
    }

    public boolean isRouteRequired() {
        return payload.isRouteRequired();
    }

    public boolean isRouteResolved() {
        return payload.isRouteResolved();
    }

    public String getFallbackReason() {
        return payload.getFallbackReason();
    }
}
