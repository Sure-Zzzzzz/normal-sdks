package io.github.surezzzzzz.sdk.audit.iam.resource.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Simple IAM Resource Audit Listener 配置
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "io.github.surezzzzzz.sdk.audit.iam.resource.listener")
public class SimpleIamResourceAuditListenerProperties {

    private Handler handler = new Handler();

    @Data
    public static class Handler {
        private Log log = new Log();
    }

    @Data
    public static class Log {
        /**
         * 是否启用默认日志处理器，默认关闭
         */
        private boolean enabled = false;
    }
}
