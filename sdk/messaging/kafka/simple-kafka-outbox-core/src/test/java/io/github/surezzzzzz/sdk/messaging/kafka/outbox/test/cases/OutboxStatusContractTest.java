package io.github.surezzzzzz.sdk.messaging.kafka.outbox.test.cases;

import io.github.surezzzzzz.sdk.messaging.kafka.outbox.constant.OutboxStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OutboxStatusContractTest {

    @Test
    void shouldRetainOutboxStatusContract() {
        String[] codes = OutboxStatus.getAllCodes();
        assertArrayEquals(new String[]{"PENDING", "PROCESSING", "RETRY_WAIT", "SENT", "POISON"}, codes,
                "状态代码顺序必须保持稳定");
        assertEquals(OutboxStatus.PENDING, OutboxStatus.fromCode("pending"), "状态代码必须支持忽略大小写解析");
        assertEquals("待投递", OutboxStatus.PENDING.getDescription(), "状态说明必须保持稳定");
        assertNull(OutboxStatus.fromCode("UNKNOWN"), "未知状态必须返回 null");
        assertNull(OutboxStatus.fromCode(null), "空状态必须返回 null");
        assertTrue(OutboxStatus.isValid("SENT"), "已发送状态必须有效");
        assertFalse(OutboxStatus.isValid("UNKNOWN"), "未知状态必须无效");
        assertEquals("POISON", OutboxStatus.POISON.toString(), "toString 必须返回状态代码");
    }
}
