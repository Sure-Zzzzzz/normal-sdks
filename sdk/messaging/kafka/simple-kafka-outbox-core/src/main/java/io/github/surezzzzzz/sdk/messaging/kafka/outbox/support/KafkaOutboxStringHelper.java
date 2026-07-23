package io.github.surezzzzzz.sdk.messaging.kafka.outbox.support;

import io.github.surezzzzzz.sdk.messaging.kafka.outbox.constant.KafkaOutboxCoreConstant;

/**
 * Outbox 文本安全处理 Helper。
 *
 * @author surezzzzzz
 */
public final class KafkaOutboxStringHelper {

    private static final int ZERO = 0;

    private KafkaOutboxStringHelper() {
        throw new UnsupportedOperationException(KafkaOutboxCoreConstant.MESSAGE_CONSTANT_CLASS_CANNOT_INSTANTIATE);
    }

    /**
     * 判断文本是否包含非空白字符。
     *
     * @param value 文本
     * @return 是否包含非空白字符
     */
    public static boolean hasText(String value) {
        return trimToNull(value) != null;
    }

    /**
     * 去除首尾空白，并将空白文本转换为 null。
     *
     * @param value 文本
     * @return 规范化后的文本
     */
    public static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() == ZERO ? null : trimmed;
    }

    /**
     * 返回适合安全展示的文本；不安全或超长文本返回占位符。
     *
     * @param value 文本
     * @return 可安全展示的文本、占位符或 null
     */
    public static String safeDisplay(String value) {
        if (value == null) {
            return null;
        }
        if (containsUnsafeCharacter(value) || codePointCount(value) > KafkaOutboxCoreConstant.MAX_SAFE_DISPLAY_CODE_POINTS) {
            return KafkaOutboxCoreConstant.SAFE_VALUE_UNAVAILABLE;
        }
        return value;
    }

    /**
     * 返回可安全持久化的错误摘要，并按 Unicode 字符边界截断。
     *
     * @param value 错误摘要
     * @return 规范化后的错误摘要、占位符或 null
     */
    public static String truncateErrorSummary(String value) {
        if (value == null) {
            return null;
        }
        if (containsUnsafeCharacter(value)) {
            return KafkaOutboxCoreConstant.SAFE_VALUE_UNAVAILABLE;
        }
        int endIndex = offsetByCodePoints(value, KafkaOutboxCoreConstant.MAX_ERROR_SUMMARY_CODE_POINTS);
        return value.substring(ZERO, endIndex);
    }

    private static int codePointCount(String value) {
        return value.codePointCount(ZERO, value.length());
    }

    private static int offsetByCodePoints(String value, int maximumCodePoints) {
        int codePoints = codePointCount(value);
        return codePoints <= maximumCodePoints ? value.length()
                : value.offsetByCodePoints(ZERO, maximumCodePoints);
    }

    private static boolean containsUnsafeCharacter(String value) {
        for (int offset = ZERO; offset < value.length(); ) {
            int codePoint = value.codePointAt(offset);
            int type = Character.getType(codePoint);
            if (Character.isISOControl(codePoint) || type == Character.FORMAT
                    || type == Character.LINE_SEPARATOR || type == Character.PARAGRAPH_SEPARATOR) {
                return true;
            }
            offset += Character.charCount(codePoint);
        }
        return false;
    }
}
