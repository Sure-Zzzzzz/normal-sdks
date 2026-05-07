package io.github.surezzzzzz.sdk.limiter.redis.smart.audit;

/**
 * SmartRedisLimiter 限流审计链路追踪 ID Provider
 *
 * <p>用于从当前请求上下文中提取链路追踪 ID，填入 {@link io.github.surezzzzzz.sdk.limiter.redis.smart.model.SmartRedisLimiterRecord#traceId}。
 *
 * @author surezzzzzz
 */
public interface SmartRedisLimiterTraceIdProvider {

    /**
     * 获取链路追踪 ID
     *
     * @return traceId，如果无法获取则返回 null
     */
    String getTraceId();
}
