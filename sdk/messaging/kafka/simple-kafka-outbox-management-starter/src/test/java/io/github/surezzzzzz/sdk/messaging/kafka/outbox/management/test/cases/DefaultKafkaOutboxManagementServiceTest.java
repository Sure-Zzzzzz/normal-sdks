package io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.test.cases;

import io.github.surezzzzzz.sdk.messaging.kafka.outbox.constant.OutboxPayloadKind;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.constant.OutboxStatus;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.configuration.SimpleKafkaOutboxManagementProperties;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.exception.KafkaOutboxRecordNotFoundException;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.exception.KafkaOutboxRecordStateConflictException;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.repository.KafkaOutboxManagementRepository;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.service.DefaultKafkaOutboxManagementService;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.service.KafkaOutboxManagementService;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.model.OutboxRecord;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.EnumMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 默认 Management 服务测试。
 *
 * @author surezzzzzz
 */
@Slf4j
class DefaultKafkaOutboxManagementServiceTest {
    @Test
    void shouldCompleteStatusSummaryAndSafelyDisplayRecordFields() {
        KafkaOutboxManagementRepository repository = mock(KafkaOutboxManagementRepository.class);
        EnumMap<OutboxStatus, Long> counts = new EnumMap<OutboxStatus, Long>(OutboxStatus.class);
        counts.put(OutboxStatus.POISON, 3L);
        when(repository.countByStatus()).thenReturn(counts);
        OutboxRecord record = record(9L, OutboxStatus.POISON);
        when(repository.browse(any())).thenReturn(Arrays.asList(record));
        KafkaOutboxManagementService service = new DefaultKafkaOutboxManagementService(repository, properties());
        assertEquals(OutboxStatus.values().length, service.summaries().size(), "必须补齐五种状态");
        assertEquals(Long.valueOf(0L), service.summaries().get(0).getCount(), "缺失状态必须补零");
        assertEquals("message-id", service.browse("POISON", null, 20).getRecords().get(0).getMessageId(), "消息标识必须可用于人工定位");
        assertEquals("<unsafe>", service.browse("POISON", null, 20).getRecords().get(0).getTopic(), "不安全文本必须不可展示");
        log.info("Management 页面投影不包含 recordKey 与 traceId");
    }

    @Test
    void shouldRejectInvalidBrowseParametersBeforeRepositoryQuery() {
        KafkaOutboxManagementRepository repository = mock(KafkaOutboxManagementRepository.class);
        KafkaOutboxManagementService service = new DefaultKafkaOutboxManagementService(repository, properties());
        assertThrows(io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.exception.KafkaOutboxManagementException.class,
                () -> service.browse("UNKNOWN", null, 20));
        assertThrows(io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.exception.KafkaOutboxManagementException.class,
                () -> service.browse("PENDING", "invalid", 20));
        assertThrows(io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.exception.KafkaOutboxManagementException.class,
                () -> service.browse("PENDING", null, 101));
        verifyNoInteractions(repository);
    }

    @Test
    void shouldReportRecordNotFoundWhenPoisonResetTargetsMissingRecord() {
        KafkaOutboxManagementRepository repository = mock(KafkaOutboxManagementRepository.class);
        when(repository.resetPoison(1L)).thenReturn(0);
        when(repository.findById(1L)).thenReturn(null);
        KafkaOutboxManagementService service = new DefaultKafkaOutboxManagementService(repository, properties());
        assertThrows(KafkaOutboxRecordNotFoundException.class, () -> service.resetPoison(1L));
        verify(repository).findById(1L);
    }

    @Test
    void shouldReportStateConflictWhenPoisonResetLosesCas() {
        KafkaOutboxManagementRepository repository = mock(KafkaOutboxManagementRepository.class);
        when(repository.resetPoison(1L)).thenReturn(0);
        when(repository.findById(1L)).thenReturn(record(1L, OutboxStatus.PENDING));
        KafkaOutboxManagementService service = new DefaultKafkaOutboxManagementService(repository, properties());
        assertThrows(KafkaOutboxRecordStateConflictException.class, () -> service.resetPoison(1L));
        verify(repository).findById(1L);
    }

    private SimpleKafkaOutboxManagementProperties properties() {
        SimpleKafkaOutboxManagementProperties properties = new SimpleKafkaOutboxManagementProperties();
        properties.getAdmin().setUsername("test");
        properties.getAdmin().setPassword("test");
        return properties;
    }

    private OutboxRecord record(Long id, OutboxStatus status) {
        return OutboxRecord.builder().recordId(id).messageId("message-id").topic("unsafe\nvalue").recordKey("key")
                .traceId("trace").payloadKind(OutboxPayloadKind.STRING).status(status).attempt(1)
                .availableAt(Instant.parse("2026-01-01T00:00:00Z")).createdAt(Instant.now()).updatedAt(Instant.now()).build();
    }
}
