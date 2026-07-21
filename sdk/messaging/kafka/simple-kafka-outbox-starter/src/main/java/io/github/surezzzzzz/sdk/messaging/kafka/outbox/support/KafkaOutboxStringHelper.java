package io.github.surezzzzzz.sdk.messaging.kafka.outbox.support;

import io.github.surezzzzzz.sdk.messaging.kafka.outbox.constant.SimpleKafkaOutboxConstant;

/**
 * Kafka Outbox 字符串 Helper
 *
 * @author surezzzzzz
 */
public final class KafkaOutboxStringHelper {

    private KafkaOutboxStringHelper() {
        throw new UnsupportedOperationException(SimpleKafkaOutboxConstant.UTILITY_CLASS_MESSAGE);
    }

    /**
     * 判断是否包含有效文本
     *
     * @param value 字符串
     * @return 是否包含文本
     */
    public static boolean hasText(String value) {
        return value != null && value.trim().length() > SimpleKafkaOutboxConstant.ZERO;
    }

    /**
     * 去除首尾空白，空文本返回 null
     *
     * @param value 字符串
     * @return 标准化字符串
     */
    public static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() == SimpleKafkaOutboxConstant.ZERO ? null : trimmed;
    }

    /**
     * 将值转换为可安全展示的摘要
     *
     * @param value 原值
     * @return 安全值
     */
    public static String safeDisplay(String value) {
        if (value == null) {
            return null;
        }
        if (value.length() > SimpleKafkaOutboxConstant.MAX_SAFE_DISPLAY_LENGTH || containsUnsafeCharacter(value)) {
            return SimpleKafkaOutboxConstant.SAFE_VALUE_UNAVAILABLE;
        }
        return value;
    }

    /**
     * 截断稳定错误摘要
     *
     * @param value 摘要
     * @return 截断后的摘要
     */
    public static String truncateErrorSummary(String value) {
        if (value == null) {
            return null;
        }
        if (containsUnsafeCharacter(value)) {
            return SimpleKafkaOutboxConstant.SAFE_VALUE_UNAVAILABLE;
        }
        return value.length() <= SimpleKafkaOutboxConstant.MAX_ERROR_SUMMARY_LENGTH
                ? value : value.substring(SimpleKafkaOutboxConstant.ZERO,
                SimpleKafkaOutboxConstant.MAX_ERROR_SUMMARY_LENGTH);
    }

    /**
     * 检测控制字符、格式字符与分隔符等不可安全展示的字符。
     */
    private static boolean containsUnsafeCharacter(String value) {
        for (int offset = SimpleKafkaOutboxConstant.ZERO; offset < value.length(); ) {
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
