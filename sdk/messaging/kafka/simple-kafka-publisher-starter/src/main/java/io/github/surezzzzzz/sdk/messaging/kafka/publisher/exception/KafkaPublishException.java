package io.github.surezzzzzz.sdk.messaging.kafka.publisher.exception;

/**
 * Kafka 发布异常
 *
 * @author surezzzzzz
 */
public class KafkaPublishException extends SimpleKafkaPublisherException {

    private static final long serialVersionUID = 1L;

    public KafkaPublishException(String errorCode, String message) {
        super(errorCode, message);
    }

    public KafkaPublishException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
