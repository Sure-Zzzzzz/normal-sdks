package io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.exception;

/**
 * Outbox 记录状态冲突异常。
 *
 * @author surezzzzzz
 */
public class KafkaOutboxRecordStateConflictException extends KafkaOutboxManagementException {

    public KafkaOutboxRecordStateConflictException(String errorCode, String message) {
        super(errorCode, message);
    }
}
