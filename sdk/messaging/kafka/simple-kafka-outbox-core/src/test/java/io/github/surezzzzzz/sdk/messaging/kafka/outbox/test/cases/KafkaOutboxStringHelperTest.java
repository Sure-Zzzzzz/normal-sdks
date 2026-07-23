package io.github.surezzzzzz.sdk.messaging.kafka.outbox.test.cases;

import io.github.surezzzzzz.sdk.messaging.kafka.outbox.constant.KafkaOutboxCoreConstant;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.support.KafkaOutboxStringHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class KafkaOutboxStringHelperTest {

    @Test
    void shouldNormalizeText() {
        assertTrue(KafkaOutboxStringHelper.hasText(" text "), "非空白文本必须有效");
        assertFalse(KafkaOutboxStringHelper.hasText(" \t "), "空白文本必须无效");
        assertEquals("text", KafkaOutboxStringHelper.trimToNull(" text "), "文本必须去首尾空白");
        assertNull(KafkaOutboxStringHelper.trimToNull(" \t "), "空白文本必须转换为 null");
    }

    @Test
    void shouldRejectUnsafeCharacters() {
        assertEquals(KafkaOutboxCoreConstant.SAFE_VALUE_UNAVAILABLE, KafkaOutboxStringHelper.safeDisplay("safe\nvalue"), "控制字符必须拒绝展示");
        assertEquals(KafkaOutboxCoreConstant.SAFE_VALUE_UNAVAILABLE, KafkaOutboxStringHelper.safeDisplay("safe value"), "行分隔符必须拒绝展示");
        assertEquals(KafkaOutboxCoreConstant.SAFE_VALUE_UNAVAILABLE, KafkaOutboxStringHelper.safeDisplay("safe value"), "段落分隔符必须拒绝展示");
        assertEquals(KafkaOutboxCoreConstant.SAFE_VALUE_UNAVAILABLE, KafkaOutboxStringHelper.truncateErrorSummary("safe‎value"),
                "格式字符必须拒绝写入错误摘要");
    }

    @Test
    void shouldApplyCodePointBoundaries() {
        String safeDisplayBoundary = repeat("😀", KafkaOutboxCoreConstant.MAX_SAFE_DISPLAY_CODE_POINTS);
        String oversizedDisplay = safeDisplayBoundary + "😀";
        String summaryBoundary = repeat("a", KafkaOutboxCoreConstant.MAX_ERROR_SUMMARY_CODE_POINTS - 1) + "😀";
        String oversizedSummary = summaryBoundary + "b";

        assertEquals(safeDisplayBoundary, KafkaOutboxStringHelper.safeDisplay(safeDisplayBoundary),
                "256 个 Unicode 字符必须允许展示");
        assertEquals(KafkaOutboxCoreConstant.SAFE_VALUE_UNAVAILABLE, KafkaOutboxStringHelper.safeDisplay(oversizedDisplay),
                "超过 256 个 Unicode 字符必须拒绝展示");
        assertEquals(summaryBoundary, KafkaOutboxStringHelper.truncateErrorSummary(summaryBoundary),
                "摘要边界不能截断代理对");
        assertEquals(summaryBoundary, KafkaOutboxStringHelper.truncateErrorSummary(oversizedSummary),
                "摘要必须按 Unicode 字符边界截断");
    }

    private String repeat(String value, int count) {
        StringBuilder builder = new StringBuilder(value.length() * count);
        for (int index = 0; index < count; index++) {
            builder.append(value);
        }
        return builder.toString();
    }
}
