package io.github.surezzzzzz.sdk.kafka.route.exception;

import lombok.Getter;

/**
 * Simple Kafka Route 异常基类
 *
 * @author surezzzzzz
 */
@Getter
public class SimpleKafkaRouteException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String errorCode;

    public SimpleKafkaRouteException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public SimpleKafkaRouteException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
