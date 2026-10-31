package io.github.surezzzzzz.sdk.audit.license.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * License 审计监听器配置
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "io.github.surezzzzzz.sdk.audit.license.listener")
public class LicenseAuditListenerProperties {

    /**
     * 处理器配置
     */
    private Handler handler = new Handler();

    /**
     * 处理器配置项
     */
    @Data
    public static class Handler {

        /**
         * 日志处理器配置
         */
        private Log log = new Log();
    }

    /**
     * 日志处理器配置项
     */
    @Data
    public static class Log {

        /**
         * 是否启用默认日志处理器
         */
        private boolean enabled = false;
    }
}
