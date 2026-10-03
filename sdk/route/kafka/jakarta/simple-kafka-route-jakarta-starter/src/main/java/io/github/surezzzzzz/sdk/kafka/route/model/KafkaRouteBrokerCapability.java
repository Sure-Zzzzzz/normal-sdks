package io.github.surezzzzzz.sdk.kafka.route.model;

/**
 * Kafka broker 能力探测结果
 *
 * @author surezzzzzz
 */
public enum KafkaRouteBrokerCapability {

    /**
     * 已确认支持
     */
    SUPPORTED,

    /**
     * 已确认不支持
     */
    UNSUPPORTED,

    /**
     * 无法确认
     */
    UNKNOWN;

    @Override
    public String toString() {
        return name();
    }
}
