package io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.support;

import io.github.surezzzzzz.sdk.messaging.kafka.outbox.constant.OutboxStatus;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.constant.ErrorCode;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.exception.KafkaOutboxManagementException;
import lombok.Builder;
import lombok.Getter;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

/**
 * Outbox 游标编解码工具。
 *
 * @author surezzzzzz
 */
public final class KafkaOutboxManagementCursorHelper {
    private KafkaOutboxManagementCursorHelper() {
    }

    /**
     * 编码游标。
     */
    public static String encode(OutboxStatus status, Instant availableAt, Long recordId) {
        String value = status.getCode() + "|" + availableAt.toEpochMilli() + "|" + recordId;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 解码并校验游标。
     */
    public static Cursor decode(String cursor, OutboxStatus expectedStatus) {
        try {
            String value = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = value.split("\\|", -1);
            OutboxStatus status = parts.length == 3 ? OutboxStatus.fromCode(parts[0]) : null;
            if (status != expectedStatus) {
                throw invalid();
            }
            long epochMilli = Long.parseLong(parts[1]);
            long recordId = Long.parseLong(parts[2]);
            if (recordId < 1) {
                throw invalid();
            }
            return Cursor.builder().availableAt(Instant.ofEpochMilli(epochMilli)).recordId(recordId).build();
        } catch (IllegalArgumentException ex) {
            throw invalid();
        }
    }

    private static KafkaOutboxManagementException invalid() {
        return new KafkaOutboxManagementException(ErrorCode.REQUEST_INVALID, ErrorMessage.REQUEST_INVALID);
    }

    /**
     * 游标内容。
     */
    @Getter
    @Builder
    public static class Cursor {
        private final Instant availableAt;
        private final Long recordId;
    }
}
