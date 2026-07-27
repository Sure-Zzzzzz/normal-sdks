package io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.exception;

import lombok.Getter;

/**
 * Management 基础异常。
 *
 * @author surezzzzzz
 */
@Getter
public class KafkaOutboxManagementException extends RuntimeException {

    private final String errorCode;

    public KafkaOutboxManagementException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public KafkaOutboxManagementException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
