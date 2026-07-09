package io.github.surezzzzzz.sdk.elasticsearch.search.agg.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.elasticsearch.search.annotation.SimpleElasticsearchSearchComponent;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.SimpleElasticsearchSearchConstant;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.MultiBucketsAggregation;
import org.elasticsearch.search.aggregations.bucket.SingleBucketAggregation;
import org.elasticsearch.search.aggregations.bucket.composite.CompositeAggregation;

import java.lang.reflect.Method;
import java.util.*;

/**
 * 聚合响应解析器
 *
 * <p>将 ES 聚合响应（ES 7.x Java 对象 / ES 6.x 原始 JSON）统一解析为 Map/List 结构。
 * 从 {@link AggExecutor} 中提取，职责单一，便于独立测试和扩展。
 *
 * <p>ES 7.x 的 metrics 类（Stats / ExtendedStats / Percentiles / PercentileRanks / Percentile /
 * NumericMetricsAggregation）在 6.x 客户端下包路径不同（6.x 在 metrics.stats / metrics.percentiles
 * 子包，7.x 提到 metrics 顶层），故 ES 7.x 路径全部走反射，避免编译期硬依赖。
 *
 * @author surezzzzzz
 */
@SimpleElasticsearchSearchComponent
@Slf4j
public class AggregationResponseParser {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 解析单个聚合（ES 7.x 路径）
     *
     * <p>类型分发顺序不可随意调整：
     * <ul>
     *   <li>ExtendedStats 必须在 Stats 之前（ExtendedStats extends Stats）</li>
     *   <li>PercentileRanks 必须在 Percentiles 之前（两者都实现 Iterable&lt;Percentile&gt;）</li>
     * </ul>
     */
    public Object parse(Aggregation aggregation) {
        Object singleValue = tryNumericSingleValue(aggregation);
        if (singleValue != null) {
            return singleValue;
        }
        // ExtendedStats 必须在 Stats 之前
        Object extendedStats = tryExtendedStats(aggregation);
        if (extendedStats != null) {
            return extendedStats;
        }
        Object stats = tryStats(aggregation);
        if (stats != null) {
            return stats;
        }
        // PercentileRanks 必须在 Percentiles 之前
        Object percentileRanks = tryPercentileRanks(aggregation);
        if (percentileRanks != null) {
            return percentileRanks;
        }
        Object percentiles = tryPercentiles(aggregation);
        if (percentiles != null) {
            return percentiles;
        }
        if (aggregation instanceof SingleBucketAggregation) {
            return parseSingleBucket((SingleBucketAggregation) aggregation);
        }
        if (aggregation instanceof MultiBucketsAggregation) {
            return parseBuckets((MultiBucketsAggregation) aggregation);
        }
        return aggregation.toString();
    }

    /**
     * 解析 composite 聚合（ES 7.x 路径）
     */
    public List<Map<String, Object>> parseComposite(CompositeAggregation aggregation) {
        return parseBuckets(aggregation);
    }

