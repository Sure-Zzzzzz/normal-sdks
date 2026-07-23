package io.github.surezzzzzz.sdk.kafka.route.support;

import io.github.surezzzzzz.sdk.kafka.route.constant.SimpleKafkaRouteConstant;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Kafka route 敏感日志 Helper
 *
 * @author surezzzzzz
 */
public final class KafkaRouteSensitiveLogHelper {

    private KafkaRouteSensitiveLogHelper() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 脱敏配置 map 中的敏感 key
     *
     * @param config 原始配置
     * @return 脱敏后的新 map
     */
    public static Map<String, Object> maskSensitiveKeys(Map<String, Object> config) {
        Map<String, Object> masked = new LinkedHashMap<>();
        if (config == null || config.isEmpty()) {
            return masked;
        }
        for (Map.Entry<String, Object> entry : config.entrySet()) {
            if (isSensitiveKey(entry.getKey())) {
                masked.put(entry.getKey(), SimpleKafkaRouteConstant.MASKED_VALUE);
            } else {
                masked.put(entry.getKey(), entry.getValue());
            }
        }
        return masked;
    }

    /**
     * 判断 Kafka 配置 key 是否敏感
     *
     * @param key Kafka 配置 key
     * @return true 表示敏感 key
     */
    public static boolean isSensitiveKey(String key) {
        if (!KafkaRouteStringHelper.hasText(key)) {
            return false;
        }
        String lowerKey = key.trim().toLowerCase(Locale.ROOT);
        for (String fragment : SimpleKafkaRouteConstant.SENSITIVE_KEY_FRAGMENTS) {
            if (lowerKey.contains(fragment)) {
                return true;
            }
        }
        return false;
    }
}
