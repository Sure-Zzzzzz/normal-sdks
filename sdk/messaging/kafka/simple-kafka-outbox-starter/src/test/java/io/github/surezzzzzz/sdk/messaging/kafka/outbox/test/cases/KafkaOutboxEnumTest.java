package io.github.surezzzzzz.sdk.messaging.kafka.outbox.test.cases;

import io.github.surezzzzzz.sdk.messaging.kafka.outbox.constant.OutboxPayloadKind;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.constant.OutboxStatus;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kafka Outbox 枚举标准方法测试
 *
 * @author surezzzzzz
 */
@Slf4j
public class KafkaOutboxEnumTest {

    @Test
    public void testOutboxStatusStandardMethods() {
        String[] expectedCodes = {"PENDING", "PROCESSING", "RETRY_WAIT", "SENT", "POISON"};
        String[] actualCodes = OutboxStatus.getAllCodes();

        log.info("OutboxStatus 预期代码: {}, 实际代码: {}", Arrays.toString(expectedCodes),
                Arrays.toString(actualCodes));
        assertArrayEquals(expectedCodes, actualCodes, "OutboxStatus 应按声明顺序返回全部代码");
        for (OutboxStatus status : OutboxStatus.values()) {
            log.info("OutboxStatus 标准方法输入: {}, 输出: {}", status.getCode().toLowerCase(), status);
            assertEquals(status, OutboxStatus.fromCode(status.getCode().toLowerCase()),
                    "OutboxStatus.fromCode 应忽略大小写");
            assertTrue(OutboxStatus.isValid(status.getCode()), "OutboxStatus.isValid 应接受已知代码");
            assertEquals(status.getCode(), status.toString(), "OutboxStatus.toString 应返回代码");
            assertFalse(status.getDescription().trim().isEmpty(), "OutboxStatus 描述不能为空");
        }

        log.info("OutboxStatus 非法输入: null、空字符串、UNKNOWN");
        assertNull(OutboxStatus.fromCode(null), "OutboxStatus.fromCode(null) 应返回 null");
        assertNull(OutboxStatus.fromCode("UNKNOWN"), "OutboxStatus.fromCode 应拒绝未知代码");
        assertFalse(OutboxStatus.isValid(""), "OutboxStatus.isValid 应拒绝空字符串");
    }

    @Test
    public void testOutboxPayloadKindStandardMethods() {
        String[] expectedCodes = {"STRING", "JSON", "NULL"};
        String[] actualCodes = OutboxPayloadKind.getAllCodes();

        log.info("OutboxPayloadKind 预期代码: {}, 实际代码: {}", Arrays.toString(expectedCodes),
                Arrays.toString(actualCodes));
        assertArrayEquals(expectedCodes, actualCodes, "OutboxPayloadKind 应按声明顺序返回全部代码");
        for (OutboxPayloadKind kind : OutboxPayloadKind.values()) {
            log.info("OutboxPayloadKind 标准方法输入: {}, 输出: {}", kind.getCode().toLowerCase(), kind);
            assertEquals(kind, OutboxPayloadKind.fromCode(kind.getCode().toLowerCase()),
                    "OutboxPayloadKind.fromCode 应忽略大小写");
            assertTrue(OutboxPayloadKind.isValid(kind.getCode()), "OutboxPayloadKind.isValid 应接受已知代码");
            assertEquals(kind.getCode(), kind.toString(), "OutboxPayloadKind.toString 应返回代码");
            assertFalse(kind.getDescription().trim().isEmpty(), "OutboxPayloadKind 描述不能为空");
        }

        log.info("OutboxPayloadKind 非法输入: null、空字符串、UNKNOWN");
        assertNull(OutboxPayloadKind.fromCode(null), "OutboxPayloadKind.fromCode(null) 应返回 null");
        assertNull(OutboxPayloadKind.fromCode("UNKNOWN"), "OutboxPayloadKind.fromCode 应拒绝未知代码");
        assertFalse(OutboxPayloadKind.isValid(""), "OutboxPayloadKind.isValid 应拒绝空字符串");
    }
}
