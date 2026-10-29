package io.github.surezzzzzz.sdk.audit.crm.configuration;

import io.github.surezzzzzz.sdk.audit.crm.constant.SimpleCrmAuditListenerConstant;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * CRM 审计监听器配置。
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = SimpleCrmAuditListenerConstant.CONFIG_PREFIX)
public class SimpleCrmAuditListenerProperties {

    /** 处理器配置。 */
    private Handler handler = new Handler();

    /** 处理器配置项。 */
    @Data
    public static class Handler {

        /** 日志处理器配置。 */
        private Log log = new Log();
    }

    /** 日志处理器配置项。 */
    @Data
    public static class Log {

        /** 是否启用默认日志处理器。 */
        private boolean enabled = false;
    }
}
