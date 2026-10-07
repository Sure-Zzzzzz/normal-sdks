package io.github.surezzzzzz.sdk.audit.limiter.constant;

/**
 * SmartRedisLimiter 限流审计监听器常量
 *
 * @author surezzzzzz
 */
public final class SmartRedisLimiterAuditListenerConstant {

    /**
     * 配置前缀
     */
    public static final String CONFIG_PREFIX = "io.github.surezzzzzz.sdk.audit.limiter.listener";

    /**
     * 默认日志处理器配置前缀
     */
    public static final String LOG_HANDLER_CONFIG_PREFIX = CONFIG_PREFIX + ".handler.log";

    /**
     * 默认是否启用日志处理器
     */
    public static final boolean DEFAULT_LOG_HANDLER_ENABLED = true;

    private SmartRedisLimiterAuditListenerConstant() {
        throw new UnsupportedOperationException("SmartRedisLimiterAuditListenerConstant cannot be instantiated");
    }
}
