package io.github.surezzzzzz.sdk.messaging.kafka.consumer.configuration;

import io.github.surezzzzzz.sdk.messaging.kafka.consumer.constant.SimpleKafkaConsumerConstant;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Simple Kafka Consumer 配置
 *
 * @author surezzzzzz
 */
@Data
@ConfigurationProperties(SimpleKafkaConsumerConstant.CONFIG_PREFIX)
public class SimpleKafkaConsumerProperties {

    /**
     * 是否启用
     */
    private boolean enable = SimpleKafkaConsumerConstant.DEFAULT_ENABLE;

    /**
     * 幂等配置
     */
    private IdempotencyConfig idempotency = new IdempotencyConfig();

    /**
     * 错误处理配置
     */
    private ErrorConfig error = new ErrorConfig();

    /**
     * 容器配置
     */
    private ContainerConfig container = new ContainerConfig();

    /**
     * 幂等配置
     */
    @Data
    public static class IdempotencyConfig {

        /**
         * 是否启用幂等检查
         */
        private boolean enable = SimpleKafkaConsumerConstant.DEFAULT_IDEMPOTENCY_ENABLE;

        /**
         * 已完成幂等标记保留时长（毫秒）
         */
        private long ttlMs = SimpleKafkaConsumerConstant.DEFAULT_IDEMPOTENCY_TTL_MS;

        /**
         * 处理中租约时长（毫秒）
         */
        private long leaseMs = SimpleKafkaConsumerConstant.DEFAULT_IDEMPOTENCY_LEASE_MS;

        /**
         * redis-route 数据源 key
         */
        private String redisRouteKey = SimpleKafkaConsumerConstant.DEFAULT_IDEMPOTENCY_REDIS_ROUTE_KEY;
    }

    /**
     * 错误处理配置
     */
    @Data
    public static class ErrorConfig {

        /**
         * 最大尝试次数（含首次）
         */
        private int maxAttempts = SimpleKafkaConsumerConstant.DEFAULT_ERROR_MAX_ATTEMPTS;

        /**
         * 首次重试间隔（毫秒）
         */
        private long initialIntervalMs = SimpleKafkaConsumerConstant.DEFAULT_ERROR_INITIAL_INTERVAL_MS;

        /**
         * 退避倍数
         */
        private double multiplier = SimpleKafkaConsumerConstant.DEFAULT_ERROR_MULTIPLIER;

        /**
         * 重试间隔上限（毫秒）
         */
        private long maxIntervalMs = SimpleKafkaConsumerConstant.DEFAULT_ERROR_MAX_INTERVAL_MS;

        /**
         * 抖动比例
         */
        private double jitterFactor = SimpleKafkaConsumerConstant.DEFAULT_ERROR_JITTER_FACTOR;

        /**
         * 死信配置
         */
        private DeadLetterConfig deadLetter = new DeadLetterConfig();
    }

    /**
     * 死信配置
     */
    @Data
    public static class DeadLetterConfig {

        /**
         * 是否启用死信投递
         */
        private boolean enable = SimpleKafkaConsumerConstant.DEFAULT_DEAD_LETTER_ENABLE;

        /**
         * 死信 topic 后缀
         */
        private String suffix = SimpleKafkaConsumerConstant.DEFAULT_DEAD_LETTER_SUFFIX;

        /**
         * 死信投递 datasource key（空表示与原消息同 datasource）
         */
        private String datasourceKey = SimpleKafkaConsumerConstant.DEFAULT_DEAD_LETTER_DATASOURCE_KEY;
    }

    /**
     * 容器配置
     */
    @Data
    public static class ContainerConfig {

        /**
         * 新 group 首次消费偏移策略（earliest / latest / none）
         */
        private String autoOffsetReset;

        /**
         * 是否自动提交 offset，空时继承 route datasource 配置
         */
        private Boolean enableAutoCommit;

        /**
         * 单次 poll 最大记录数，空时继承 route datasource 配置
         */
        private Integer maxPollRecords;

        /**
         * 并发消费者数
         */
        private int concurrency = SimpleKafkaConsumerConstant.DEFAULT_CONCURRENCY;

        /**
         * 停机等待 in-flight handler 完成时长（毫秒）
         */
        private long shutdownAwaitMs = SimpleKafkaConsumerConstant.DEFAULT_SHUTDOWN_AWAIT_MS;
    }
}