    /**
     * 解析 ES 6.x 原始 JSON 聚合响应
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> parseEs6xResponse(String responseJson) throws Exception {
        Map<String, Object> responseMap = OBJECT_MAPPER.readValue(responseJson,
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
                });
        Map<String, Object> rawAggregations =
                (Map<String, Object>) responseMap.get(SimpleElasticsearchSearchConstant.ES_JSON_AGGREGATIONS);
        if (rawAggregations == null) {
            return Collections.emptyMap();
        }
        return rawAggregations;
    }

    /**
     * 解析 ES 6.x 单个聚合值
     *
     * <p>类型识别顺序：extended_stats（含 sum_of_squares）必须在 stats 之前。
     */
    @SuppressWarnings("unchecked")
    public Object parseEs6xValue(Object aggValue) {
        if (!(aggValue instanceof Map)) {
            return aggValue;
        }
        Map<String, Object> aggMap = (Map<String, Object>) aggValue;

        // 单值指标聚合：{"value": X}
        if (aggMap.containsKey(SimpleElasticsearchSearchConstant.ES_JSON_VALUE) && aggMap.size() == 1) {
            return aggMap.get(SimpleElasticsearchSearchConstant.ES_JSON_VALUE);
        }
        // 单桶聚合：{doc_count: N, sub_agg: {...}}
        if (aggMap.containsKey(SimpleElasticsearchSearchConstant.ES_JSON_DOC_COUNT)
                && !aggMap.containsKey(SimpleElasticsearchSearchConstant.ES_JSON_BUCKETS)) {
            return parseEs6xSingleBucket(aggMap);
        }
        // 基础统计 / 扩展统计聚合
        if (aggMap.containsKey(SimpleElasticsearchSearchConstant.ES_JSON_COUNT)
                && aggMap.containsKey(SimpleElasticsearchSearchConstant.ES_JSON_MIN)
                && aggMap.containsKey(SimpleElasticsearchSearchConstant.ES_JSON_MAX)) {
            // extended_stats 必须在 stats 之前
            if (aggMap.containsKey(SimpleElasticsearchSearchConstant.EXTENDED_STATS_SUM_OF_SQUARES)) {
                return parseEs6xExtendedStats(aggMap);
            }
            return parseEs6xStats(aggMap);
        }
        // 桶聚合
        if (aggMap.containsKey(SimpleElasticsearchSearchConstant.ES_JSON_BUCKETS)) {
            return parseEs6xBuckets(aggMap);
        }
        // 百分位 / 百分位排名聚合：{"values": {"50.0": X, ...}}
        if (aggMap.containsKey(SimpleElasticsearchSearchConstant.ES_JSON_VALUES)
                && aggMap.get(SimpleElasticsearchSearchConstant.ES_JSON_VALUES) instanceof Map) {
            return aggMap.get(SimpleElasticsearchSearchConstant.ES_JSON_VALUES);
        }
        return aggValue;
    }

    // ==================== ES 7.x 私有解析方法（反射，兼容 6.x/7.x 包路径差异）====================

    /**
     * 反射判断并解析 NumericMetricsAggregation.SingleValue（6.x 在 metrics.NumericMetricsAggregation，
     * 7.x 同路径；返回 value() 结果，非 SingleValue 返回 null）。
     */
    private Object tryNumericSingleValue(Aggregation aggregation) {
        Class<?> singleValueClass = loadClass(SimpleElasticsearchSearchConstant.AGG_CLASS_NUMERIC_SINGLE_VALUE);
        if (singleValueClass == null || !singleValueClass.isInstance(aggregation)) {
            return null;
        }
        try {
            Method value = singleValueClass.getMethod(SimpleElasticsearchSearchConstant.AGG_METHOD_VALUE);
            return value.invoke(aggregation);
        } catch (Exception e) {
            return null;
        }
    }

