package io.github.surezzzzzz.sdk.messaging.kafka.outbox.test.cases;

import io.github.surezzzzzz.sdk.messaging.kafka.outbox.constant.OutboxPayloadKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OutboxPayloadKindTest {

    @Test
    void shouldRetainPayloadKindContract() {
        assertArrayEquals(new String[]{"STRING", "JSON", "NULL"}, OutboxPayloadKind.getAllCodes(),
                "payload 类型代码顺序必须保持稳定");
        assertEquals(OutboxPayloadKind.JSON, OutboxPayloadKind.fromCode("json"),
                "payload 类型必须支持忽略大小写解析");
        assertEquals("字符串", OutboxPayloadKind.STRING.getDescription(), "类型说明必须保持稳定");
        assertNull(OutboxPayloadKind.fromCode("BINARY"), "未知类型必须返回 null");
        assertNull(OutboxPayloadKind.fromCode(null), "空类型必须返回 null");
        assertTrue(OutboxPayloadKind.isValid("NULL"), "已定义类型必须有效");
        assertFalse(OutboxPayloadKind.isValid("BINARY"), "未知类型必须无效");
        assertEquals("STRING", OutboxPayloadKind.STRING.toString(), "toString 必须返回类型代码");
    }
}
