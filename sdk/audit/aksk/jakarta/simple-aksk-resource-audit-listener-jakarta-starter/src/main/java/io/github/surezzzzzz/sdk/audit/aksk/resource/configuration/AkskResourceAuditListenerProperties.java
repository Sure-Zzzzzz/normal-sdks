package io.github.surezzzzzz.sdk.audit.aksk.resource.configuration;

import io.github.surezzzzzz.sdk.audit.aksk.resource.constant.AkskResourceAuditListenerConstant;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AKSK 资源审计配置，保留旧线默认日志开关并增加模块总开关。
 *
 * @author surezzzzzz
 */
@Data
@ConfigurationProperties(prefix = AkskResourceAuditListenerConstant.CONFIG_PREFIX)
public class AkskResourceAuditListenerProperties {
    /**
     * 总开关；关闭时不注册模块自带组件，不影响宿主自有 Handler。
     */
    private boolean enable = AkskResourceAuditListenerConstant.DEFAULT_ENABLE;
    /**
     * 审计处理器配置。
     */
    private Handler handler = new Handler();

    @Data
    public static class Handler {
        /**
         * 默认日志处理器配置。
         */
        private Log log = new Log();
    }

    @Data
    public static class Log {
        /**
         * 默认关闭；显式开启后与业务 Handler 并存。
         */
        private boolean enabled = AkskResourceAuditListenerConstant.DEFAULT_LOG_ENABLED;
    }
}