    private Object tryExtendedStats(Aggregation aggregation) {
        Class<?> clazz = loadClass(
                SimpleElasticsearchSearchConstant.AGG_PACKAGE_METRICS_ES7 + "." + SimpleElasticsearchSearchConstant.AGG_CLASS_NAME_EXTENDED_STATS,
                SimpleElasticsearchSearchConstant.AGG_PACKAGE_EXTENDED_STATS_ES6 + "." + SimpleElasticsearchSearchConstant.AGG_CLASS_NAME_EXTENDED_STATS);
        if (clazz == null || !clazz.isInstance(aggregation)) {
            return null;
        }
        try {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put(SimpleElasticsearchSearchConstant.AGG_RESULT_COUNT, invokeNoArg(aggregation, SimpleElasticsearchSearchConstant.AGG_METHOD_GET_COUNT));
            map.put(SimpleElasticsearchSearchConstant.STATS_RESULT_MIN, invokeNoArg(aggregation, SimpleElasticsearchSearchConstant.AGG_METHOD_GET_MIN));
            map.put(SimpleElasticsearchSearchConstant.STATS_RESULT_MAX, invokeNoArg(aggregation, SimpleElasticsearchSearchConstant.AGG_METHOD_GET_MAX));
            map.put(SimpleElasticsearchSearchConstant.STATS_RESULT_AVG, invokeNoArg(aggregation, SimpleElasticsearchSearchConstant.AGG_METHOD_GET_AVG));
            map.put(SimpleElasticsearchSearchConstant.STATS_RESULT_SUM, invokeNoArg(aggregation, SimpleElasticsearchSearchConstant.AGG_METHOD_GET_SUM));
            map.put(SimpleElasticsearchSearchConstant.EXTENDED_STATS_SUM_OF_SQUARES, invokeNoArg(aggregation, SimpleElasticsearchSearchConstant.AGG_METHOD_GET_SUM_OF_SQUARES));
            map.put(SimpleElasticsearchSearchConstant.EXTENDED_STATS_VARIANCE, invokeNoArg(aggregation, SimpleElasticsearchSearchConstant.AGG_METHOD_GET_VARIANCE));
            map.put(SimpleElasticsearchSearchConstant.EXTENDED_STATS_STD_DEVIATION, invokeNoArg(aggregation, SimpleElasticsearchSearchConstant.AGG_METHOD_GET_STD_DEVIATION));
            Map<String, Object> bounds = new LinkedHashMap<>();
            Class<?> boundsClass = loadClass(
                    SimpleElasticsearchSearchConstant.AGG_PACKAGE_METRICS_ES7 + "." + SimpleElasticsearchSearchConstant.AGG_CLASS_NAME_EXTENDED_STATS_BOUNDS,
                    SimpleElasticsearchSearchConstant.AGG_PACKAGE_EXTENDED_STATS_ES6 + "." + SimpleElasticsearchSearchConstant.AGG_CLASS_NAME_EXTENDED_STATS_BOUNDS);
            Object upper = invokeBounds(aggregation, boundsClass, SimpleElasticsearchSearchConstant.AGG_BOUNDS_UPPER);
            Object lower = invokeBounds(aggregation, boundsClass, SimpleElasticsearchSearchConstant.AGG_BOUNDS_LOWER);
            bounds.put(SimpleElasticsearchSearchConstant.EXTENDED_STATS_BOUNDS_UPPER, upper);
            bounds.put(SimpleElasticsearchSearchConstant.EXTENDED_STATS_BOUNDS_LOWER, lower);
            map.put(SimpleElasticsearchSearchConstant.EXTENDED_STATS_STD_DEVIATION_BOUNDS, bounds);
            return map;
        } catch (Exception e) {
            return null;
        }
    }

    private Object tryStats(Aggregation aggregation) {
        Class<?> clazz = loadClass(
                SimpleElasticsearchSearchConstant.AGG_PACKAGE_METRICS_ES7 + "." + SimpleElasticsearchSearchConstant.AGG_CLASS_NAME_STATS,
                SimpleElasticsearchSearchConstant.AGG_PACKAGE_STATS_ES6 + "." + SimpleElasticsearchSearchConstant.AGG_CLASS_NAME_STATS);
        if (clazz == null || !clazz.isInstance(aggregation)) {
            return null;
        }
        try {
            Map<String, Object> map = new HashMap<>();
            map.put(SimpleElasticsearchSearchConstant.AGG_RESULT_COUNT, invokeNoArg(aggregation, SimpleElasticsearchSearchConstant.AGG_METHOD_GET_COUNT));
            map.put(SimpleElasticsearchSearchConstant.STATS_RESULT_MIN, invokeNoArg(aggregation, SimpleElasticsearchSearchConstant.AGG_METHOD_GET_MIN));
            map.put(SimpleElasticsearchSearchConstant.STATS_RESULT_MAX, invokeNoArg(aggregation, SimpleElasticsearchSearchConstant.AGG_METHOD_GET_MAX));
            map.put(SimpleElasticsearchSearchConstant.STATS_RESULT_AVG, invokeNoArg(aggregation, SimpleElasticsearchSearchConstant.AGG_METHOD_GET_AVG));
            map.put(SimpleElasticsearchSearchConstant.STATS_RESULT_SUM, invokeNoArg(aggregation, SimpleElasticsearchSearchConstant.AGG_METHOD_GET_SUM));
            return map;
        } catch (Exception e) {
            return null;
        }
    }

