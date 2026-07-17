package io.github.surezzzzzz.sdk.messaging.kafka.publisher.test.cases;

import io.github.surezzzzzz.sdk.messaging.kafka.publisher.constant.SimpleKafkaPublisherConstant;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.support.KafkaPublishStringHelper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Kafka 发布字符串 Helper 测试
 *
 * @author surezzzzzz
 */
@Slf4j
public class KafkaPublishStringHelperTest {

    @Test
    public void testSafeForErrorMessageKeepsNullAndSafeValue() {
        String safeValue = "mock-safe-value";

        log.info("安全错误展示值: {}", KafkaPublishStringHelper.safeForErrorMessage(safeValue));
        assertNull(KafkaPublishStringHelper.safeForErrorMessage(null), "null 应保持为 null");
        assertEquals(safeValue, KafkaPublishStringHelper.safeForErrorMessage(safeValue),
                "安全字符串应原样返回");
    }

    @Test
    public void testSafeForErrorMessageRejectsControlAndUnicodeSeparator() {
        String controlValue = "mock\nforged";
        String unicodeValue = "mock" + Character.toString((char) 0x2029) + "forged";
        String formatValue = "mock" + Character.toString((char) 0x202E) + "forged";

        log.info("控制字符错误展示值: {}", KafkaPublishStringHelper.safeForErrorMessage(controlValue));
        log.info("Unicode 分隔符错误展示值: {}", KafkaPublishStringHelper.safeForErrorMessage(unicodeValue));
        log.info("Unicode format 错误展示值: {}", KafkaPublishStringHelper.safeForErrorMessage(formatValue));
        assertEquals(SimpleKafkaPublisherConstant.ERROR_VALUE_UNSAFE_DISPLAY,
                KafkaPublishStringHelper.safeForErrorMessage(controlValue),
                "控制字符字符串应使用固定占位符");
        assertEquals(SimpleKafkaPublisherConstant.ERROR_VALUE_UNSAFE_DISPLAY,
                KafkaPublishStringHelper.safeForErrorMessage(unicodeValue),
                "Unicode 分隔符字符串应使用固定占位符");
        assertEquals(SimpleKafkaPublisherConstant.ERROR_VALUE_UNSAFE_DISPLAY,
                KafkaPublishStringHelper.safeForErrorMessage(formatValue),
                "Unicode format 字符串应使用固定占位符");
    }

    @Test
    public void testSafeForErrorMessageLengthBoundary() {
        String boundary = repeat('a', SimpleKafkaPublisherConstant.MAX_ERROR_DISPLAY_LENGTH);
        String overBoundary = boundary + "a";

        log.info("错误展示长度边界: boundary={}, overBoundary={}", boundary.length(), overBoundary.length());
        assertEquals(boundary, KafkaPublishStringHelper.safeForErrorMessage(boundary),
                "最大允许长度应原样返回");
        assertEquals(SimpleKafkaPublisherConstant.ERROR_VALUE_UNSAFE_DISPLAY,
                KafkaPublishStringHelper.safeForErrorMessage(overBoundary),
                "超过最大长度应使用固定占位符");
    }

    private String repeat(char value, int count) {
        StringBuilder builder = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            builder.append(value);
        }
        return builder.toString();
    }
}
