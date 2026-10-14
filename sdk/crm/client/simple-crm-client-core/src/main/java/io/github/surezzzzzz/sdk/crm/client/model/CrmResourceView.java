package io.github.surezzzzzz.sdk.crm.client.model;

import java.util.Map;

/**
 * CRM wire 资源视图：严格字段校验后的不可变结果（键与稳定 code 与服务端 openapi 对齐）。
 *
 * @author surezzzzzz
 */
public final class CrmResourceView {

    private final Map<String, Object> fields;

    /**
     * 创建资源视图。
     *
     * @param fields 已校验字段集
     */
    public CrmResourceView(Map<String, Object> fields) {
        this.fields = java.util.Collections.unmodifiableMap(
                new java.util.LinkedHashMap<String, Object>(fields));
    }

    /**
     * 读字段值。
     *
     * @param key 字段键
     * @return 值（缺失抛异常）
     */
    public Object get(String key) {
        Object value = fields.get(key);
        if (value == null) {
            throw new IllegalArgumentException("missing field: " + key);
        }
        return value;
    }

    /**
     * 读字符串字段。
     *
     * @param key 字段键
     * @return 字符串
     */
    public String getString(String key) {
        return String.valueOf(get(key));
    }

    /**
     * 读整数字段。
     *
     * @param key 字段键
     * @return 整数
     */
    public int getInt(String key) {
        Object value = get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return new java.math.BigDecimal(String.valueOf(value)).intValueExact();
    }

    /**
     * 读可空字段。
     *
     * @param key 字段键
     * @return 值或 null
     */
    public Object getNullable(String key) {
        return fields.get(key);
    }

    /**
     * 字段集快照。
     *
     * @return 不可变 Map
     */
    public Map<String, Object> asMap() {
        return fields;
    }
}
