package io.github.surezzzzzz.sdk.messaging.kafka.consumer.constant;

/**
 * Simple Kafka Consumer 常量
 *
 * @author surezzzzzz
 */
public final class SimpleKafkaConsumerConstant {

    /**
     * 配置前缀
     */
    public static final String CONFIG_PREFIX = "io.github.surezzzzzz.sdk.messaging.kafka.consumer";

    // ==================== 配置前缀 ====================
    /**
     * spring-kafka KafkaTemplate 类名，用于 ConditionalOnClass
     */
    public static final String CLASS_NAME_KAFKA_TEMPLATE = "org.springframework.kafka.core.KafkaTemplate";
    /**
     * redis-route 注册表类名，用于可选幂等自动配置
     */
    public static final String CLASS_NAME_REDIS_ROUTE_REGISTRY =
            "io.github.surezzzzzz.sdk.redis.route.registry.SimpleRedisRouteRegistry";
    /**
     * 启用属性名
     */
    public static final String CONFIG_PROPERTY_ENABLE = "enable";
    /**
     * 幂等启用属性名
     */
    public static final String CONFIG_PROPERTY_IDEMPOTENCY_ENABLE = "idempotency.enable";
    /**
     * 默认是否启用
     */
    public static final boolean DEFAULT_ENABLE = false;
    /**
     * 工具类提示
     */
    public static final String UTILITY_CLASS_MESSAGE = "Utility class";
    /**
     * 零
     */
    public static final int ZERO = 0;

    // ==================== 数值常量 ====================
    /**
     * 消费容器生命周期阶段
     */
    public static final int CONTAINER_LIFECYCLE_PHASE = 100;
    /**
     * 首次尝试序号
     */
    public static final int FIRST_ATTEMPT = 1;
    /**
     * 默认是否启用幂等检查
     */
    public static final boolean DEFAULT_IDEMPOTENCY_ENABLE = false;

    // ==================== 幂等默认值 ====================
    /**
     * 默认已完成幂等标记保留时长（毫秒），24 小时
     */
    public static final long DEFAULT_IDEMPOTENCY_TTL_MS = 86400000L;
    /**
     * 默认处理中租约时长（毫秒），5 分钟
     */
    public static final long DEFAULT_IDEMPOTENCY_LEASE_MS = 300000L;
    /**
     * 默认幂等 redis-route 数据源 key
     */
    public static final String DEFAULT_IDEMPOTENCY_REDIS_ROUTE_KEY = "default";
    /**
     * 幂等 redis key 前缀
     */
    public static final String IDEMPOTENCY_REDIS_KEY_PREFIX = "sure-kafka-consumer:idempotency:";
    /**
     * 幂等 redis key 模板
     * 参数: messageId
     */
    public static final String IDEMPOTENCY_REDIS_KEY_TEMPLATE = IDEMPOTENCY_REDIS_KEY_PREFIX + "%s";
    /**
     * 处理中幂等租约值前缀
     */
    public static final String IDEMPOTENCY_PROCESSING_VALUE_PREFIX = "PROCESSING:";
    /**
     * 已完成幂等标记值
     */
    public static final String IDEMPOTENCY_COMPLETED_VALUE = "COMPLETED";
    /**
     * 默认最大尝试次数（含首次）
     */
    public static final int DEFAULT_ERROR_MAX_ATTEMPTS = 3;

    // ==================== 错误处理默认值 ====================
    /**
     * 默认首次重试间隔（毫秒）
     */
    public static final long DEFAULT_ERROR_INITIAL_INTERVAL_MS = 1000L;
    /**
     * 默认退避倍数
     */
    public static final double DEFAULT_ERROR_MULTIPLIER = 2.0;
    /**
     * 默认重试间隔上限（毫秒）
     */
    public static final long DEFAULT_ERROR_MAX_INTERVAL_MS = 30000L;
    /**
     * 默认抖动比例
     */
    public static final double DEFAULT_ERROR_JITTER_FACTOR = 0.2;
    /**
     * 退避倍数下限
     */
    public static final double BACKOFF_MULTIPLIER_MIN = 1.0;

    // ==================== 错误处理校验边界 ====================
    /**
     * 抖动比例下限
     */
    public static final double BACKOFF_JITTER_MIN = 0.0;
    /**
     * 抖动比例上限
     */
    public static final double BACKOFF_JITTER_MAX = 1.0;
    /**
     * 最大尝试次数下限
     */
    public static final int BACKOFF_MAX_ATTEMPTS_MIN = 1;
    /**
     * 默认是否启用死信投递
     */
    public static final boolean DEFAULT_DEAD_LETTER_ENABLE = true;

    // ==================== 死信默认值 ====================
    /**
     * 默认死信 topic 后缀
     */
    public static final String DEFAULT_DEAD_LETTER_SUFFIX = ".DLT";
    /**
     * 默认死信投递 datasource key（空表示与原消息同 datasource）
     */
    public static final String DEFAULT_DEAD_LETTER_DATASOURCE_KEY = "";
    /**
     * 默认死信投递同步等待时长（毫秒）
     */
    public static final long DEFAULT_DEAD_LETTER_SEND_TIMEOUT_MS = 10000L;
    /**
     * 死信 header：原始 topic
     */
    public static final String DEAD_LETTER_HEADER_ORIGINAL_TOPIC = "x-original-topic";

