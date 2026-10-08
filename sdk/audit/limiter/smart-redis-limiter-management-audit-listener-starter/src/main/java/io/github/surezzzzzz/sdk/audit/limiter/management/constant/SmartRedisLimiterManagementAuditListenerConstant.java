package io.github.surezzzzzz.sdk.audit.limiter.management.constant;

/**
 * 限流管理审计监听器常量
 *
 * @author surezzzzzz
 */
public final class SmartRedisLimiterManagementAuditListenerConstant {

    /**
     * 配置前缀
     */
    public static final String CONFIG_PREFIX = "io.github.surezzzzzz.sdk.audit.limiter.management.listener";
    /**
     * 默认日志 Handler 配置前缀
     */
    public static final String LOG_HANDLER_CONFIG_PREFIX = CONFIG_PREFIX + ".handler.log";
    /**
     * 默认启用日志 Handler
     */
    public static final boolean DEFAULT_LOG_HANDLER_ENABLED = true;

    private SmartRedisLimiterManagementAuditListenerConstant() {
        throw new UnsupportedOperationException("Utility class");
    }
}
