package io.github.surezzzzzz.sdk.audit.aksk.server.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AKSK Server 审计监听器配置
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "io.github.surezzzzzz.sdk.audit.aksk.server.listener")
public class SimpleAkskServerAuditListenerProperties {

    private Handler handler = new Handler();

    @Data
    public static class Handler {
        private Log log = new Log();
    }

    @Data
    public static class Log {
        /**
         * 是否启用默认日志处理器，默认开启
         */
        private boolean enabled = true;
    }
}
