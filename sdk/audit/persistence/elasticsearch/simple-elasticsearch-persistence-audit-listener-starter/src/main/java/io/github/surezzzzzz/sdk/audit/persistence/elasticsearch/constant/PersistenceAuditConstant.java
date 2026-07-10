package io.github.surezzzzzz.sdk.audit.persistence.elasticsearch.constant;

/**
 * ES Persistence 审计监听器常量
 *
 * @author surezzzzzz
 */
public final class PersistenceAuditConstant {

    private PersistenceAuditConstant() {
        throw new UnsupportedOperationException("Utility class");
    }

    // ==================== 配置相关常量 ====================

    public static final String CONFIG_PREFIX = "io.github.surezzzzzz.sdk.audit.persistence.elasticsearch.listener";
    public static final String LOG_HANDLER_CONFIG_PREFIX = CONFIG_PREFIX + ".handler.log";
    public static final String CONFIG_ENABLE = "enable";
    public static final String CONFIG_ENABLED = "enabled";

    // ==================== 线程池默认值 ====================

    public static final int DEFAULT_EXECUTOR_CORE_SIZE = 4;
    public static final int DEFAULT_EXECUTOR_MAX_SIZE = 20;
    public static final int DEFAULT_EXECUTOR_QUEUE_CAPACITY = 2000;
    public static final int DEFAULT_EXECUTOR_KEEP_ALIVE_SECONDS = 60;
    public static final String DEFAULT_EXECUTOR_THREAD_NAME_PREFIX = "es-persistence-audit-";
    public static final String EXECUTOR_BEAN_NAME = "esPersistenceAuditExecutor";

    // ==================== 拒绝策略常量 ====================

    public static final String REJECT_POLICY_CALLER_RUNS = "CALLER_RUNS";
    public static final String REJECT_POLICY_DISCARD = "DISCARD";
    public static final String REJECT_POLICY_DISCARD_OLDEST = "DISCARD_OLDEST";
    public static final String REJECT_POLICY_ABORT = "ABORT";

    // ==================== 审计记录常量 ====================

    public static final int DEFAULT_MAX_FAILURE_SIZE = 20;
    public static final int HTTP_STATUS_CONFLICT = 409;
    public static final String SOURCE_TYPE = "PERSISTENCE";
    public static final String RESULT_SUCCESS = "success";
    public static final String RESULT_FAILURE = "failure";
    public static final String RESULT_PARTIAL_FAILURE = "partial_failure";
    public static final String RESULT_TASK_SUBMITTED = "task_submitted";
    public static final String LOG_RECORD_FORMAT = "ES_PERSISTENCE_AUDIT - {}";
}
