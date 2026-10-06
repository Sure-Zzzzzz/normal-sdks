package io.github.surezzzzzz.sdk.audit.persistence.elasticsearch.configuration;

import io.github.surezzzzzz.sdk.audit.persistence.elasticsearch.constant.PersistenceAuditConstant;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * ES Persistence 审计监听器配置属性
 *
 * @author surezzzzzz
 */
@Data
@ConfigurationProperties(prefix = PersistenceAuditConstant.CONFIG_PREFIX)
public class PersistenceAuditProperties {

    /**
     * 是否启用
     */
    private boolean enable = false;

    /**
     * 线程池配置
     */
    private Executor executor = new Executor();

    /**
     * 审计记录配置
     */
    private Record record = new Record();

    /**
     * 处理器配置
     */
    private Handler handler = new Handler();

    @Data
    public static class Executor {

        /**
         * 核心线程数
         */
        private int coreSize = PersistenceAuditConstant.DEFAULT_EXECUTOR_CORE_SIZE;

        /**
         * 最大线程数
         */
        private int maxSize = PersistenceAuditConstant.DEFAULT_EXECUTOR_MAX_SIZE;

        /**
         * 队列容量
         */
        private int queueCapacity = PersistenceAuditConstant.DEFAULT_EXECUTOR_QUEUE_CAPACITY;

        /**
         * 空闲线程存活时间（秒）
         */
        private int keepAliveSeconds = PersistenceAuditConstant.DEFAULT_EXECUTOR_KEEP_ALIVE_SECONDS;

        /**
         * 拒绝策略：CALLER_RUNS、DISCARD、DISCARD_OLDEST、ABORT
         */
        private String rejectPolicy = PersistenceAuditConstant.REJECT_POLICY_CALLER_RUNS;
    }

    @Data
    public static class Record {

        /**
         * 最大失败明细条数
         */
        private int maxFailureSize = PersistenceAuditConstant.DEFAULT_MAX_FAILURE_SIZE;
    }

    @Data
    public static class Handler {

        /**
         * 日志处理器配置
         */
        private Log log = new Log();
    }

    @Data
    public static class Log {

        /**
         * 是否启用默认日志处理器
         */
        private boolean enabled = false;
    }
}
