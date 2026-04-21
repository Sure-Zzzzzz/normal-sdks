package io.github.surezzzzzz.sdk.elasticsearch.search.agg.executor;

import io.github.surezzzzzz.sdk.elasticsearch.search.agg.builder.AggregationDslBuilder;
import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.AggRequest;
import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.AggResponse;
import io.github.surezzzzzz.sdk.elasticsearch.search.annotation.SimpleElasticsearchSearchComponent;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.*;
import io.github.surezzzzzz.sdk.elasticsearch.search.core.event.EsAggEvent;
import io.github.surezzzzzz.sdk.elasticsearch.search.core.model.AggExecutionContext;
import io.github.surezzzzzz.sdk.elasticsearch.search.exception.AggregationException;
import io.github.surezzzzzz.sdk.elasticsearch.search.executor.AbstractExecutor;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.model.IndexMetadata;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.builder.QueryDslBuilder;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryCondition;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryRequest;
import io.github.surezzzzzz.sdk.elasticsearch.search.support.ElasticsearchCompatibilityHelper;
import io.github.surezzzzzz.sdk.elasticsearch.search.support.TimeRangeHelper;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.MultiBucketsAggregation;
import org.elasticsearch.search.aggregations.bucket.composite.CompositeAggregation;
import org.elasticsearch.search.aggregations.metrics.NumericMetricsAggregation;
import org.elasticsearch.search.aggregations.metrics.Stats;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.*;