    private Object tryPercentileRanks(Aggregation aggregation) {
        Class<?> clazz = loadClass(
                SimpleElasticsearchSearchConstant.AGG_PACKAGE_METRICS_ES7 + "." + SimpleElasticsearchSearchConstant.AGG_CLASS_NAME_PERCENTILE_RANKS,
                SimpleElasticsearchSearchConstant.AGG_PACKAGE_PERCENTILES_ES6 + "." + SimpleElasticsearchSearchConstant.AGG_CLASS_NAME_PERCENTILE_RANKS);
        if (clazz == null || !clazz.isInstance(aggregation)) {
            return null;
        }
        return parsePercentileIterable(aggregation, true);
    }

    private Object tryPercentiles(Aggregation aggregation) {
        Class<?> clazz = loadClass(
                SimpleElasticsearchSearchConstant.AGG_PACKAGE_METRICS_ES7 + "." + SimpleElasticsearchSearchConstant.AGG_CLASS_NAME_PERCENTILES,
                SimpleElasticsearchSearchConstant.AGG_PACKAGE_PERCENTILES_ES6 + "." + SimpleElasticsearchSearchConstant.AGG_CLASS_NAME_PERCENTILES);
        if (clazz == null || !clazz.isInstance(aggregation)) {
            return null;
        }
        return parsePercentileIterable(aggregation, false);
    }

    @SuppressWarnings("unchecked")
    private Object parsePercentileIterable(Aggregation aggregation, boolean ranks) {
        try {
            // Percentile 接口 6.x/7.x 包路径不同，反射加载
            Class<?> percentileClass = loadClass(
                    SimpleElasticsearchSearchConstant.AGG_PACKAGE_METRICS_ES7 + "." + SimpleElasticsearchSearchConstant.AGG_CLASS_NAME_PERCENTILE,
                    SimpleElasticsearchSearchConstant.AGG_PACKAGE_PERCENTILES_ES6 + "." + SimpleElasticsearchSearchConstant.AGG_CLASS_NAME_PERCENTILE);
            Method getPercent = percentileClass.getMethod(SimpleElasticsearchSearchConstant.AGG_METHOD_GET_PERCENT);
            Method getValue = percentileClass.getMethod(SimpleElasticsearchSearchConstant.AGG_METHOD_GET_VALUE);
            Map<String, Object> map = new LinkedHashMap<>();
            for (Object p : (Iterable<Object>) aggregation) {
                Object percent = getPercent.invoke(p);
                Object value = getValue.invoke(p);
                if (ranks) {
                    map.put(String.valueOf(value), percent);
                } else {
                    map.put(String.valueOf(percent), value);
                }
            }
            return map;
        } catch (Exception e) {
            log.warn("通过反射解析百分位聚合迭代结果失败：{}", e.getMessage());
            return null;
        }
    }

    private static Object invokeNoArg(Object target, String methodName) throws Exception {
        Method m = target.getClass().getMethod(methodName);
        return m.invoke(target);
    }

    private static Object invokeBounds(Object target, Class<?> boundsClass, String boundName) {
        if (boundsClass == null) {
            return null;
        }
        try {
            Object boundEnum = Enum.valueOf((Class<? extends Enum>) boundsClass, boundName);
            Method m = target.getClass().getMethod(SimpleElasticsearchSearchConstant.AGG_METHOD_GET_STD_DEVIATION_BOUND, boundsClass);
            return m.invoke(target, boundEnum);
        } catch (Exception e) {
            return null;
        }
    }

    private static Class<?> loadClass(String... candidateNames) {
        for (String name : candidateNames) {
            try {
                return Class.forName(name);
            } catch (ClassNotFoundException ignored) {
            }
        }
        return null;
    }

