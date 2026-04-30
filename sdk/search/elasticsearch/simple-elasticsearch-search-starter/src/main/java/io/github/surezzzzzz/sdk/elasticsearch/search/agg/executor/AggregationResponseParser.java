package io.github.surezzzzzz.sdk.elasticsearch.search.agg.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.elasticsearch.search.annotation.SimpleElasticsearchSearchComponent;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.SimpleElasticsearchSearchConstant;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.MultiBucketsAggregation;
import org.elasticsearch.search.aggregations.bucket.SingleBucketAggregation;
import org.elasticsearch.search.aggregations.bucket.composite.CompositeAggregation;
import org.elasticsearch.search.aggregations.metrics.*;

import java.util.*;

/**
 * 聚合响应解析器
 *
 * <p>将 ES 聚合响应（ES 7.x Java 对象 / ES 6.x 原始 JSON）统一解析为 Map/List 结构。
 * 从 {@link AggExecutor} 中提取，职责单一，便于独立测试和扩展。
 *
 * @author surezzzzzz
 */
@SimpleElasticsearchSearchComponent
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
        if (aggregation instanceof NumericMetricsAggregation.SingleValue) {
            return ((NumericMetricsAggregation.SingleValue) aggregation).value();
        }
        // ExtendedStats 必须在 Stats 之前
        if (aggregation instanceof ExtendedStats) {
            return parseExtendedStats((ExtendedStats) aggregation);
        }
        if (aggregation instanceof Stats) {
            return parseStats((Stats) aggregation);
        }
        // PercentileRanks 必须在 Percentiles 之前
        if (aggregation instanceof PercentileRanks) {
            return parsePercentileRanks((PercentileRanks) aggregation);
        }
        if (aggregation instanceof Percentiles) {
            return parsePercentiles((Percentiles) aggregation);
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
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
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

        // SingleValue：{"value": X}
        if (aggMap.containsKey(SimpleElasticsearchSearchConstant.ES_JSON_VALUE) && aggMap.size() == 1) {
            return aggMap.get(SimpleElasticsearchSearchConstant.ES_JSON_VALUE);
        }
        // SingleBucket：{doc_count: N, sub_agg: {...}}
        if (aggMap.containsKey(SimpleElasticsearchSearchConstant.ES_JSON_DOC_COUNT)
                && !aggMap.containsKey(SimpleElasticsearchSearchConstant.ES_JSON_BUCKETS)) {
            return parseEs6xSingleBucket(aggMap);
        }
        // Stats / ExtendedStats
        if (aggMap.containsKey(SimpleElasticsearchSearchConstant.ES_JSON_COUNT)
                && aggMap.containsKey(SimpleElasticsearchSearchConstant.ES_JSON_MIN)
                && aggMap.containsKey(SimpleElasticsearchSearchConstant.ES_JSON_MAX)) {
            // extended_stats 必须在 stats 之前
            if (aggMap.containsKey(SimpleElasticsearchSearchConstant.EXTENDED_STATS_SUM_OF_SQUARES)) {
                return parseEs6xExtendedStats(aggMap);
            }
            return parseEs6xStats(aggMap);
        }
        // Bucket 聚合
        if (aggMap.containsKey(SimpleElasticsearchSearchConstant.ES_JSON_BUCKETS)) {
            return parseEs6xBuckets(aggMap);
        }
        // Percentiles / PercentileRanks：{"values": {"50.0": X, ...}}
        if (aggMap.containsKey(SimpleElasticsearchSearchConstant.ES_JSON_VALUES)
                && aggMap.get(SimpleElasticsearchSearchConstant.ES_JSON_VALUES) instanceof Map) {
            return aggMap.get(SimpleElasticsearchSearchConstant.ES_JSON_VALUES);
        }
        return aggValue;
    }

    // ==================== ES 7.x 私有解析方法 ====================

    private Map<String, Object> parseExtendedStats(ExtendedStats es) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(SimpleElasticsearchSearchConstant.AGG_RESULT_COUNT, es.getCount());
        map.put(SimpleElasticsearchSearchConstant.STATS_RESULT_MIN, es.getMin());
        map.put(SimpleElasticsearchSearchConstant.STATS_RESULT_MAX, es.getMax());
        map.put(SimpleElasticsearchSearchConstant.STATS_RESULT_AVG, es.getAvg());
        map.put(SimpleElasticsearchSearchConstant.STATS_RESULT_SUM, es.getSum());
        map.put(SimpleElasticsearchSearchConstant.EXTENDED_STATS_SUM_OF_SQUARES, es.getSumOfSquares());
        map.put(SimpleElasticsearchSearchConstant.EXTENDED_STATS_VARIANCE, es.getVariance());
        map.put(SimpleElasticsearchSearchConstant.EXTENDED_STATS_STD_DEVIATION, es.getStdDeviation());
        Map<String, Object> bounds = new LinkedHashMap<>();
        bounds.put(SimpleElasticsearchSearchConstant.EXTENDED_STATS_BOUNDS_UPPER,
                es.getStdDeviationBound(ExtendedStats.Bounds.UPPER));
        bounds.put(SimpleElasticsearchSearchConstant.EXTENDED_STATS_BOUNDS_LOWER,
                es.getStdDeviationBound(ExtendedStats.Bounds.LOWER));
        map.put(SimpleElasticsearchSearchConstant.EXTENDED_STATS_STD_DEVIATION_BOUNDS, bounds);
        return map;
    }

    private Map<String, Object> parseStats(Stats stats) {
        Map<String, Object> map = new HashMap<>();
        map.put(SimpleElasticsearchSearchConstant.AGG_RESULT_COUNT, stats.getCount());
        map.put(SimpleElasticsearchSearchConstant.STATS_RESULT_MIN, stats.getMin());
        map.put(SimpleElasticsearchSearchConstant.STATS_RESULT_MAX, stats.getMax());
        map.put(SimpleElasticsearchSearchConstant.STATS_RESULT_AVG, stats.getAvg());
        map.put(SimpleElasticsearchSearchConstant.STATS_RESULT_SUM, stats.getSum());
        return map;
    }

    private Map<String, Object> parsePercentileRanks(PercentileRanks agg) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (Percentile p : agg) {
            map.put(String.valueOf(p.getValue()), p.getPercent());
        }
        return map;
    }

    private Map<String, Object> parsePercentiles(Percentiles agg) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (Percentile p : agg) {
            map.put(String.valueOf(p.getPercent()), p.getValue());
        }
        return map;
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
        if (!(bucketsValue instanceof List)) {
            return buckets;
        }
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
        return buckets;
    }
}
