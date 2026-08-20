package io.github.surezzzzzz.sdk.audit.http.xff.configuration;

import io.github.surezzzzzz.sdk.audit.http.xff.constant.ErrorCode;
import io.github.surezzzzzz.sdk.audit.http.xff.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.audit.http.xff.constant.SimpleXffCaptureAuditListenerConstant;
import io.github.surezzzzzz.sdk.audit.http.xff.exception.XffCaptureAuditValidationException;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Simple XFF Capture Audit Listener 配置属性。
 *
 * @author surezzzzzz
 */
@Data
@ConfigurationProperties(prefix = SimpleXffCaptureAuditListenerConstant.CONFIG_PREFIX)
public class SimpleXffCaptureAuditListenerProperties {

    /**
     * 是否启用审计 Listener。
     */
    private boolean enable = SimpleXffCaptureAuditListenerConstant.DEFAULT_ENABLE;

    /**
     * 被调用应用名称；未配置时读取 spring.application.name。
     */
    private String applicationName;

    /**
     * Listener 专用执行器配置。
     */
    private Executor executor = new Executor();

    private static String requireText(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new XffCaptureAuditValidationException(ErrorCode.REQUIRED_VALUE_MISSING,
                    String.format(ErrorMessage.REQUIRED_VALUE_MISSING, field));
        }
        return value;
    }

    private static void requireGreaterThanZero(int value, String field) {
        if (value <= 0) {
            throw new XffCaptureAuditValidationException(ErrorCode.CONFIG_VALUE_INVALID,
                    String.format(ErrorMessage.CONFIG_VALUE_INVALID,
                            String.format(SimpleXffCaptureAuditListenerConstant.DETAIL_MUST_BE_GREATER_THAN_ZERO,
                                    field)));
        }
    }

    /**
     * 校验配置。
     */
    public void validate() {
        executor.validate();
    }

    /**
     * Listener 专用执行器配置。
     */
    @Data
    public static class Executor {

        /**
         * 核心线程数。
         */
        private int coreSize = SimpleXffCaptureAuditListenerConstant.DEFAULT_EXECUTOR_CORE_SIZE;

        /**
         * 最大线程数。
         */
        private int maxSize = SimpleXffCaptureAuditListenerConstant.DEFAULT_EXECUTOR_MAX_SIZE;

        /**
         * 队列容量。
         */
        private int queueCapacity = SimpleXffCaptureAuditListenerConstant.DEFAULT_EXECUTOR_QUEUE_CAPACITY;

        /**
         * 空闲线程存活秒数。
         */
        private int keepAliveSeconds = SimpleXffCaptureAuditListenerConstant.DEFAULT_EXECUTOR_KEEP_ALIVE_SECONDS;

        /**
         * 停机等待秒数。
         */
        private int awaitTerminationSeconds =
                SimpleXffCaptureAuditListenerConstant.DEFAULT_EXECUTOR_AWAIT_TERMINATION_SECONDS;

        /**
         * 校验执行器配置。
         */
        public void validate() {
            requireGreaterThanZero(coreSize,
                    SimpleXffCaptureAuditListenerConstant.FIELD_EXECUTOR_CORE_SIZE);
            requireGreaterThanZero(maxSize,
                    SimpleXffCaptureAuditListenerConstant.FIELD_EXECUTOR_MAX_SIZE);
            if (maxSize < coreSize) {
                throw new XffCaptureAuditValidationException(ErrorCode.CONFIG_VALUE_INVALID,
                        String.format(ErrorMessage.CONFIG_VALUE_INVALID,
                                String.format(SimpleXffCaptureAuditListenerConstant.DETAIL_MUST_NOT_BE_LESS_THAN,
                                        SimpleXffCaptureAuditListenerConstant.FIELD_EXECUTOR_MAX_SIZE,
                                        SimpleXffCaptureAuditListenerConstant.FIELD_EXECUTOR_CORE_SIZE)));
            }
            requireGreaterThanZero(queueCapacity,
                    SimpleXffCaptureAuditListenerConstant.FIELD_EXECUTOR_QUEUE_CAPACITY);
            if (keepAliveSeconds < 0) {
                throw new XffCaptureAuditValidationException(ErrorCode.CONFIG_VALUE_INVALID,
                        String.format(ErrorMessage.CONFIG_VALUE_INVALID,
                                String.format(SimpleXffCaptureAuditListenerConstant.DETAIL_MUST_NOT_BE_NEGATIVE,
                                        SimpleXffCaptureAuditListenerConstant.FIELD_EXECUTOR_KEEP_ALIVE_SECONDS)));
            }
            requireGreaterThanZero(awaitTerminationSeconds,
                    SimpleXffCaptureAuditListenerConstant.FIELD_EXECUTOR_AWAIT_TERMINATION_SECONDS);
        }
    }
}