    private Map<String, Object> parseSingleBucket(SingleBucketAggregation agg) {
        Map<String, Object> result = new HashMap<>();
        result.put(SimpleElasticsearchSearchConstant.AGG_RESULT_COUNT, agg.getDocCount());
        Aggregations subAggs = agg.getAggregations();
        if (subAggs != null) {
            for (Aggregation subAgg : subAggs) {
                result.put(subAgg.getName(), parse(subAgg));
            }
        }
        return result;
    }

    private List<Map<String, Object>> parseBuckets(MultiBucketsAggregation agg) {
        List<Map<String, Object>> buckets = new ArrayList<>();
        for (MultiBucketsAggregation.Bucket bucket : agg.getBuckets()) {
            Map<String, Object> bucketMap = new HashMap<>();
            bucketMap.put(SimpleElasticsearchSearchConstant.AGG_RESULT_KEY, bucket.getKeyAsString());
            bucketMap.put(SimpleElasticsearchSearchConstant.AGG_RESULT_COUNT, bucket.getDocCount());
            Aggregations subAggs = bucket.getAggregations();
            if (subAggs != null && !subAggs.asList().isEmpty()) {
                for (Aggregation subAgg : subAggs) {
                    bucketMap.put(subAgg.getName(), parse(subAgg));
                }
            }
            buckets.add(bucketMap);
        }
        return buckets;
    }

    // ==================== ES 6.x 私有解析方法 ====================

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseEs6xSingleBucket(Map<String, Object> aggMap) {
        Map<String, Object> result = new HashMap<>();
        result.put(SimpleElasticsearchSearchConstant.AGG_RESULT_COUNT,
                aggMap.get(SimpleElasticsearchSearchConstant.ES_JSON_DOC_COUNT));
        for (Map.Entry<String, Object> entry : aggMap.entrySet()) {
            if (!entry.getKey().equals(SimpleElasticsearchSearchConstant.ES_JSON_DOC_COUNT)) {
                result.put(entry.getKey(), parseEs6xValue(entry.getValue()));
            }
        }
        return result;
    }

