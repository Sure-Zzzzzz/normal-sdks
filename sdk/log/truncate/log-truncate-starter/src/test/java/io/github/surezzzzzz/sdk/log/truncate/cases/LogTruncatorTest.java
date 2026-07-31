package io.github.surezzzzzz.sdk.log.truncate.cases;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.log.truncate.configuration.LogTruncateProperties;
import io.github.surezzzzzz.sdk.log.truncate.support.LogTruncator;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 日志截断器测试
 *
 * @author surezzzzzz
 */
@Slf4j
public class LogTruncatorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 验证空值和未超限内容保持既有语义
     */
    @Test
    public void shouldKeepNullAndUntruncatedValues() {
        LogTruncator truncator = newTruncator(1024, 1024, 8);

        assertNull(truncator.truncateRaw(null));
        assertEquals("null", truncator.truncate(null));
        assertEquals("中文😀abc", truncator.truncateRaw("中文😀abc"));
        assertEquals("中文😀abc", truncator.truncate("中文😀abc"));
    }

    /**
     * 验证总字节上限严格限制最终输出
     */
    @Test
    public void shouldKeepTotalOutputWithinUtf8Limit() {
        LogTruncator truncator = newTruncator(18, 1024, 8);
        String result = truncator.truncateRaw("中文😀中文😀中文😀");

        log.info("总字节截断结果：{}", result);
        assertTrue(utf8Length(result) <= 18);
        assertFalse(result.contains("�"));
    }

    /**
     * 验证截断标记中 dropped 位数变化不突破总字节上限
     */
    @Test
    public void shouldRespectTotalLimitAcrossDroppedDigitBoundaries() {
        assertTotalLimitForDroppedBoundary(repeat("a", 28), 20, 9);
        assertTotalLimitForDroppedBoundary(repeat("a", 29), 20, 11);
        assertTotalLimitForDroppedBoundary(repeat("a", 127), 30, 99);
        assertTotalLimitForDroppedBoundary(repeat("a", 128), 30, 101);
    }

    /**
     * 验证 JSON 文本字段按 code point 严格截断
     */
    @Test
    public void shouldKeepTextFieldWithinCodePointLimit() throws Exception {
        LogTruncator truncator = newTruncator(4096, 14, 8);
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("message", "😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀");

        String result = truncator.truncate(value);
        JsonNode message = objectMapper.readTree(result).get("message");

        log.info("字段截断结果：{}", result);
        assertTrue(codePointLength(message.asText()) <= 14);
        assertFalse(message.asText().contains("�"));
    }

    /**
     * 验证字段截断标记的位数变化不突破字符上限
     */
    @Test
    public void shouldRespectFieldLimitAcrossDroppedDigitBoundaries() throws Exception {
        assertFieldLimitForDroppedBoundary(repeat("a", 28), 20, 9);
        assertFieldLimitForDroppedBoundary(repeat("a", 29), 20, 11);
        assertFieldLimitForDroppedBoundary(repeat("a", 127), 30, 99);
        assertFieldLimitForDroppedBoundary(repeat("a", 128), 30, 101);
    }

    /**
     * 验证无法容纳完整标记时仍不越界
     */
    @Test
    public void shouldPreferStrictLimitWhenMarkerCannotFit() throws Exception {
        LogTruncator totalTruncator = newTruncator(2, 1024, 8);
        String rawResult = totalTruncator.truncateRaw("abcdef");

        LogTruncator fieldTruncator = newTruncator(4096, 2, 8);
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("message", "abcdef");
        JsonNode message = objectMapper.readTree(fieldTruncator.truncate(value)).get("message");

        assertTrue(utf8Length(rawResult) <= 2);
        assertTrue(codePointLength(message.asText()) <= 2);
        assertFalse(rawResult.contains("[truncated"));
        assertFalse(message.asText().contains("[truncated"));
    }

    /**
     * 验证零和负阈值保持安全输出
     */
    @Test
    public void shouldHandleNonPositiveLimits() throws Exception {
        LogTruncator truncator = newTruncator(0, -1, 8);
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("message", "abcdef");

        assertEquals("", truncator.truncateRaw("abcdef"));
        assertEquals("", truncator.truncate(value));

        LogTruncator fieldTruncator = newTruncator(4096, 0, 8);
        JsonNode message = objectMapper.readTree(fieldTruncator.truncate(value)).get("message");
        assertEquals("", message.asText());
    }

    /**
     * 验证空文本配置不会影响安全截断
     */
    @Test
    public void shouldHandleEmptyTextConfiguration() {
        LogTruncateProperties properties = new LogTruncateProperties();
        properties.setMaxTotalBytes(4);
        properties.setEllipsis(null);
        properties.setTruncatedNoteTemplate(null);
        LogTruncator truncator = new LogTruncator(properties);

        assertEquals("abcd", truncator.truncateRaw("abcdef"));
    }

    /**
     * 验证对象和数组在深度边界使用占位符
     */
    @Test
    public void shouldReplaceNestedObjectAndArrayAtDepthLimit() {
        LogTruncator truncator = newTruncator(4096, 1024, 2);
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("items", Arrays.asList(Arrays.asList("value")));

        String result = truncator.truncate(nested);

        assertTrue(result.contains("__depth_exceeded__"));
    }

    /**
     * 验证非正最大深度从根节点开始使用占位符
     */
    @Test
    public void shouldReplaceRootAtNonPositiveDepthLimit() {
        LogTruncator truncator = newTruncator(4096, 1024, 0);
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("message", "content");

        assertEquals("\"__depth_exceeded__\"", truncator.truncate(value));
    }

    /**
     * 验证各文本字段先受字段上限约束，再受总字节上限约束
     */
    @Test
    public void shouldApplyFieldLimitBeforeTotalLimit() throws Exception {
        LogTruncator truncator = newTruncator(55, 24, 8);
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("first", repeat("a", 80));
        value.put("second", repeat("b", 80));

        String fieldLimited = newTruncator(4096, 24, 8).truncate(value);
        String result = truncator.truncate(value);
        JsonNode fieldLimitedNode = objectMapper.readTree(fieldLimited);

        assertEquals("aaaaaa... [truncated 74]", fieldLimitedNode.get("first").asText());
        assertEquals("bbbbbb... [truncated 74]", fieldLimitedNode.get("second").asText());
        assertTrue(utf8Length(fieldLimited) > 55);
        assertTrue(utf8Length(result) <= 55);
        assertTrue(result.contains("[truncated"));
        assertTrue(result.startsWith("{\"first\":\"aaaaaa... [truncated 74]\""));
    }

    /**
     * 验证异常和序列化失败降级文本都受总字节限制
     */
    @Test
    public void shouldTruncateThrowableAndSerializationFallback() {
        LogTruncator truncator = newTruncator(64, 1024, 8);
        String throwableResult = truncator.truncate(new IllegalArgumentException(repeat("异常", 80)));
        String fallbackResult = truncator.truncate(new Object() {
            public String getValue() {
                throw new IllegalStateException(repeat("失败", 80));
            }
        });

        assertNotNull(throwableResult);
        assertNotNull(fallbackResult);
        assertTrue(utf8Length(throwableResult) <= 64);
        assertTrue(utf8Length(fallbackResult) <= 64);
    }

    private void assertTotalLimitForDroppedBoundary(String value, int maxBytes, int expectedDropped) {
        LogTruncateProperties properties = new LogTruncateProperties();
        properties.setMaxTotalBytes(maxBytes);
        properties.setEllipsis("");
        properties.setTruncatedNoteTemplate("{dropped}");
        LogTruncator truncator = new LogTruncator(properties);
        String result = truncator.truncateRaw(value);

        assertTrue(utf8Length(result) <= maxBytes);
        assertEquals(expectedDropped, droppedFromNumericMarker(result));
        assertEquals(value.length() - numericMarkerStart(result), droppedFromNumericMarker(result));
    }

    private void assertFieldLimitForDroppedBoundary(String value, int maxChars, int expectedDropped) throws Exception {
        LogTruncateProperties properties = new LogTruncateProperties();
        properties.setMaxTotalBytes(4096);
        properties.setMaxFieldChars(maxChars);
        properties.setEllipsis("");
        properties.setTruncatedNoteTemplate("{dropped}");
        LogTruncator truncator = new LogTruncator(properties);
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("message", value);
        JsonNode message = objectMapper.readTree(truncator.truncate(source)).get("message");

        assertTrue(codePointLength(message.asText()) <= maxChars);
        assertEquals(expectedDropped, droppedFromNumericMarker(message.asText()));
        assertEquals(value.length() - numericMarkerStart(message.asText()), droppedFromNumericMarker(message.asText()));
    }

    private int droppedFromNumericMarker(String value) {
        return Integer.parseInt(value.substring(numericMarkerStart(value)));
    }

    private int numericMarkerStart(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (Character.isDigit(value.charAt(index))) {
                return index;
            }
        }
        return value.length();
    }

    private LogTruncator newTruncator(int maxTotalBytes, int maxFieldChars, int maxDepth) {
        LogTruncateProperties properties = new LogTruncateProperties();
        properties.setMaxTotalBytes(maxTotalBytes);
        properties.setMaxFieldChars(maxFieldChars);
        properties.setMaxDepth(maxDepth);
        return new LogTruncator(properties);
    }

    private int utf8Length(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }

    private int codePointLength(String value) {
        return value.codePointCount(0, value.length());
    }

    private String repeat(String value, int count) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < count; index++) {
            builder.append(value);
        }
        return builder.toString();
    }
}
