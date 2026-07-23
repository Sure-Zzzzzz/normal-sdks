package io.github.surezzzzzz.sdk.messaging.kafka.outbox.test.cases;

import io.github.surezzzzzz.sdk.messaging.kafka.outbox.constant.OutboxPayloadKind;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.constant.OutboxStatus;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.model.OutboxRecord;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class OutboxRecordTest {

    @Test
    void shouldBuildReadOnlyPayloadFreeRecord() {
        Instant now = Instant.parse("2026-07-23T00:00:00Z");
        OutboxRecord record = OutboxRecord.builder()
                .recordId(1L)
                .messageId("message-1")
                .topic("topic-a")
                .payloadKind(OutboxPayloadKind.JSON)
                .snapshotVersion(1)
                .status(OutboxStatus.RETRY_WAIT)
                .attempt(2)
                .availableAt(now)
                .leaseUntil(now.plusSeconds(60))
                .lastErrorCode("OUTBOX_SEND_FAILED")
                .lastErrorSummary("发送失败")
                .createdAt(now)
                .updatedAt(now.plusSeconds(1))
                .build();

        assertEquals(1L, record.getRecordId(), "记录标识必须保持一致");
        assertEquals("message-1", record.getMessageId(), "消息标识必须保持一致");
        assertEquals(OutboxPayloadKind.JSON, record.getPayloadKind(), "payload 类型必须保持强类型");
        assertEquals(OutboxStatus.RETRY_WAIT, record.getStatus(), "状态必须保持强类型");
        assertEquals(now, record.getAvailableAt(), "时间必须保留 Instant 语义");
        assertEquals("发送失败", record.getLastErrorSummary(), "错误摘要必须保持一致");
    }

    @Test
    void shouldExposeOnlyFinalPayloadFreeFields() {
        for (Field field : OutboxRecord.class.getDeclaredFields()) {
            assertTrue(Modifier.isFinal(field.getModifiers()), "记录字段必须只读：" + field.getName());
            assertFalse(field.getName().contains("payloadJson"), "领域模型不能包含 payload 内容");
            assertFalse(field.getName().contains("headersJson"), "领域模型不能包含 headers 内容");
            assertFalse(field.getName().contains("attributesJson"), "领域模型不能包含 attributes 内容");
            assertFalse(field.getName().equals("ownerToken"), "领域模型不能包含 worker 所有权令牌");
            assertFalse(field.getName().equals("version"), "领域模型不能包含 JDBC 乐观锁版本");
        }
        for (Method method : OutboxRecord.class.getMethods()) {
            assertFalse(method.getName().startsWith("set"), "领域模型不能暴露 setter：" + method.getName());
        }
    }
}
