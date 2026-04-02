package io.github.surezzzzzz.sdk.audit.search.elasticsearch.configuration;

import io.github.surezzzzzz.sdk.audit.search.elasticsearch.constant.SimpleElasticsearchAuditListenerConstant;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Simple Elasticsearch Audit Listener Properties
 *
 * @author surezzzzzz
 */
@Data
@ConfigurationProperties(prefix = SimpleElasticsearchAuditListenerConstant.CONFIG_PREFIX)
public class SimpleElasticsearchAuditListenerProperties {

    /**
     * 线程池配置
     */
    private Executor executor = new Executor();

    /**
     * 处理器配置
     */
    private Handler handler = new Handler();

    @Data
    public static class Executor {

        /**
         * 核心线程数
         */
        private int coreSize = SimpleElasticsearchAuditListenerConstant.DEFAULT_EXECUTOR_CORE_SIZE;

        /**
         * 最大线程数
         */
        private int maxSize = SimpleElasticsearchAuditListenerConstant.DEFAULT_EXECUTOR_MAX_SIZE;

        /**
         * 队列容量
         */
        private int queueCapacity = SimpleElasticsearchAuditListenerConstant.DEFAULT_EXECUTOR_QUEUE_CAPACITY;

        /**
         * 空闲线程存活时间（秒）
         */
        private int keepAliveSeconds = SimpleElasticsearchAuditListenerConstant.DEFAULT_EXECUTOR_KEEP_ALIVE_SECONDS;

        /**
         * 拒绝策略：CALLER_RUNS（默认）、DISCARD、DISCARD_OLDEST、ABORT
         */
        private String rejectPolicy = SimpleElasticsearchAuditListenerConstant.REJECT_POLICY_CALLER_RUNS;
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
