package io.github.surezzzzzz.sdk.messaging.kafka.consumer.exception;

/**
 * Kafka Consumer 业务消费异常
 *
 * @author surezzzzzz
 */
public class KafkaConsumerException extends SimpleKafkaConsumerException {

    private static final long serialVersionUID = 1L;

    public KafkaConsumerException(String errorCode, String message) {
        super(errorCode, message);
    }

    public KafkaConsumerException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
