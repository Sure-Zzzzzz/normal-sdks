package io.github.surezzzzzz.sdk.messaging.kafka.publisher.exception;

/**
 * Kafka Publisher 配置异常
 *
 * @author surezzzzzz
 */
public class KafkaPublishConfigurationException extends SimpleKafkaPublisherException {

    private static final long serialVersionUID = 1L;

    public KafkaPublishConfigurationException(String errorCode, String message) {
        super(errorCode, message);
    }

    public KafkaPublishConfigurationException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
