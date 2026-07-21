package io.github.surezzzzzz.sdk.messaging.kafka.outbox.exception;

import lombok.Getter;

/**
 * Kafka Outbox 基础异常
 *
 * @author surezzzzzz
 */
@Getter
public class KafkaOutboxException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 错误码
     */
    private final String errorCode;

    /**
     * 创建异常
     *
     * @param errorCode 错误码
     * @param message   错误消息
     */
    public KafkaOutboxException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * 创建带原因的异常
     *
     * @param errorCode 错误码
     * @param message   错误消息
     * @param cause     原因
     */
    public KafkaOutboxException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
