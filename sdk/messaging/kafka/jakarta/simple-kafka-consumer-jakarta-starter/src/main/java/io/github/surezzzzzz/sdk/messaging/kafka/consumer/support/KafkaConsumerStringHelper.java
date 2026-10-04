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
     * 生成可进入事件和死信 header 的脱敏异常类别。
     *
     * <p>异常消息可能携带认证信息、手机号、SQL 或业务数据，因此仅保留异常简单类名。
     *
     * @param cause 异常
     * @return 异常简单类名；无法识别时返回固定安全值
     */
    public static String safeExceptionSummary(Throwable cause) {
        if (cause == null) {
            return SimpleKafkaConsumerConstant.DEFAULT_EXCEPTION_SUMMARY;
        }
        String simpleName = cause.getClass().getSimpleName();
        return hasText(simpleName) ? simpleName : SimpleKafkaConsumerConstant.DEFAULT_EXCEPTION_SUMMARY;
    }
}