    // ==================== 死信 header ====================
    /**
     * 死信 header：原始 partition
     */
    public static final String DEAD_LETTER_HEADER_ORIGINAL_PARTITION = "x-original-partition";
    /**
     * 死信 header：原始 offset
     */
    public static final String DEAD_LETTER_HEADER_ORIGINAL_OFFSET = "x-original-offset";
    /**
     * 死信 header：错误码
     */
    public static final String DEAD_LETTER_HEADER_ERROR_CODE = "x-error-code";
    /**
     * 死信 header：错误摘要
     */
    public static final String DEAD_LETTER_HEADER_ERROR_SUMMARY = "x-error-summary";
    /**
     * 死信 header：尝试次数
     */
    public static final String DEAD_LETTER_HEADER_ATTEMPT = "x-attempt";
    /**
     * 默认新 group 首次消费偏移策略
     */
    public static final String DEFAULT_AUTO_OFFSET_RESET = "latest";

    // ==================== container 默认值 ====================
    /**
     * earliest 偏移策略
     */
    public static final String AUTO_OFFSET_RESET_EARLIEST = "earliest";
    /**
     * latest 偏移策略
     */
    public static final String AUTO_OFFSET_RESET_LATEST = "latest";
    /**
     * none 偏移策略
     */
    public static final String AUTO_OFFSET_RESET_NONE = "none";
    /**
     * 默认是否自动提交 offset
     */
    public static final boolean DEFAULT_ENABLE_AUTO_COMMIT = false;
    /**
     * 默认单次 poll 最大记录数
     */
    public static final int DEFAULT_MAX_POLL_RECORDS = 500;
    /**
     * 默认并发消费者数
     */
    public static final int DEFAULT_CONCURRENCY = 1;
    /**
     * 默认停机等待 in-flight handler 完成时长（毫秒）
     */
    public static final long DEFAULT_SHUTDOWN_AWAIT_MS = 30000L;
    /**
     * 并发数下限
     */
    public static final int CONCURRENCY_MIN = 1;
    /**
     * 消息 id header 名（与 publisher / outbox 对齐）
     */
    public static final String HEADER_MESSAGE_ID = "x-message-id";

    // ==================== 消息 header 与 messageId ====================
    /**
     * messageId 兜底模板
     * 参数: topic, partition, offset
     */
    public static final String MESSAGE_ID_FALLBACK_TEMPLATE = "%s:%d:%d";
    /**
     * 错误摘要最大长度
     */
    public static final int ERROR_SUMMARY_MAX_LENGTH = 512;

    // ==================== 错误摘要 ====================
    /**
     * 错误摘要截断后缀
     */
    public static final String ERROR_SUMMARY_TRUNCATE_SUFFIX = "...";
    /**
     * 失败原因：route 注册表缺失
     */
    public static final String REASON_ROUTE_REGISTRY_MISSING = "route-registry-missing";

    // ==================== 失败原因 ====================
    /**
     * 失败原因：幂等启用但 Redis 数据源缺失
     */
    public static final String REASON_IDEMPOTENCY_REDIS_MISSING = "idempotency-redis-missing";
    /**
     * 失败原因：重复注册
     */
    public static final String REASON_DUPLICATE_REGISTRATION = "duplicate-registration";
    /**
     * 失败原因：groupId 缺失
     */
    public static final String REASON_GROUP_ID_MISSING = "group-id-missing";
    /**
     * 失败原因：topic 为空
     */
    public static final String REASON_TOPIC_EMPTY = "topic-empty";
    /**
     * 失败原因：注册项为空
     */
    public static final String REASON_REGISTRATION_MISSING = "registration-missing";
    /**
     * 失败原因：handler 为空
     */
    public static final String REASON_HANDLER_MISSING = "handler-missing";
    /**
     * 失败原因：退避参数非法
     */
    public static final String REASON_BACKOFF_INVALID = "backoff-invalid";
    /**
     * 失败原因：死信配置非法
     */
    public static final String REASON_DEAD_LETTER_INVALID = "dead-letter-invalid";
    /**
     * 失败原因：并发数非法
     */
    public static final String REASON_CONCURRENCY_INVALID = "concurrency-invalid";
    /**
     * 失败原因：max-poll-records 非法
     */
    public static final String REASON_MAX_POLL_RECORDS_INVALID = "max-poll-records-invalid";
    /**
     * 失败原因：auto-offset-reset 非法
     */
    public static final String REASON_AUTO_OFFSET_RESET_INVALID = "auto-offset-reset-invalid";
    /**
     * 失败原因：不支持自动提交
     */
    public static final String REASON_AUTO_COMMIT_UNSUPPORTED = "enable-auto-commit-unsupported";
    /**
     * 失败原因：幂等配置非法
     */
    public static final String REASON_IDEMPOTENCY_CONFIG_INVALID = "idempotency-config-invalid";
    /**
     * 失败原因：幂等 TTL 非法
     */
    public static final String REASON_IDEMPOTENCY_TTL_INVALID = "idempotency-ttl-invalid";
    /**
     * 失败原因：幂等租约时长非法
     */
    public static final String REASON_IDEMPOTENCY_LEASE_INVALID = "idempotency-lease-invalid";

    private SimpleKafkaConsumerConstant() {
        throw new UnsupportedOperationException(UTILITY_CLASS_MESSAGE);
    }
}
