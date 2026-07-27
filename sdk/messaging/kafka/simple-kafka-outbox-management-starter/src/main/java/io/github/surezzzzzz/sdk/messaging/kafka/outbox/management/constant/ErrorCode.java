package io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.constant;

/**
 * Management 错误码。
 *
 * @author surezzzzzz
 */
public final class ErrorCode {

    public static final String CONFIGURATION_INVALID = "KAFKA_OUTBOX_MANAGEMENT_001";
    public static final String PERSISTENCE_FAILED = "KAFKA_OUTBOX_MANAGEMENT_002";
    public static final String RECORD_NOT_FOUND = "KAFKA_OUTBOX_MANAGEMENT_003";
    public static final String RECORD_STATE_CONFLICT = "KAFKA_OUTBOX_MANAGEMENT_004";
    public static final String REQUEST_INVALID = "KAFKA_OUTBOX_MANAGEMENT_005";

    private ErrorCode() {
        throw new UnsupportedOperationException("常量类不能实例化");
    }
}