/**
 * 聚合执行器实现
 * 继承 {@link AbstractExecutor} 获得通用执行骨架（降级重试、异常处理），
 * 实现 agg 特有的 DSL 构建、响应处理逻辑。
 *
 * <p>ES 6.x 兼容性：{@link ElasticsearchCompatibilityHelper.Es6xAggregationResponseException}
 * 在 {@link #executeOnce} 内部捕获处理，不冒泡到基类。
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleElasticsearchSearchComponent
public class AggExecutor extends AbstractExecutor<AggRequest, AggResponse> {

    @Autowired
    private QueryDslBuilder queryDslBuilder;

    @Autowired
    private AggregationDslBuilder aggDslBuilder;

    // ==================== 抽象方法实现 ====================

    @Override
    protected void validateRequest(AggRequest request) {
        if (request.getIndex() == null || request.getIndex().trim().isEmpty()) {
            throw new AggregationException(ErrorCode.INDEX_ALIAS_REQUIRED, ErrorMessage.INDEX_ALIAS_REQUIRED);
        }
        if (request.getAggs() == null || request.getAggs().isEmpty()) {
            throw new AggregationException(ErrorCode.AGG_DEFINITION_REQUIRED, ErrorMessage.AGG_DEFINITION_REQUIRED);
        }
        // 通配索引默认时间范围注入
        String defaultDateRange = properties.getQueryLimits().getDefaultDateRange();
        if (StringUtils.hasText(defaultDateRange)
                && request.getQuery() == null
                && isWildcardIndex(request.getIndex())) {
            QueryRequest.DateRange range = TimeRangeHelper.buildRecentRange(defaultDateRange);
            request.setQuery(buildDateRangeQuery(range, request.getIndex()));
            log.debug("Applied default date range [{}] for wildcard index [{}]",
                    defaultDateRange, request.getIndex());
        }
    }

    @Override
    protected boolean needsDowngradeRetry(AggRequest request, IndexMetadata metadata) {
        QueryRequest.DateRange dateRange = extractDateRangeFromQuery(request.getQuery(), metadata);
        return properties.getDowngrade().isEnabled()
                && metadata.isDateSplit()
                && dateRange != null;
    }

    @Override
    protected AggResponse executeOnce(AggRequest request, IndexMetadata metadata,
                                      long startTime, DowngradeLevel level) throws IOException {
        QueryRequest.DateRange dateRange = extractDateRangeFromQuery(request.getQuery(), metadata);
        SearchRequest searchRequest = buildSearchRequest(request, metadata, dateRange, level);

        log.debug("Executing aggregation: indices={}, dsl={}",
                String.join(",", searchRequest.indices()),
                searchRequest.source().toString());

        String datasourceKey = routeResolver.resolveDataSource(request.getIndex());
        log.debug("Index [{}] routed to datasource [{}]", request.getIndex(), datasourceKey);
        RestHighLevelClient client = registry.getHighLevelClient(datasourceKey);

        AggResponse response;
        try {
            SearchResponse searchResponse = ElasticsearchCompatibilityHelper.executeSearch(
                    client, datasourceKey, searchRequest, registry);
            response = processResponse(searchResponse);
        } catch (ElasticsearchCompatibilityHelper.Es6xAggregationResponseException e) {
            // ES 6.x 聚合响应在子类内部处理，不冒泡到基类的 catch(IOException)
            log.debug("ES 6.x aggregation response detected, using manual JSON parsing for index [{}]",
                    request.getIndex());
            try {
                response = parseEs6xAggregationResponse(e.getResponseJson());
            } catch (Exception parseException) {
                log.error("Failed to manually parse ES 6.x aggregation response", parseException);
                throw new AggregationException(ErrorCode.AGG_EXECUTION_FAILED,
                        "Failed to parse ES 6.x aggregation response: " + parseException.getMessage(),
                        parseException);
            }
        }

        response.setTook(System.currentTimeMillis() - startTime);
        log.debug("Aggregation executed: index={}, downgradeLevel={}, took={}ms",
                request.getIndex(), level, response.getTook());

        try {
            AggExecutionContext context = AggExecutionContext.builder()
                    .actualIndices(searchRequest.indices())
                    .datasource(datasourceKey)
                    .build();
            eventPublisher.publishEvent(new EsAggEvent(this, request, response, context));
        } catch (Exception e) {
            log.warn("Failed to publish EsAggEvent", e);
        }

        return response;
    }

    @Override
    protected String getIndex(AggRequest request) {
        return request.getIndex();
    }

    @Override
    protected RuntimeException wrapIoException(IOException e) {
        return new AggregationException(ErrorCode.AGG_EXECUTION_FAILED, ErrorMessage.AGG_EXECUTION_FAILED, e);
    }

    // ==================== 钩子方法覆盖 ====================

    @Override
    protected DowngradeLevel estimateDowngradeLevel(AggRequest request, IndexMetadata metadata) {
        if (!properties.getDowngrade().isEnableEstimate()) {
            return DowngradeLevel.LEVEL_0;
        }
        QueryRequest.DateRange dateRange = extractDateRangeFromQuery(request.getQuery(), metadata);
        if (dateRange == null) {
            return DowngradeLevel.LEVEL_0;
        }
        String[] estimatedIndices = indexRouteProcessor.route(metadata, dateRange);
        DowngradeLevel level = indexRouteProcessor.detectDowngradeLevelFromIndices(estimatedIndices);
        if (level != DowngradeLevel.LEVEL_0) {
            log.info("Pre-estimated downgrade to {} for index [{}]", level, request.getIndex());
        }
        return level;
    }

    // ==================== 私有方法 ====================

    private boolean isWildcardIndex(String index) {
        return index != null && (index.contains(SimpleElasticsearchSearchConstant.WILDCARD_STAR)
                || index.contains(SimpleElasticsearchSearchConstant.WILDCARD_QUESTION));
    }

    private QueryCondition buildDateRangeQuery(QueryRequest.DateRange range, String index) {
        try {
            IndexMetadata metadata = mappingManager.getMetadata(index);
            String dateField = metadata.getDateField();
            if (dateField == null) {
                return null;
            }
            return QueryCondition.builder()
                    .field(dateField)
                    .op(QueryOperator.BETWEEN.getOperator())
                    .values(Arrays.asList(range.getFrom(), range.getTo()))
                    .build();
        } catch (Exception e) {
            log.debug("Could not build default date range query for index [{}]: {}", index, e.getMessage());
            return null;
        }
    }

    private SearchRequest buildSearchRequest(AggRequest request, IndexMetadata metadata,
                                             QueryRequest.DateRange dateRange, DowngradeLevel level) {
        String[] indices = indexRouteProcessor.routeWithDowngrade(metadata, dateRange, level);
        SearchRequest searchRequest = new SearchRequest(indices);

        if (properties.getQueryLimits().isIgnoreUnavailableIndices()) {
            searchRequest.indicesOptions(IndicesOptions.lenientExpandOpen());
        }

        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        QueryBuilder queryBuilder = request.getQuery() != null
                ? queryDslBuilder.build(request.getIndex(), request.getQuery())
                : QueryBuilders.matchAllQuery();
        sourceBuilder.query(queryBuilder);

        AggregationBuilder[] aggBuilders = aggDslBuilder.build(
                request.getIndex(), request.getAggs(), request.getAfter());
        for (AggregationBuilder aggBuilder : aggBuilders) {
            sourceBuilder.aggregation(aggBuilder);
        }

        sourceBuilder.size(SimpleElasticsearchSearchConstant.AGG_NO_DOCS_SIZE);
        searchRequest.source(sourceBuilder);
        return searchRequest;
    }

    private QueryRequest.DateRange extractDateRangeFromQuery(QueryCondition query, IndexMetadata metadata) {
        if (query == null || !metadata.isDateSplit()) {
            return null;
        }
        return findDateRange(query, metadata.getDateField());
    }

    private QueryRequest.DateRange findDateRange(QueryCondition condition, String dateField) {
        if (condition == null) {
            return null;
        }
        if (dateField.equals(condition.getField())
                && QueryOperator.BETWEEN.getOperator().equalsIgnoreCase(condition.getOp())
                && condition.getValues() != null && condition.getValues().size() >= 2) {
            return QueryRequest.DateRange.builder()
                    .from(condition.getValues().get(0).toString())
                    .to(condition.getValues().get(1).toString())
                    .build();
        }
        if (condition.getConditions() != null) {
            for (QueryCondition sub : condition.getConditions()) {
                QueryRequest.DateRange range = findDateRange(sub, dateField);
                if (range != null) {
                    return range;
                }
            }
        }
        return null;
    }

    private AggResponse processResponse(SearchResponse searchResponse) {
        Map<String, Object> data = new HashMap<>();
        Map<String, Map<String, Object>> afterKey = new HashMap<>();

        Aggregations aggregations = searchResponse.getAggregations();
        if (aggregations != null) {
            for (Aggregation aggregation : aggregations) {
                if (aggregation instanceof CompositeAggregation) {
                    CompositeAggregation composite = (CompositeAggregation) aggregation;
                    data.put(aggregation.getName(), parseBucketAggregation(composite));
                    Map<String, Object> key = composite.afterKey();
                    if (key != null && !key.isEmpty()) {
                        afterKey.put(aggregation.getName(), key);
                    }
                } else {
                    data.put(aggregation.getName(), parseAggregation(aggregation));
                }
            }
        }

        return AggResponse.builder()
                .aggregations(data)
                .afterKey(afterKey.isEmpty() ? null : afterKey)
                .build();
    }

    private Object parseAggregation(Aggregation aggregation) {
        if (aggregation instanceof NumericMetricsAggregation.SingleValue) {
            return ((NumericMetricsAggregation.SingleValue) aggregation).value();
        }
        if (aggregation instanceof Stats) {
            Stats stats = (Stats) aggregation;
            Map<String, Object> statsMap = new HashMap<>();
            statsMap.put(SimpleElasticsearchSearchConstant.AGG_RESULT_COUNT, stats.getCount());
            statsMap.put(SimpleElasticsearchSearchConstant.STATS_RESULT_MIN, stats.getMin());
            statsMap.put(SimpleElasticsearchSearchConstant.STATS_RESULT_MAX, stats.getMax());
            statsMap.put(SimpleElasticsearchSearchConstant.STATS_RESULT_AVG, stats.getAvg());
            statsMap.put(SimpleElasticsearchSearchConstant.STATS_RESULT_SUM, stats.getSum());
            return statsMap;
        }
        if (aggregation instanceof MultiBucketsAggregation) {
            return parseBucketAggregation((MultiBucketsAggregation) aggregation);
        }
        return aggregation.toString();
    }

    private List<Map<String, Object>> parseBucketAggregation(MultiBucketsAggregation aggregation) {
        List<Map<String, Object>> buckets = new ArrayList<>();
        for (MultiBucketsAggregation.Bucket bucket : aggregation.getBuckets()) {
            Map<String, Object> bucketMap = new HashMap<>();
            bucketMap.put(SimpleElasticsearchSearchConstant.AGG_RESULT_KEY, bucket.getKeyAsString());
            bucketMap.put(SimpleElasticsearchSearchConstant.AGG_RESULT_COUNT, bucket.getDocCount());
            Aggregations subAggs = bucket.getAggregations();
            if (subAggs != null && !subAggs.asList().isEmpty()) {
                for (Aggregation subAgg : subAggs) {
                    bucketMap.put(subAgg.getName(), parseAggregation(subAgg));
                }
            }
            buckets.add(bucketMap);
        }
        return buckets;
    }

    @SuppressWarnings("unchecked")
    private AggResponse parseEs6xAggregationResponse(String responseJson) throws Exception {
        com.fasterxml.jackson.databind.ObjectMapper objectMapper =
                new com.fasterxml.jackson.databind.ObjectMapper();
        Map<String, Object> responseMap = objectMapper.readValue(responseJson,
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
                });
        Map<String, Object> rawAggregations =
                (Map<String, Object>) responseMap.get(SimpleElasticsearchSearchConstant.ES_JSON_AGGREGATIONS);

        if (rawAggregations == null) {
            return AggResponse.builder().aggregations(new HashMap<>()).build();
        }

        Map<String, Object> parsedAggregations = new HashMap<>();
        Map<String, Map<String, Object>> afterKey = new HashMap<>();

        for (Map.Entry<String, Object> entry : rawAggregations.entrySet()) {
            String aggName = entry.getKey();
            Object aggValue = entry.getValue();
            if (aggValue instanceof Map) {
                Map<String, Object> aggMap = (Map<String, Object>) aggValue;
                if (aggMap.containsKey(SimpleElasticsearchSearchConstant.ES_JSON_AFTER_KEY)) {
                    Map<String, Object> key =
                            (Map<String, Object>) aggMap.get(SimpleElasticsearchSearchConstant.ES_JSON_AFTER_KEY);
                    if (key != null && !key.isEmpty()) {
                        afterKey.put(aggName, key);
                    }
                    parsedAggregations.put(aggName, parseCompositeAggregationValue(aggMap));
                    continue;
                }
            }
            parsedAggregations.put(aggName, parseAggregationValue(aggValue));
        }

        AggResponse.AggResponseBuilder builder = AggResponse.builder()
                .aggregations(parsedAggregations)
                .afterKey(afterKey.isEmpty() ? null : afterKey);
        if (properties.getApi().isIncludeRawResponse()) {
            builder.rawResponse(rawAggregations);
        }
        return builder.build();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseCompositeAggregationValue(Map<String, Object> aggMap) {
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
            Object keyObj = bucket.get(SimpleElasticsearchSearchConstant.ES_JSON_KEY);
            if (keyObj instanceof Map) {
                Map<String, Object> keyMap = (Map<String, Object>) keyObj;
                parsedBucket.put(SimpleElasticsearchSearchConstant.AGG_RESULT_KEY,
                        keyMap.size() == 1 ? keyMap.values().iterator().next() : keyMap);
            } else {
                parsedBucket.put(SimpleElasticsearchSearchConstant.AGG_RESULT_KEY, keyObj);
            }
            parsedBucket.put(SimpleElasticsearchSearchConstant.AGG_RESULT_COUNT,
                    bucket.get(SimpleElasticsearchSearchConstant.ES_JSON_DOC_COUNT));
            for (Map.Entry<String, Object> e : bucket.entrySet()) {
                String k = e.getKey();
                if (!k.equals(SimpleElasticsearchSearchConstant.ES_JSON_KEY)
                        && !k.equals(SimpleElasticsearchSearchConstant.ES_JSON_DOC_COUNT)) {
                    parsedBucket.put(k, parseAggregationValue(e.getValue()));
                }
            }
            buckets.add(parsedBucket);
        }
        return buckets;
    }

    @SuppressWarnings("unchecked")
    private Object parseAggregationValue(Object aggValue) {
        if (!(aggValue instanceof Map)) {
            return aggValue;
        }
        Map<String, Object> aggMap = (Map<String, Object>) aggValue;
        if (aggMap.containsKey(SimpleElasticsearchSearchConstant.ES_JSON_VALUE) && aggMap.size() == 1) {
            return aggMap.get(SimpleElasticsearchSearchConstant.ES_JSON_VALUE);
        }
        if (aggMap.containsKey(SimpleElasticsearchSearchConstant.ES_JSON_COUNT)
                && aggMap.containsKey(SimpleElasticsearchSearchConstant.ES_JSON_MIN)
                && aggMap.containsKey(SimpleElasticsearchSearchConstant.ES_JSON_MAX)) {
            Map<String, Object> statsMap = new HashMap<>();
            statsMap.put(SimpleElasticsearchSearchConstant.AGG_RESULT_COUNT,
                    aggMap.get(SimpleElasticsearchSearchConstant.ES_JSON_COUNT));
            statsMap.put(SimpleElasticsearchSearchConstant.STATS_RESULT_MIN,
                    aggMap.get(SimpleElasticsearchSearchConstant.ES_JSON_MIN));
            statsMap.put(SimpleElasticsearchSearchConstant.STATS_RESULT_MAX,
                    aggMap.get(SimpleElasticsearchSearchConstant.ES_JSON_MAX));
            statsMap.put(SimpleElasticsearchSearchConstant.STATS_RESULT_AVG,
                    aggMap.get(SimpleElasticsearchSearchConstant.ES_JSON_AVG));
            statsMap.put(SimpleElasticsearchSearchConstant.STATS_RESULT_SUM,
                    aggMap.get(SimpleElasticsearchSearchConstant.ES_JSON_SUM));
            return statsMap;
        }
        if (aggMap.containsKey(SimpleElasticsearchSearchConstant.ES_JSON_BUCKETS)) {
            Object bucketsValue = aggMap.get(SimpleElasticsearchSearchConstant.ES_JSON_BUCKETS);
            if (bucketsValue instanceof List) {
                List<Map<String, Object>> buckets = new ArrayList<>();
                for (Object bucketObj : (List<?>) bucketsValue) {
                    if (bucketObj instanceof Map) {
                        Map<String, Object> bucket = (Map<String, Object>) bucketObj;
                        Map<String, Object> parsedBucket = new HashMap<>();
                        parsedBucket.put(SimpleElasticsearchSearchConstant.AGG_RESULT_KEY,
                                bucket.get(SimpleElasticsearchSearchConstant.ES_JSON_KEY_AS_STRING));
                        if (parsedBucket.get(SimpleElasticsearchSearchConstant.AGG_RESULT_KEY) == null) {
                            parsedBucket.put(SimpleElasticsearchSearchConstant.AGG_RESULT_KEY,
                                    bucket.get(SimpleElasticsearchSearchConstant.ES_JSON_KEY));
                        }
                        parsedBucket.put(SimpleElasticsearchSearchConstant.AGG_RESULT_COUNT,
                                bucket.get(SimpleElasticsearchSearchConstant.ES_JSON_DOC_COUNT));
                        for (Map.Entry<String, Object> entry : bucket.entrySet()) {
                            String key = entry.getKey();
                            if (!key.equals(SimpleElasticsearchSearchConstant.ES_JSON_KEY)
                                    && !key.equals(SimpleElasticsearchSearchConstant.ES_JSON_KEY_AS_STRING)
                                    && !key.equals(SimpleElasticsearchSearchConstant.ES_JSON_DOC_COUNT)) {
                                parsedBucket.put(key, parseAggregationValue(entry.getValue()));
                            }
                        }
                        buckets.add(parsedBucket);
                    }
                }
                return buckets;
            }
        }
        return aggValue;
    }
}
