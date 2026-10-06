package io.github.surezzzzzz.sdk.messaging.kafka.outbox.configuration;

import io.github.surezzzzzz.sdk.messaging.kafka.outbox.constant.SimpleKafkaOutboxConstant;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Simple Kafka Outbox 配置
 *
 * @author surezzzzzz
 */
@Data
@ConfigurationProperties(SimpleKafkaOutboxConstant.CONFIG_PREFIX)
public class SimpleKafkaOutboxProperties {

    /**
     * 是否启用
     */
    private boolean enable = SimpleKafkaOutboxConstant.DEFAULT_ENABLE;
    /**
     * 业务 DataSource Bean 名
     */
    private String dataSourceBeanName = SimpleKafkaOutboxConstant.DEFAULT_RESOURCE_BEAN_NAME;
    /**
     * 事务管理器 Bean 名
     */
    private String transactionManagerBeanName = SimpleKafkaOutboxConstant.DEFAULT_RESOURCE_BEAN_NAME;
    /**
     * Outbox 表名
     */
    private String tableName = SimpleKafkaOutboxConstant.DEFAULT_TABLE_NAME;
    /**
     * Worker 配置
     */
    private WorkerConfig worker = new WorkerConfig();
    /**
     * 发送配置
     */
    private SendConfig send = new SendConfig();
    /**
     * 重试配置
     */
    private RetryConfig retry = new RetryConfig();
    /**
     * 清理配置
     */
    private CleanupConfig cleanup = new CleanupConfig();

    /**
     * Worker 配置
     */
    @Data
    public static class WorkerConfig {
        /**
         * 是否启用默认 Worker
         */
        private boolean enable = SimpleKafkaOutboxConstant.DEFAULT_WORKER_ENABLE;
        /**
         * 并发数
         */
        private int concurrency = SimpleKafkaOutboxConstant.DEFAULT_WORKER_CONCURRENCY;
        /**
         * 每轮候选上限
         */
        private int batchSize = SimpleKafkaOutboxConstant.DEFAULT_WORKER_BATCH_SIZE;
        /**
         * 扫描间隔
         */
        private long scanIntervalMs = SimpleKafkaOutboxConstant.DEFAULT_SCAN_INTERVAL_MS;
        /**
         * 空闲间隔
         */
        private long idleIntervalMs = SimpleKafkaOutboxConstant.DEFAULT_IDLE_INTERVAL_MS;
        /**
         * 租约时长
         */
        private long leaseMs = SimpleKafkaOutboxConstant.DEFAULT_LEASE_MS;
        /**
         * 停机等待时长
         */
        private long shutdownAwaitMs = SimpleKafkaOutboxConstant.DEFAULT_SHUTDOWN_AWAIT_MS;
    }

    /**
     * 发送配置
     */
    @Data
    public static class SendConfig {
        /**
         * 单条发送等待超时
         */
        private long timeoutMs = SimpleKafkaOutboxConstant.DEFAULT_SEND_TIMEOUT_MS;
    }

    /**
     * 重试配置
     */
    @Data
    public static class RetryConfig {
        /**
         * 最大投递总次数
         */
        private int maxAttempts = SimpleKafkaOutboxConstant.DEFAULT_MAX_ATTEMPTS;
        /**
         * 初始重试间隔
         */
        private long initialIntervalMs = SimpleKafkaOutboxConstant.DEFAULT_RETRY_INITIAL_INTERVAL_MS;
        /**
         * 退避倍数
         */
        private double multiplier = SimpleKafkaOutboxConstant.DEFAULT_RETRY_MULTIPLIER;
        /**
         * 最大重试间隔
         */
        private long maxIntervalMs = SimpleKafkaOutboxConstant.DEFAULT_RETRY_MAX_INTERVAL_MS;
        /**
         * 抖动比例
         */
        private double jitterFactor = SimpleKafkaOutboxConstant.DEFAULT_RETRY_JITTER_FACTOR;
    }

    /**
     * 清理配置
     */
    @Data
    public static class CleanupConfig {
        /**
         * 是否启用自动清理
         */
        private boolean enable = SimpleKafkaOutboxConstant.DEFAULT_CLEANUP_ENABLE;
        /**
         * SENT 保留天数
         */
        private int retentionDays = SimpleKafkaOutboxConstant.DEFAULT_CLEANUP_RETENTION_DAYS;
        /**
         * 每批清理数量
         */
        private int batchSize = SimpleKafkaOutboxConstant.DEFAULT_CLEANUP_BATCH_SIZE;
        /**
         * 清理间隔
         */
        private long intervalMs = SimpleKafkaOutboxConstant.DEFAULT_CLEANUP_INTERVAL_MS;
    }
}
