package io.github.surezzzzzz.sdk.audit.http.xff.constant;

/**
 * Simple XFF Capture Audit Listener 常量。
 *
 * @author surezzzzzz
 */
public final class SimpleXffCaptureAuditListenerConstant {

    /**
     * 配置前缀。
     */
    public static final String CONFIG_PREFIX = "io.github.surezzzzzz.sdk.audit.http.xff.capture.listener";
    /**
     * 启用配置名称。
     */
    public static final String CONFIG_ENABLE = "enable";
    /**
     * 配置启用值。
     */
    public static final String CONFIG_VALUE_TRUE = "true";
    /**
     * Spring 应用名配置键。
     */
    public static final String SPRING_APPLICATION_NAME_PROPERTY = "spring.application.name";
    /**
     * 默认是否启用。
     */
    public static final boolean DEFAULT_ENABLE = false;
    /**
     * 默认核心线程数。
     */
    public static final int DEFAULT_EXECUTOR_CORE_SIZE = 2;
    /**
     * 默认最大线程数。
     */
    public static final int DEFAULT_EXECUTOR_MAX_SIZE = 4;
    /**
     * 默认队列容量。
     */
    public static final int DEFAULT_EXECUTOR_QUEUE_CAPACITY = 1000;
    /**
     * 默认空闲线程存活秒数。
     */
    public static final int DEFAULT_EXECUTOR_KEEP_ALIVE_SECONDS = 60;
    /**
     * 默认停机等待秒数。
     */
    public static final int DEFAULT_EXECUTOR_AWAIT_TERMINATION_SECONDS = 10;
    /**
     * 捕获时间格式。
     */
    public static final String CAPTURED_TIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX";
    /**
     * 捕获时间时区。
     */
    public static final String CAPTURED_TIME_ZONE = "UTC";
    /**
     * 执行器 Bean 名称。
     */
    public static final String EXECUTOR_BEAN_NAME = "xffCaptureAuditExecutor";
    /**
     * 默认日志 Provider Bean 名称。
     */
    public static final String LOGGING_PROVIDER_BEAN_NAME = "loggingXffCaptureAuditPersistenceProvider";
    /**
     * 执行器线程名前缀。
     */
    public static final String EXECUTOR_THREAD_NAME_PREFIX = "xff-capture-audit-";
    /**
     * 应用名称字段名。
     */
    public static final String FIELD_APPLICATION_NAME = "applicationName";
    /**
     * 核心线程数字段名。
     */
    public static final String FIELD_EXECUTOR_CORE_SIZE = "executor.coreSize";
    /**
     * 最大线程数字段名。
     */
    public static final String FIELD_EXECUTOR_MAX_SIZE = "executor.maxSize";
    /**
     * 队列容量字段名。
     */
    public static final String FIELD_EXECUTOR_QUEUE_CAPACITY = "executor.queueCapacity";
    /**
     * 空闲线程存活字段名。
     */
    public static final String FIELD_EXECUTOR_KEEP_ALIVE_SECONDS = "executor.keepAliveSeconds";
    /**
     * 停机等待字段名。
     */
    public static final String FIELD_EXECUTOR_AWAIT_TERMINATION_SECONDS =
            "executor.awaitTerminationSeconds";
    /**
     * 数值必须大于零详情模板。
     */
    public static final String DETAIL_MUST_BE_GREATER_THAN_ZERO = "%s 必须大于 0";
    /**
     * 数值不能小于详情模板。
     */
    public static final String DETAIL_MUST_NOT_BE_LESS_THAN = "%s 不能小于 %s";
    /**
     * 数值不能小于零详情模板。
     */
    public static final String DETAIL_MUST_NOT_BE_NEGATIVE = "%s 不能小于 0";

    private SimpleXffCaptureAuditListenerConstant() {
        throw new UnsupportedOperationException("Utility class");
    }
}
