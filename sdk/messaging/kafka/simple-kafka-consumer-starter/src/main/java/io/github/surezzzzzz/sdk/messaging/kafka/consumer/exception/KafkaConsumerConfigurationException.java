package io.github.surezzzzzz.sdk.messaging.kafka.consumer.exception;

/**
 * Kafka Consumer 配置异常
 *
 * @author surezzzzzz
 */
public class KafkaConsumerConfigurationException extends SimpleKafkaConsumerException {

    private static final long serialVersionUID = 1L;

    public KafkaConsumerConfigurationException(String errorCode, String message) {
        super(errorCode, message);
    }

    public KafkaConsumerConfigurationException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
