package io.github.surezzzzzz.sdk.feedback.server.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 测试断言用 JSON 字段提取（jackson，仅测试引用；items 数组取首元素字段）。
 *
 * @author surezzzzzz
 */
public final class JsonHelper {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonHelper() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 提取响应中首个出现的指定字符串字段（顶层或 items 数组首元素）。
     *
     * @param body  JSON 文本
     * @param field 字段名
     * @return 字段值；不存在返回 null
     */
    public static long longField(String body, String field) {
        try {
            JsonNode root = MAPPER.readTree(body);
            if (root.has(field)) {
                return root.get(field).asLong();
            }
            return 0L;
        } catch (Exception exception) {
            throw new IllegalStateException("JSON 字段提取失败: " + field, exception);
        }
    }

    public static String stringField(String body, String field) {
        try {
            JsonNode root = MAPPER.readTree(body);
            if (root.has(field)) {
                return root.get(field).asText();
            }
            JsonNode items = root.get("items");
            if (items != null && items.isArray() && items.size() > 0 && items.get(0).has(field)) {
                return items.get(0).get(field).asText();
            }
            return null;
        } catch (Exception exception) {
            throw new IllegalStateException("JSON 字段提取失败: " + field, exception);
        }
    }

    /**
     * 提取响应中首个出现的指定布尔字段（顶层或 items 数组首元素）。
     *
     * @param body  JSON 文本
     * @param field 字段名
     * @return 字段值；不存在或非布尔返回 null
     */
    public static Boolean booleanField(String body, String field) {
        try {
            JsonNode root = MAPPER.readTree(body);
            if (root.has(field) && root.get(field).isBoolean()) {
                return root.get(field).asBoolean();
            }
            JsonNode items = root.get("items");
            if (items != null && items.isArray() && items.size() > 0 && items.get(0).has(field)
                    && items.get(0).get(field).isBoolean()) {
                return items.get(0).get(field).asBoolean();
            }
            return null;
        } catch (Exception exception) {
            throw new IllegalStateException("JSON 字段提取失败: " + field, exception);
        }
    }
}
