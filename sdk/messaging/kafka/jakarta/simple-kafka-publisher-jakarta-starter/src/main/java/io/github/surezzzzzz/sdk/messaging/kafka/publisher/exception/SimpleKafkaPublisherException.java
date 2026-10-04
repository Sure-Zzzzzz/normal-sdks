package io.github.surezzzzzz.sdk.messaging.kafka.publisher.exception;

import lombok.Getter;

/**
 * Simple Kafka Publisher 基础异常
 *
 * @author surezzzzzz
 */
@Getter
public class SimpleKafkaPublisherException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String errorCode;

    public SimpleKafkaPublisherException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public SimpleKafkaPublisherException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
