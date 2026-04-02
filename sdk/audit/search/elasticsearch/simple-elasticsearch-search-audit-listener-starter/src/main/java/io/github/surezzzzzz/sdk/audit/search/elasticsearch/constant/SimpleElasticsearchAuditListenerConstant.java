package io.github.surezzzzzz.sdk.audit.search.elasticsearch.constant;

/**
 * Simple Elasticsearch Audit Listener Constants
 *
 * @author surezzzzzz
 */
public final class SimpleElasticsearchAuditListenerConstant {

    private SimpleElasticsearchAuditListenerConstant() {
        throw new UnsupportedOperationException("Utility class");
    }

    // ==================== 配置相关常量 ====================

    /**
     * 配置前缀
     */
    public static final String CONFIG_PREFIX = "io.github.surezzzzzz.sdk.audit.search.elasticsearch.listener";

    /**
     * 日志处理器配置前缀
     */
    public static final String LOG_HANDLER_CONFIG_PREFIX = CONFIG_PREFIX + ".handler.log";

    // ==================== 线程池默认值 ====================

    /**
     * 线程池核心线程数默认值
     */
    public static final int DEFAULT_EXECUTOR_CORE_SIZE = 4;

    /**
     * 线程池最大线程数默认值
     */
    public static final int DEFAULT_EXECUTOR_MAX_SIZE = 20;

    /**
     * 线程池队列容量默认值
     */
    public static final int DEFAULT_EXECUTOR_QUEUE_CAPACITY = 2000;

    /**
     * 线程池线程名前缀
     */
    public static final String DEFAULT_EXECUTOR_THREAD_NAME_PREFIX = "es-audit-";

    /**
     * 线程池空闲线程存活时间（秒）默认值
     */
    public static final int DEFAULT_EXECUTOR_KEEP_ALIVE_SECONDS = 60;

    // ==================== 拒绝策略常量 ====================

    /**
     * 拒绝策略：由调用线程直接执行（默认）
     */
    public static final String REJECT_POLICY_CALLER_RUNS = "CALLER_RUNS";

    /**
     * 拒绝策略：直接丢弃，记录警告日志
     */
    public static final String REJECT_POLICY_DISCARD = "DISCARD";

    /**
     * 拒绝策略：丢弃队列中最旧的任务
     */
    public static final String REJECT_POLICY_DISCARD_OLDEST = "DISCARD_OLDEST";

    /**
     * 拒绝策略：抛出 RejectedExecutionException
     */
    public static final String REJECT_POLICY_ABORT = "ABORT";

    /**
     * 线程池 bean 名称
     */
    public static final String EXECUTOR_BEAN_NAME = "esAuditExecutor";
}
