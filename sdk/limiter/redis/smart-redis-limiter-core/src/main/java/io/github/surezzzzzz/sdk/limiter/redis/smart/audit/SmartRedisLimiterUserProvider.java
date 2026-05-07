package io.github.surezzzzzz.sdk.limiter.redis.smart.audit;

/**
 * SmartRedisLimiter 限流审计用户信息 Provider
 *
 * <p>用于从当前请求上下文中提取用户身份信息，填入 {@link io.github.surezzzzzz.sdk.limiter.redis.smart.model.SmartRedisLimiterRecord}。
 *
 * <p>SDK 不提供默认实现，由业务方按需注册。如果同时引入了 {@code simple-aksk-resource-server-starter}，
 * 可参考 {@code IntrospectEsAuditUserProvider} 从 SecurityContext 获取用户信息。
 *
 * @author surezzzzzz
 */
public interface SmartRedisLimiterUserProvider {

    /**
     * 获取客户端ID
     */
    String getClientId();

    /**
     * 获取客户端类型
     */
    String getClientType();

    /**
     * 获取用户ID
     */
    String getUserId();

    /**
     * 获取用户名
     */
    String getUsername();
}
