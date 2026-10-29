package io.github.surezzzzzz.sdk.audit.crm.constant;

/**
 * CRM 审计监听器常量。
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
public final class SimpleCrmAuditListenerConstant {

    /** 配置前缀。 */
    public static final String CONFIG_PREFIX = "io.github.surezzzzzz.sdk.audit.crm.listener";

    /** 默认日志处理器配置前缀。 */
    public static final String LOG_HANDLER_CONFIG_PREFIX = CONFIG_PREFIX + ".handler.log";

    /** 配置开关名称。 */
    public static final String ENABLED_PROPERTY = "enabled";

    /** 开关启用值。 */
    public static final String ENABLED_VALUE = "true";

    /** 默认日志标识。 */
    public static final String LOG_EVENT_PREFIX = "CRM_AUDIT";

    private SimpleCrmAuditListenerConstant() {
        throw new UnsupportedOperationException("常量类不允许实例化");
    }
}
