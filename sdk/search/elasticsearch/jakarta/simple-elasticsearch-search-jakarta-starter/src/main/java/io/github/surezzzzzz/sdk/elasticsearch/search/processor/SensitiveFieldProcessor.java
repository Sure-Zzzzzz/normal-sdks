package io.github.surezzzzzz.sdk.elasticsearch.search.processor;

import io.github.surezzzzzz.sdk.elasticsearch.search.configuration.SimpleElasticsearchSearchProperties.IndexConfig;
import io.github.surezzzzzz.sdk.elasticsearch.search.configuration.SimpleElasticsearchSearchProperties.SensitiveFieldConfig;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.github.surezzzzzz.sdk.elasticsearch.search.constant.SearchProtocolConstant.*;
import static io.github.surezzzzzz.sdk.elasticsearch.search.support.SearchValidationHelper.field;
import static io.github.surezzzzzz.sdk.elasticsearch.search.support.SearchValidationHelper.text;

/**
 * 操作门禁与返回数据保护，包括点路径、嵌套对象和数组。
 */
public class SensitiveFieldProcessor {
    /**
     * 禁止字段与其子字段不能查询；MASK 仅可过滤/投影，不输出排序值或桶键。
     */
    public void require(IndexConfig config, String field, boolean valueLeavesSource) {
        if (!text(field)) throw field();
        for (SensitiveFieldConfig rule : config.getSensitiveFields()) {
            boolean related = field.equals(rule.getField()) || field.startsWith(rule.getField() + DOT) || rule.getField().startsWith(field + DOT);
            if (related && (VALUE_FORBIDDEN.equals(rule.getStrategy()) || valueLeavesSource)) throw field();
        }
    }

    /**
     * 返回独立 Map；不改写调用方持有的原始 hit source。
     */
    public Map<String, Object> protect(IndexConfig config, Map<String, Object> source) {
        return protectMap(config, source, EMPTY);
    }

    private Map<String, Object> protectMap(IndexConfig config, Map<String, Object> source, String parent) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String path = parent.isEmpty() ? entry.getKey() : parent + DOT + entry.getKey();
            SensitiveFieldConfig rule = rule(config, path);
            if (rule != null && VALUE_FORBIDDEN.equals(rule.getStrategy())) continue;
            Object value = entry.getValue();
            if (rule != null && VALUE_MASK.equals(rule.getStrategy())) value = mask(value, rule);
            else value = descend(config, value, path);
            result.put(entry.getKey(), value);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Object descend(IndexConfig config, Object value, String path) {
        if (value instanceof Map) return protectMap(config, (Map<String, Object>) value, path);
        if (value instanceof List) {
            List<Object> result = new ArrayList<>();
            for (Object item : (List<?>) value) result.add(descend(config, item, path));
            return result;
        }
        return value;
    }

    private SensitiveFieldConfig rule(IndexConfig config, String path) {
        SensitiveFieldConfig mask = null;
        for (SensitiveFieldConfig rule : config.getSensitiveFields())
            if (path.equals(rule.getField()) || path.startsWith(rule.getField() + DOT)) {
                if (VALUE_FORBIDDEN.equals(rule.getStrategy())) return rule;
                mask = rule;
            }
        return mask;
    }

    private Object mask(Object value, SensitiveFieldConfig rule) {
        if (value == null) return null;
        if (value instanceof List) {
            List<Object> result = new ArrayList<>();
            for (Object item : (List<?>) value) result.add(mask(item, rule));
            return result;
        }
        if (!(value instanceof String)) return rule.getMaskPattern();
        String text = (String) value;
        int start = rule.getMaskStart() == null ? 0 : rule.getMaskStart(), end = rule.getMaskEnd() == null ? 0 : rule.getMaskEnd();
        if ((long) start + end >= text.length()) return rule.getMaskPattern();
        return text.substring(0, start) + rule.getMaskPattern() + text.substring(text.length() - end);
    }
}
