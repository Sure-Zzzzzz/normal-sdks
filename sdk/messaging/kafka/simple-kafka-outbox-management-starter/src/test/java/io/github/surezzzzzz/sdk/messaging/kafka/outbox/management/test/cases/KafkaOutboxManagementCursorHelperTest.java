package io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.test.cases;

import io.github.surezzzzzz.sdk.messaging.kafka.outbox.constant.OutboxStatus;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.exception.KafkaOutboxManagementException;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.support.KafkaOutboxManagementCursorHelper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 游标编解码测试。
 *
 * @author surezzzzzz
 */
@Slf4j
class KafkaOutboxManagementCursorHelperTest {
    @Test
    void shouldRoundTripAndBindStatus() {
        Instant availableAt = Instant.parse("2026-01-02T03:04:05.678Z");
        String cursor = KafkaOutboxManagementCursorHelper.encode(OutboxStatus.POISON, availableAt, 7L);
        KafkaOutboxManagementCursorHelper.Cursor decoded = KafkaOutboxManagementCursorHelper.decode(cursor, OutboxStatus.POISON);
        log.info("游标编码结果：{}", cursor);
        assertEquals(availableAt, decoded.getAvailableAt());
        assertEquals(Long.valueOf(7L), decoded.getRecordId());
        assertThrows(KafkaOutboxManagementException.class,
                () -> KafkaOutboxManagementCursorHelper.decode(cursor, OutboxStatus.PENDING));
    }

    @Test
    void shouldRejectMalformedCursor() {
        assertThrows(KafkaOutboxManagementException.class,
                () -> KafkaOutboxManagementCursorHelper.decode("invalid", OutboxStatus.PENDING));
        String zeroId = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "PENDING|0|0".getBytes(StandardCharsets.UTF_8));
        assertThrows(KafkaOutboxManagementException.class,
                () -> KafkaOutboxManagementCursorHelper.decode(zeroId, OutboxStatus.PENDING));
    }
}
