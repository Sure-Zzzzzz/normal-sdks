package io.github.surezzzzzz.sdk.messaging.kafka.consumer.constant;

/**
 * 错误信息常量
 *
 * @author surezzzzzz
 */
public final class ErrorMessage {

    /**
     * 配置或注册非法
     * 参数: reason
     */
    public static final String CONFIG_INVALID = "消费配置或注册非法：%s";

    // ==================== 配置与注册错误 ====================
    /**
     * topic 无法解析到 datasource
     * 参数: topic, datasourceKeys
     */
    public static final String TOPIC_DATASOURCE_UNRESOLVED = "topic 无法解析到 datasource：topic=[%s]，已配置数据源=[%s]";
    /**
     * 注解消费方法不符合约定或未暴露给 AOP 代理
     * 参数: method
     */
    public static final String CONFIG_INVALID_HANDLER_METHOD = "handler-missing: method=[%s]";
    /**
     * 同一 datasource 的 topic 被重复注册
     * 参数: datasource, topic
     */
    public static final String CONFIG_INVALID_DUPLICATE_REGISTRATION =
            SimpleKafkaConsumerConstant.REASON_DUPLICATE_REGISTRATION + ": datasource=[%s]，topic=[%s]";
    /**
     * 显式 datasource 不存在
     * 参数: datasource
     */
    public static final String CONFIG_INVALID_DATASOURCE_MISSING =
            "route-registry-missing: datasource=[%s]";
    /**
     * 消费组缺失
     * 参数: topic
     */
    public static final String CONFIG_INVALID_GROUP_ID_MISSING =
            SimpleKafkaConsumerConstant.REASON_GROUP_ID_MISSING + ": topic=[%s]";
    /**
     * topic 未找到对应消费处理器
     * 参数: topic
     */
    public static final String HANDLER_NOT_FOUND = "消费 topic 未找到处理器：topic=[%s]";

    // ==================== 消费处理错误 ====================
    /**
     * 注解消费方法不可访问
     */
    public static final String ANNOTATED_HANDLER_INACCESSIBLE = "消费注解方法不可访问";
    /**
     * 注解消费方法调用失败
     */
    public static final String ANNOTATED_HANDLER_INVOCATION_FAILED = "消费注解方法调用失败";
    /**
     * 消费处理异常（可重试）
     * 参数: topic, messageId, attempt
     */
    public static final String CONSUME_RETRYABLE = "消费处理异常（可重试）：topic=[%s]，messageId=[%s]，attempt=[%d]";
    /**
     * 消费处理异常（不可重试）
     * 参数: topic, messageId
     */
    public static final String CONSUME_FATAL = "消费处理异常（不可重试）：topic=[%s]，messageId=[%s]";
    /**
     * 幂等检查器异常
     * 参数: topic, messageId
     */
    public static final String IDEMPOTENCY_CHECK_FAILED = "幂等检查器异常：topic=[%s]，messageId=[%s]";
    /**
     * 幂等处理租约未到期
     * 参数: topic, messageId
     */
    public static final String IDEMPOTENCY_IN_PROGRESS = "幂等处理租约未到期：topic=[%s]，messageId=[%s]";
    /**
     * 死信投递失败
     * 参数: topic, messageId, deadLetterTopic
     */
    public static final String DEAD_LETTER_PUBLISH_FAILED = "死信投递失败：topic=[%s]，messageId=[%s]，死信topic=[%s]";
    /**
     * 死信结果非法
     * 参数: topic, messageId
     */
    public static final String DEAD_LETTER_RESULT_INVALID = "死信结果非法：topic=[%s]，messageId=[%s]";
    /**
     * 未知消费异常
     * 参数: topic, messageId
     */
    public static final String CONSUME_UNKNOWN = "未知消费异常：topic=[%s]，messageId=[%s]";

    private ErrorMessage() {
        throw new UnsupportedOperationException(SimpleKafkaConsumerConstant.UTILITY_CLASS_MESSAGE);
    }
}
