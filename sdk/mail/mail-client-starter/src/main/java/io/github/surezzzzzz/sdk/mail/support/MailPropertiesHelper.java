package io.github.surezzzzzz.sdk.mail.support;

import java.util.Map;
import java.util.Properties;

/**
 * Mail 配置 Helper
 *
 * @author surezzzzzz
 */
public final class MailPropertiesHelper {

    private MailPropertiesHelper() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static Properties toProperties(Map<String, Object> map) {
        Properties properties = new Properties();
        if (map == null || map.isEmpty()) {
            return properties;
        }
        flatten(properties, null, map);
        return properties;
    }

    @SuppressWarnings("unchecked")
    private static void flatten(Properties properties, String prefix, Map<String, Object> map) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            String key = prefix == null ? entry.getKey() : prefix + "." + entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Map) {
                flatten(properties, key, (Map<String, Object>) value);
            } else {
                properties.setProperty(key, String.valueOf(value));
            }
        }
    }
}
