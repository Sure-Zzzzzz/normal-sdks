package io.github.surezzzzzz.sdk.kafka.route.support;

/**
 * Kafka route 字符串 Helper
 *
 * @author surezzzzzz
 */
public final class KafkaRouteStringHelper {

    private KafkaRouteStringHelper() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
