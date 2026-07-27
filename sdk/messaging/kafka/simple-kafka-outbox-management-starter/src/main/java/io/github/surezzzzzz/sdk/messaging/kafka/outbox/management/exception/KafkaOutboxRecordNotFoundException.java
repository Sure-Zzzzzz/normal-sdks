package io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.exception;

/**
 * Outbox 记录不存在异常。
 *
 * @author surezzzzzz
 */
public class KafkaOutboxRecordNotFoundException extends KafkaOutboxManagementException {

    public KafkaOutboxRecordNotFoundException(String errorCode, String message) {
        super(errorCode, message);
    }
}
