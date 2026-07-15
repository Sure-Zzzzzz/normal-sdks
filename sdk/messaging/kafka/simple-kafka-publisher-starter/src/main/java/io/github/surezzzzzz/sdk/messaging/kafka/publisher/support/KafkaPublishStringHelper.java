package io.github.surezzzzzz.sdk.messaging.kafka.publisher.support;

import io.github.surezzzzzz.sdk.messaging.kafka.publisher.constant.SimpleKafkaPublisherConstant;

/**
 * Kafka 发布字符串 Helper
 *
 * @author surezzzzzz
 */
public final class KafkaPublishStringHelper {

    private KafkaPublishStringHelper() {
        throw new UnsupportedOperationException(SimpleKafkaPublisherConstant.UTILITY_CLASS_MESSAGE);
    }

    /**
     * 判断字符串是否有文本
     *
     * @param value 字符串
     * @return true 有文本，false 无文本
     */
    public static boolean hasText(String value) {
        return value != null && value.trim().length() > SimpleKafkaPublisherConstant.ZERO;
    }

    /**
     * 安全 trim
     *
     * @param value 字符串
     * @return trim 后字符串
     */
    public static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() == SimpleKafkaPublisherConstant.ZERO) {
            return null;
        }
        return trimmed;
    }

    /**
     * 判断是否包含控制字符
     *
     * @param value 字符串
     * @return true 包含，false 不包含
     */
    public static boolean containsControlCharacter(String value) {
        if (value == null) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (Character.isISOControl(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }
}
