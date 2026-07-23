package io.github.surezzzzzz.sdk.messaging.kafka.outbox.constant;

/**
 * Kafka Outbox Core 常量。
 *
 * @author surezzzzzz
 */
public final class KafkaOutboxCoreConstant {

    /**
     * 安全展示文本最大 Unicode 码点数。
     */
    public static final int MAX_SAFE_DISPLAY_CODE_POINTS = 256;
    /**
     * 错误摘要最大 Unicode 码点数。
     */
    public static final int MAX_ERROR_SUMMARY_CODE_POINTS = 512;
    /**
     * 不安全文本的展示占位符。
     */
    public static final String SAFE_VALUE_UNAVAILABLE = "<unsafe>";
    /**
     * 常量类禁止实例化消息。
     */
    public static final String MESSAGE_CONSTANT_CLASS_CANNOT_INSTANTIATE = "常量类不能实例化";

    private KafkaOutboxCoreConstant() {
        throw new UnsupportedOperationException(MESSAGE_CONSTANT_CLASS_CANNOT_INSTANTIATE);
    }
}
