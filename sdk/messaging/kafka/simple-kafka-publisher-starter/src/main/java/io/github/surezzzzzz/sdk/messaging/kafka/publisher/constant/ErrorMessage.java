package io.github.surezzzzzz.sdk.messaging.kafka.publisher.constant;

/**
 * Simple Kafka Publisher 错误消息
 *
 * @author surezzzzzz
 */
public final class ErrorMessage {

    private ErrorMessage() {
        throw new UnsupportedOperationException(SimpleKafkaPublisherConstant.UTILITY_CLASS_MESSAGE);
    }

    public static final String CONFIG_INVALID = "Kafka publisher 配置非法：%s";
    public static final String MESSAGE_INVALID = "Kafka publish message 非法：%s";
    public static final String PAYLOAD_INVALID = "Kafka publish payload 非法，messageType=[%s]，messageId=[%s]，reason=[%s]";
    public static final String TOPIC_EMPTY = "Kafka publish topic 不能为空";
    public static final String RECORD_INVALID = "Kafka publish record 参数非法：%s";
    public static final String SERIALIZE_FAILED = "Kafka publish message 序列化失败，messageType=[%s]，messageId=[%s]";
    public static final String SEND_FAILED = "Kafka publish message 发送失败，topic=[%s]，messageType=[%s]，messageId=[%s]";
    public static final String SEND_INTERRUPTED = "Kafka publish message 同步等待被中断，发送状态未知，topic=[%s]，messageType=[%s]，messageId=[%s]，reason=[%s]";
    public static final String SEND_TIMEOUT = "Kafka publish message 同步等待超时，发送状态未知，不应盲目重试，topic=[%s]，messageType=[%s]，messageId=[%s]，timeoutMs=[%d]";
    public static final String HEADER_INVALID = "Kafka publish header 非法，headerKey=[%s]，reason=[%s]";
    public static final String ROUTE_INPUT_INVALID = "Kafka publish 路由参数非法：%s";
}
