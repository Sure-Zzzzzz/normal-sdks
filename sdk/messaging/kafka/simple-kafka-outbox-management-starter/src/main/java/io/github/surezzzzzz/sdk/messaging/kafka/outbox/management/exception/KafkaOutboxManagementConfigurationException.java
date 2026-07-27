package io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.exception;

/**
 * Management 配置异常。
 *
 * @author surezzzzzz
 */
public class KafkaOutboxManagementConfigurationException extends KafkaOutboxManagementException {

    public KafkaOutboxManagementConfigurationException(String errorCode, String message) {
        super(errorCode, message);
    }
}
