package io.github.surezzzzzz.sdk.audit.limiter.management.configuration;

import io.github.surezzzzzz.sdk.audit.limiter.management.constant.SmartRedisLimiterManagementAuditListenerConstant;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 限流管理审计监听器配置
 *
 * @author surezzzzzz
 */
@Data
@ConfigurationProperties(prefix = SmartRedisLimiterManagementAuditListenerConstant.CONFIG_PREFIX)
public class SmartRedisLimiterManagementAuditListenerProperties {

    private Handler handler = new Handler();

    @Data
    public static class Handler {
        private Log log = new Log();
    }

    @Data
    public static class Log {
        /**
         * 是否启用三元组管理事件默认日志处理器，默认开启
         */
        private boolean managementEnabled = SmartRedisLimiterManagementAuditListenerConstant.DEFAULT_LOG_HANDLER_ENABLED;

        /**
         * 是否启用类型化管理事件默认日志处理器，默认开启
         */
        private boolean typedEnabled = SmartRedisLimiterManagementAuditListenerConstant.DEFAULT_LOG_HANDLER_ENABLED;
    }
}
