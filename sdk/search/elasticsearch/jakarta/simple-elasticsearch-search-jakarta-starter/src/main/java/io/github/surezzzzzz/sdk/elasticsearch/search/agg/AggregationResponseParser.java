package io.github.surezzzzzz.sdk.elasticsearch.search.agg;

import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.AggDefinition;
import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.AggResponse;

import java.util.*;

import static io.github.surezzzzzz.sdk.elasticsearch.search.constant.SearchProtocolConstant.*;
import static io.github.surezzzzzz.sdk.elasticsearch.search.support.SearchValidationHelper.*;

/**
 * REST 聚合统一为标量、统计对象和 key/count 桶，不暴露 rawResponse。
 */
public class AggregationResponseParser {
    /**
     * 将已验证的 REST 响应转换为公开结果，不返回原响应旁路。
     */
    public AggResponse parse(Map<String, Object> response, List<AggDefinition> definitions) {
        Map<String, Object> raw = object(response.get(VALUE_AGGREGATIONS)), result = new LinkedHashMap<>();
        Map<String, Map<String, Object>> after = new LinkedHashMap<>();
        for (AggDefinition definition : definitions)
            result.put(definition.getName(), parseValue(object(raw.get(definition.getName())), definition, after));
        return AggResponse.builder().aggregations(result).took(number(response, VALUE_TOOK)).afterKey(after.isEmpty() ? null : after).rawResponse(null).build();
    }

    private Object parseValue(Map<String, Object> value, AggDefinition definition, Map<String, Map<String, Object>> after) {
        if (value.containsKey(VALUE_AFTER_KEY)) after.put(definition.getName(), object(value.get(VALUE_AFTER_KEY)));
        if (definition.getTypeEnum().isMetrics()) {
            if (Arrays.asList(io.github.surezzzzzz.sdk.elasticsearch.search.constant.AggType.PERCENTILES,
                    io.github.surezzzzzz.sdk.elasticsearch.search.constant.AggType.PERCENTILE_RANKS).contains(definition.getTypeEnum()))
                return object(value.get(VALUE_VALUES));
            if (Arrays.asList(io.github.surezzzzzz.sdk.elasticsearch.search.constant.AggType.STATS,
                    io.github.surezzzzzz.sdk.elasticsearch.search.constant.AggType.EXTENDED_STATS).contains(definition.getTypeEnum())) {
                Map<String, Object> result = new LinkedHashMap<>();
                for (String key : Arrays.asList(VALUE_COUNT, VALUE_MIN, VALUE_MAX, VALUE_AVG, VALUE_SUM, VALUE_SUM_OF_SQUARES, VALUE_VARIANCE, VALUE_VARIANCE_POPULATION, VALUE_VARIANCE_SAMPLING,
                        VALUE_STD_DEVIATION, VALUE_STD_DEVIATION_POPULATION, VALUE_STD_DEVIATION_SAMPLING, VALUE_STD_DEVIATION_BOUNDS))
                    if (value.containsKey(key)) result.put(key, value.get(key));
                if (!result.keySet().containsAll(Arrays.asList(VALUE_COUNT, VALUE_MIN, VALUE_MAX, VALUE_AVG, VALUE_SUM)))
                    throw protocol();
                return result;
            }
            if (!value.containsKey(VALUE_VALUE) || value.get(VALUE_VALUE) != null && !(value.get(VALUE_VALUE) instanceof Number))
                throw protocol();
            return value.get(VALUE_VALUE);
        }
        if (value.containsKey(VALUE_BUCKETS)) {
            Object raw = value.get(VALUE_BUCKETS);
            List<Map<String, Object>> buckets = new ArrayList<>();
            if (raw instanceof List)
                for (Object bucket : (List<?>) raw) buckets.add(bucket(object(bucket), definition, null, after));
            else if (raw instanceof Map) for (Map.Entry<String, Object> bucket : object(raw).entrySet())
                buckets.add(bucket(object(bucket.getValue()), definition, bucket.getKey(), after));
            else throw protocol();
            return buckets;
        }
        return bucket(value, definition, null, after);
    }

    private Map<String, Object> bucket(Map<String, Object> raw, AggDefinition definition, String explicitKey, Map<String, Map<String, Object>> after) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (explicitKey != null) result.put(VALUE_KEY, explicitKey);
        else if (raw.containsKey(VALUE_KEY))
            result.put(VALUE_KEY, raw.containsKey(VALUE_KEY_AS_STRING) ? raw.get(VALUE_KEY_AS_STRING) : raw.get(VALUE_KEY));
        result.put(VALUE_COUNT, number(raw, VALUE_DOC_COUNT));
        if (definition.getAggs() != null) for (AggDefinition child : definition.getAggs())
            result.put(child.getName(), parseValue(object(raw.get(child.getName())), child, after));
        return result;
    }
}
