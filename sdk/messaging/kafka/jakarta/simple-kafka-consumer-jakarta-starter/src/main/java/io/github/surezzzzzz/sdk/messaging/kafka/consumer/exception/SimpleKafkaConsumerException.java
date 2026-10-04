package io.github.surezzzzzz.sdk.messaging.kafka.consumer.exception;

import lombok.Getter;

/**
 * Simple Kafka Consumer 异常基类
 *
 * @author surezzzzzz
 */
@Getter
public class SimpleKafkaConsumerException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 错误码
     */
    private final String errorCode;

    public SimpleKafkaConsumerException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public SimpleKafkaConsumerException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
