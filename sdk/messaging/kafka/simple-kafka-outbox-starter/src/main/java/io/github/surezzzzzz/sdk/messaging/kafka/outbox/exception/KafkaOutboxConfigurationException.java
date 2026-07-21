package io.github.surezzzzzz.sdk.messaging.kafka.outbox.exception;

/**
 * Kafka Outbox 配置异常
 *
 * @author surezzzzzz
 */
public class KafkaOutboxConfigurationException extends KafkaOutboxException {

    private static final long serialVersionUID = 1L;

    /**
     * 创建配置异常
     *
     * @param errorCode 错误码
     * @param message   错误消息
     */
    public KafkaOutboxConfigurationException(String errorCode, String message) {
        super(errorCode, message);
    }

    /**
     * 创建带原因的配置异常
     *
     * @param errorCode 错误码
     * @param message   错误消息
     * @param cause     原因
     */
    public KafkaOutboxConfigurationException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
