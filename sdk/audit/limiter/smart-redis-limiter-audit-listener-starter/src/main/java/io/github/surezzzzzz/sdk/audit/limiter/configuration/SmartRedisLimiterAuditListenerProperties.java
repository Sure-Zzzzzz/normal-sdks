package io.github.surezzzzzz.sdk.audit.limiter.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * SmartRedisLimiter 限流审计监听器配置
 *
 * @author surezzzzzz
 */
@Data
@ConfigurationProperties(prefix = "io.github.surezzzzzz.sdk.audit.limiter.listener")
public class SmartRedisLimiterAuditListenerProperties {

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
