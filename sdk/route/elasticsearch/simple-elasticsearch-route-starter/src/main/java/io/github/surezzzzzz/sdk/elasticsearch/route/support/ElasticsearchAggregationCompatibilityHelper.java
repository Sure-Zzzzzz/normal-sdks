package io.github.surezzzzzz.sdk.elasticsearch.route.support;

import io.github.surezzzzzz.sdk.elasticsearch.route.constant.SimpleElasticsearchRouteConstant;
import org.elasticsearch.script.Script;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

/**
 * Elasticsearch 聚合协议兼容 helper
 *
 * @author surezzzzzz
 */
public final class ElasticsearchAggregationCompatibilityHelper {

    private ElasticsearchAggregationCompatibilityHelper() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static Class<?> loadStatsClass() {
        return ElasticsearchReflectionHelper.loadFirstPresentClass(
                SimpleElasticsearchRouteConstant.AGG_PACKAGE_METRICS_ES7 + "." + SimpleElasticsearchRouteConstant.AGG_CLASS_NAME_STATS,
                SimpleElasticsearchRouteConstant.AGG_PACKAGE_STATS_ES6 + "." + SimpleElasticsearchRouteConstant.AGG_CLASS_NAME_STATS);
    }

    public static Class<?> loadExtendedStatsClass() {
        return ElasticsearchReflectionHelper.loadFirstPresentClass(
                SimpleElasticsearchRouteConstant.AGG_PACKAGE_METRICS_ES7 + "." + SimpleElasticsearchRouteConstant.AGG_CLASS_NAME_EXTENDED_STATS,
                SimpleElasticsearchRouteConstant.AGG_PACKAGE_EXTENDED_STATS_ES6 + "." + SimpleElasticsearchRouteConstant.AGG_CLASS_NAME_EXTENDED_STATS);
    }

    public static Class<?> loadExtendedStatsBoundsClass() {
        return ElasticsearchReflectionHelper.loadFirstPresentClass(
                SimpleElasticsearchRouteConstant.AGG_PACKAGE_METRICS_ES7 + "." + SimpleElasticsearchRouteConstant.AGG_CLASS_NAME_EXTENDED_STATS_BOUNDS,
                SimpleElasticsearchRouteConstant.AGG_PACKAGE_EXTENDED_STATS_ES6 + "." + SimpleElasticsearchRouteConstant.AGG_CLASS_NAME_EXTENDED_STATS_BOUNDS);
    }

    public static Class<?> loadPercentilesClass() {
        return ElasticsearchReflectionHelper.loadFirstPresentClass(
                SimpleElasticsearchRouteConstant.AGG_PACKAGE_METRICS_ES7 + "." + SimpleElasticsearchRouteConstant.AGG_CLASS_NAME_PERCENTILES,
                SimpleElasticsearchRouteConstant.AGG_PACKAGE_PERCENTILES_ES6 + "." + SimpleElasticsearchRouteConstant.AGG_CLASS_NAME_PERCENTILES);
    }

    public static Class<?> loadPercentileRanksClass() {
        return ElasticsearchReflectionHelper.loadFirstPresentClass(
                SimpleElasticsearchRouteConstant.AGG_PACKAGE_METRICS_ES7 + "." + SimpleElasticsearchRouteConstant.AGG_CLASS_NAME_PERCENTILE_RANKS,
                SimpleElasticsearchRouteConstant.AGG_PACKAGE_PERCENTILES_ES6 + "." + SimpleElasticsearchRouteConstant.AGG_CLASS_NAME_PERCENTILE_RANKS);
    }

    public static Class<?> loadPercentileClass() {
        return ElasticsearchReflectionHelper.loadFirstPresentClass(
                SimpleElasticsearchRouteConstant.AGG_PACKAGE_METRICS_ES7 + "." + SimpleElasticsearchRouteConstant.AGG_CLASS_NAME_PERCENTILE,
                SimpleElasticsearchRouteConstant.AGG_PACKAGE_PERCENTILES_ES6 + "." + SimpleElasticsearchRouteConstant.AGG_CLASS_NAME_PERCENTILE);
    }

    public static Class<?> loadNumericSingleValueClass() {
        return ElasticsearchReflectionHelper.loadClass(SimpleElasticsearchRouteConstant.AGG_CLASS_NUMERIC_SINGLE_VALUE);
    }

