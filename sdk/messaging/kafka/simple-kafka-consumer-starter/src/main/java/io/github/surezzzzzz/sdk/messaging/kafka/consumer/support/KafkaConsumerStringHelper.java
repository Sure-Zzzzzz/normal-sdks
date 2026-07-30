package io.github.surezzzzzz.sdk.messaging.kafka.consumer.support;

import io.github.surezzzzzz.sdk.messaging.kafka.consumer.constant.SimpleKafkaConsumerConstant;

/**
 * Kafka Consumer 字符串 Helper
 *
 * @author surezzzzzz
 */
public final class KafkaConsumerStringHelper {

    private KafkaConsumerStringHelper() {
        throw new UnsupportedOperationException(SimpleKafkaConsumerConstant.UTILITY_CLASS_MESSAGE);
    }

    /**
     * 判断字符串是否有文本
     *
     * @param value 字符串
     * @return true 有文本，false 无文本
     */
    public static boolean hasText(String value) {
        return value != null && value.trim().length() > SimpleKafkaConsumerConstant.ZERO;
    }

    /**
     * 安全 trim，空串返回 null
     *
     * @param value 字符串
     * @return trim 后字符串，空返回 null
     */
    public static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() == SimpleKafkaConsumerConstant.ZERO) {
            return null;
        }
        return trimmed;
    }

    /**
     * 判断是否包含控制字符或 Unicode 换行字符
     *
     * @param value 字符串
     * @return true 包含，false 不包含
     */
    public static boolean containsControlCharacter(String value) {
        if (value == null) {
            return false;
        }
        for (int offset = 0; offset < value.length(); ) {
            int codePoint = value.codePointAt(offset);
            int type = Character.getType(codePoint);
            if (Character.isISOControl(codePoint)
                    || type == Character.FORMAT
                    || type == Character.LINE_SEPARATOR
                    || type == Character.PARAGRAPH_SEPARATOR) {
                return true;
            }
            offset += Character.charCount(codePoint);
        }
        return false;
    }

    /**
     * 截断错误摘要到最大长度，超出加省略号
     *
     * @param value 原始摘要
     * @return 截断后的摘要
     */
    public static String truncateErrorSummary(String value) {
        if (value == null) {
            return null;
        }
        int max = SimpleKafkaConsumerConstant.ERROR_SUMMARY_MAX_LENGTH;
        if (value.length() <= max) {
            return value;
        }
        int suffixLength = SimpleKafkaConsumerConstant.ERROR_SUMMARY_TRUNCATE_SUFFIX.length();
        int cut = max - suffixLength;
        if (cut <= SimpleKafkaConsumerConstant.ZERO) {
            return SimpleKafkaConsumerConstant.ERROR_SUMMARY_TRUNCATE_SUFFIX;
        }
        return value.substring(0, cut) + SimpleKafkaConsumerConstant.ERROR_SUMMARY_TRUNCATE_SUFFIX;
    }

    /**
     * 转换为可安全写入日志/事件展示的字符串，移除控制字符（不遮蔽内容）
     *
     * @param value 原始字符串
     * @return 安全展示值
     */
    public static String safeDisplay(String value) {
        if (value == null) {
            return null;
        }
        if (!containsControlCharacter(value)) {
            return value;
        }
        StringBuilder builder = new StringBuilder(value.length());
        for (int offset = 0; offset < value.length(); ) {
            int codePoint = value.codePointAt(offset);
            int type = Character.getType(codePoint);
            if (!Character.isISOControl(codePoint)
                    && type != Character.FORMAT
                    && type != Character.LINE_SEPARATOR
                    && type != Character.PARAGRAPH_SEPARATOR) {
                builder.appendCodePoint(codePoint);
            }
            offset += Character.charCount(codePoint);
        }
        return builder.toString();
    }

    /**
     * 用于错误消息的安全字符串：移除控制字符并截断
     *
     * @param value 原始字符串
     * @return 安全展示值
     */
    public static String safeForErrorMessage(String value) {
        String safe = safeDisplay(value);
        return truncateErrorSummary(safe);
    }
}
