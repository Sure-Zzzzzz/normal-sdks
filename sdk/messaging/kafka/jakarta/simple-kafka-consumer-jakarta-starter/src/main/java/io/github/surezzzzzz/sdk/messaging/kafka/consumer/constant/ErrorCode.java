package io.github.surezzzzzz.sdk.messaging.kafka.consumer.constant;

/**
 * 错误码常量
 *
 * @author surezzzzzz
 */
public final class ErrorCode {

    /**
     * 配置或注册非法（route 缺失、幂等 Redis 缺失、参数越界、重复注册、groupId 缺失等）
     */
    public static final String CONFIG_INVALID = "KAFKA_CONSUMER_001";

    // ==================== 配置与注册错误 ====================
    /**
     * topic 无法解析到 datasource
     */
    public static final String TOPIC_DATASOURCE_UNRESOLVED = "KAFKA_CONSUMER_002";
    /**
     * 消费处理异常（可重试）
     */
    public static final String CONSUME_RETRYABLE = "KAFKA_CONSUMER_003";

    // ==================== 消费处理错误 ====================
    /**
     * 消费处理异常（不可重试）
     */
    public static final String CONSUME_FATAL = "KAFKA_CONSUMER_004";
    /**
     * 幂等检查器异常
     */
    public static final String IDEMPOTENCY_CHECK_FAILED = "KAFKA_CONSUMER_005";
    /**
     * 死信投递失败
     */
    public static final String DEAD_LETTER_PUBLISH_FAILED = "KAFKA_CONSUMER_006";
    /**
     * 死信结果非法
     */
    public static final String DEAD_LETTER_RESULT_INVALID = "KAFKA_CONSUMER_007";
    /**
     * 未知消费异常
     */
    public static final String CONSUME_UNKNOWN = "KAFKA_CONSUMER_008";
    /**
     * 幂等处理租约未到期
     */
    public static final String IDEMPOTENCY_IN_PROGRESS = "KAFKA_CONSUMER_009";

    private ErrorCode() {
        throw new UnsupportedOperationException(SimpleKafkaConsumerConstant.UTILITY_CLASS_MESSAGE);
    }
}