    public static Class<?> loadPipelineAggregatorBuildersClass() {
        return ElasticsearchReflectionHelper.loadFirstPresentClass(
                SimpleElasticsearchRouteConstant.AGG_CLASS_PIPELINE_BUILDERS_ES7,
                SimpleElasticsearchRouteConstant.AGG_CLASS_PIPELINE_BUILDERS_ES6);
    }

    public static Class<?> loadBucketSortPipelineAggregationBuilderClass() {
        return ElasticsearchReflectionHelper.loadFirstPresentClass(
                SimpleElasticsearchRouteConstant.AGG_CLASS_BUCKET_SORT_ES7,
                SimpleElasticsearchRouteConstant.AGG_CLASS_BUCKET_SORT_ES6);
    }

    public static boolean isStats(Object aggregation) {
        return aggregation != null && loadStatsClass().isInstance(aggregation);
    }

    public static boolean isExtendedStats(Object aggregation) {
        return aggregation != null && loadExtendedStatsClass().isInstance(aggregation);
    }

    public static boolean isPercentiles(Object aggregation) {
        return aggregation != null && loadPercentilesClass().isInstance(aggregation);
    }

    public static boolean isPercentileRanks(Object aggregation) {
        return aggregation != null && loadPercentileRanksClass().isInstance(aggregation);
    }

    public static boolean isNumericSingleValue(Object aggregation) {
        return aggregation != null && loadNumericSingleValueClass().isInstance(aggregation);
    }

    public static Object invokeAggregationMethod(Object target, String methodName) {
        Method method = ElasticsearchReflectionHelper.loadMethod(target.getClass(), methodName);
        return ElasticsearchReflectionHelper.invoke(method, target);
    }

    public static Object invokeExtendedStatsBound(Object target, String boundName) {
        Method method = ElasticsearchReflectionHelper.loadMethod(target.getClass(),
                SimpleElasticsearchRouteConstant.AGG_METHOD_GET_STD_DEVIATION_BOUND, String.class);
        return ElasticsearchReflectionHelper.invoke(method, target, boundName);
    }

    public static Object invokeBucketSort(String name, List<?> sortFields) {
        Class<?> buildersClass = loadPipelineAggregatorBuildersClass();
        Method method = ElasticsearchReflectionHelper.loadMethod(buildersClass,
                SimpleElasticsearchRouteConstant.AGG_METHOD_BUCKET_SORT, String.class, List.class);
        return ElasticsearchReflectionHelper.invoke(method, null, name, sortFields);
    }

    public static Object invokeBucketSelector(String name, Map<String, String> bucketsPath, Script script) {
        Class<?> buildersClass = loadPipelineAggregatorBuildersClass();
        Method method = ElasticsearchReflectionHelper.loadMethod(buildersClass,
                SimpleElasticsearchRouteConstant.AGG_METHOD_BUCKET_SELECTOR, String.class, Map.class, Script.class);
        return ElasticsearchReflectionHelper.invoke(method, null, name, bucketsPath, script);
    }

    public static void applyBucketSortSize(Object bucketSortBuilder, Integer size) {
        if (bucketSortBuilder == null || size == null) {
            return;
        }
        Method method = ElasticsearchReflectionHelper.loadMethod(bucketSortBuilder.getClass(),
                SimpleElasticsearchRouteConstant.AGG_METHOD_SIZE, Integer.class);
        ElasticsearchReflectionHelper.invoke(method, bucketSortBuilder, size);
    }

    public static void applyBucketSortFrom(Object bucketSortBuilder, int from) {
        if (bucketSortBuilder == null) {
            return;
        }
        Method method = ElasticsearchReflectionHelper.loadMethod(bucketSortBuilder.getClass(),
                SimpleElasticsearchRouteConstant.AGG_METHOD_FROM, int.class);
        ElasticsearchReflectionHelper.invoke(method, bucketSortBuilder, from);
    }

    public static Object extractBucketsValue(Map<String, Object> aggregationMap) {
        return aggregationMap == null ? null : aggregationMap.get(SimpleElasticsearchRouteConstant.JSON_FIELD_BUCKETS);
    }

    public static boolean isBucketList(Object bucketsValue) {
        return bucketsValue instanceof List;
    }

    public static boolean isBucketMap(Object bucketsValue) {
        return bucketsValue instanceof Map;
    }

    public static Object extractAfterKey(Map<String, Object> aggregationMap) {
        return aggregationMap == null ? null : aggregationMap.get(SimpleElasticsearchRouteConstant.JSON_FIELD_AFTER_KEY);
    }
}