    private Map<String, Object> parseEs6xExtendedStats(Map<String, Object> aggMap) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(SimpleElasticsearchSearchConstant.AGG_RESULT_COUNT,
                aggMap.get(SimpleElasticsearchSearchConstant.ES_JSON_COUNT));
        map.put(SimpleElasticsearchSearchConstant.STATS_RESULT_MIN,
                aggMap.get(SimpleElasticsearchSearchConstant.ES_JSON_MIN));
        map.put(SimpleElasticsearchSearchConstant.STATS_RESULT_MAX,
                aggMap.get(SimpleElasticsearchSearchConstant.ES_JSON_MAX));
        map.put(SimpleElasticsearchSearchConstant.STATS_RESULT_AVG,
                aggMap.get(SimpleElasticsearchSearchConstant.ES_JSON_AVG));
        map.put(SimpleElasticsearchSearchConstant.STATS_RESULT_SUM,
                aggMap.get(SimpleElasticsearchSearchConstant.ES_JSON_SUM));
        map.put(SimpleElasticsearchSearchConstant.EXTENDED_STATS_SUM_OF_SQUARES,
                aggMap.get(SimpleElasticsearchSearchConstant.EXTENDED_STATS_SUM_OF_SQUARES));
        map.put(SimpleElasticsearchSearchConstant.EXTENDED_STATS_VARIANCE,
                aggMap.get(SimpleElasticsearchSearchConstant.EXTENDED_STATS_VARIANCE));
        map.put(SimpleElasticsearchSearchConstant.EXTENDED_STATS_STD_DEVIATION,
                aggMap.get(SimpleElasticsearchSearchConstant.EXTENDED_STATS_STD_DEVIATION));
        map.put(SimpleElasticsearchSearchConstant.EXTENDED_STATS_STD_DEVIATION_BOUNDS,
                aggMap.get(SimpleElasticsearchSearchConstant.EXTENDED_STATS_STD_DEVIATION_BOUNDS));
        return map;
    }

    private Map<String, Object> parseEs6xStats(Map<String, Object> aggMap) {
        Map<String, Object> map = new HashMap<>();
        map.put(SimpleElasticsearchSearchConstant.AGG_RESULT_COUNT,
                aggMap.get(SimpleElasticsearchSearchConstant.ES_JSON_COUNT));
        map.put(SimpleElasticsearchSearchConstant.STATS_RESULT_MIN,
                aggMap.get(SimpleElasticsearchSearchConstant.ES_JSON_MIN));
        map.put(SimpleElasticsearchSearchConstant.STATS_RESULT_MAX,
                aggMap.get(SimpleElasticsearchSearchConstant.ES_JSON_MAX));
        map.put(SimpleElasticsearchSearchConstant.STATS_RESULT_AVG,
                aggMap.get(SimpleElasticsearchSearchConstant.ES_JSON_AVG));
        map.put(SimpleElasticsearchSearchConstant.STATS_RESULT_SUM,
                aggMap.get(SimpleElasticsearchSearchConstant.ES_JSON_SUM));
        return map;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseEs6xBuckets(Map<String, Object> aggMap) {
        List<Map<String, Object>> buckets = new ArrayList<>();
        Object bucketsValue = aggMap.get(SimpleElasticsearchSearchConstant.ES_JSON_BUCKETS);
        if (bucketsValue instanceof List) {
            // 词项 / 日期直方图等聚合：buckets 是数组
            for (Object bucketObj : (List<?>) bucketsValue) {
                if (!(bucketObj instanceof Map)) {
                    continue;
                }
                Map<String, Object> bucket = (Map<String, Object>) bucketObj;
                Map<String, Object> parsedBucket = new HashMap<>();
                Object key = bucket.get(SimpleElasticsearchSearchConstant.ES_JSON_KEY_AS_STRING);
                if (key == null) {
                    key = bucket.get(SimpleElasticsearchSearchConstant.ES_JSON_KEY);
                }
                parsedBucket.put(SimpleElasticsearchSearchConstant.AGG_RESULT_KEY, key);
                parsedBucket.put(SimpleElasticsearchSearchConstant.AGG_RESULT_COUNT,
                        bucket.get(SimpleElasticsearchSearchConstant.ES_JSON_DOC_COUNT));
                for (Map.Entry<String, Object> entry : bucket.entrySet()) {
                    String k = entry.getKey();
                    if (!k.equals(SimpleElasticsearchSearchConstant.ES_JSON_KEY)
                            && !k.equals(SimpleElasticsearchSearchConstant.ES_JSON_KEY_AS_STRING)
                            && !k.equals(SimpleElasticsearchSearchConstant.ES_JSON_DOC_COUNT)) {
                        parsedBucket.put(k, parseEs6xValue(entry.getValue()));
                    }
                }
                buckets.add(parsedBucket);
            }
        } else if (bucketsValue instanceof Map) {
            // 多过滤器聚合：buckets 是对象，key 为过滤器名，value 为 bucket 内容
            Map<String, Object> filtersBuckets = (Map<String, Object>) bucketsValue;
            for (Map.Entry<String, Object> entry : filtersBuckets.entrySet()) {
                Object value = entry.getValue();
                if (!(value instanceof Map)) {
                    continue;
                }
                Map<String, Object> bucket = (Map<String, Object>) value;
                Map<String, Object> parsedBucket = new HashMap<>();
                parsedBucket.put(SimpleElasticsearchSearchConstant.AGG_RESULT_KEY, entry.getKey());
                parsedBucket.put(SimpleElasticsearchSearchConstant.AGG_RESULT_COUNT,
                        bucket.get(SimpleElasticsearchSearchConstant.ES_JSON_DOC_COUNT));
                for (Map.Entry<String, Object> sub : bucket.entrySet()) {
                    String k = sub.getKey();
                    if (!k.equals(SimpleElasticsearchSearchConstant.ES_JSON_DOC_COUNT)) {
                        parsedBucket.put(k, parseEs6xValue(sub.getValue()));
                    }
                }
                buckets.add(parsedBucket);
            }
        }
        return buckets;
    }
}
