package io.github.surezzzzzz.sdk.audit.aksk.resource.constant;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * AKSK 资源审计配置常量。
 *
 * @author surezzzzzz
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AkskResourceAuditListenerConstant {
    /**
     * 总开关和嵌套配置的统一前缀。
     */
    public static final String CONFIG_PREFIX = "io.github.surezzzzzz.sdk.audit.aksk.resource.listener";
    /**
     * 默认日志 Handler 的配置前缀，与旧线保持一致。
     */
    public static final String LOG_CONFIG_PREFIX = CONFIG_PREFIX + ".handler.log";
    /**
     * 默认允许装配；仍要求存在至少一个 Handler。
     */
    public static final boolean DEFAULT_ENABLE = true;
    /**
     * 默认不输出日志审计，与旧线保持一致。
     */
    public static final boolean DEFAULT_LOG_ENABLED = false;
}
