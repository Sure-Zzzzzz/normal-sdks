package io.github.surezzzzzz.sdk.elasticsearch.search.agg.executor;

import io.github.surezzzzzz.sdk.elasticsearch.route.model.ClusterInfo;
import io.github.surezzzzzz.sdk.elasticsearch.route.model.LowLevelSearchResult;
import io.github.surezzzzzz.sdk.elasticsearch.route.support.*;
import io.github.surezzzzzz.sdk.elasticsearch.search.agg.builder.AggregationDslBuilder;
import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.AggRequest;
import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.AggResponse;
import io.github.surezzzzzz.sdk.elasticsearch.search.agg.validator.AggRequestValidatorChain;
import io.github.surezzzzzz.sdk.elasticsearch.search.annotation.SimpleElasticsearchSearchComponent;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.*;
import io.github.surezzzzzz.sdk.elasticsearch.search.core.event.EsAggErrorEvent;
import io.github.surezzzzzz.sdk.elasticsearch.search.core.event.EsAggEvent;
import io.github.surezzzzzz.sdk.elasticsearch.search.core.model.AggExecutionContext;
import io.github.surezzzzzz.sdk.elasticsearch.search.exception.AggregationException;
import io.github.surezzzzzz.sdk.elasticsearch.search.exception.DowngradeFailedException;
import io.github.surezzzzzz.sdk.elasticsearch.search.executor.AbstractExecutor;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.model.IndexMetadata;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.model.ResolvedIndexConfig;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.builder.QueryDslBuilder;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryCondition;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryRequest;
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
import org.elasticsearch.search.aggregations.bucket.composite.CompositeAggregation;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * 聚合执行器实现
 *
 * <p>继承 {@link AbstractExecutor} 获得通用执行骨架（降级重试、异常处理），
 * 实现 agg 特有的 DSL 构建和响应编排逻辑。
 * 聚合响应解析委托给 {@link AggregationResponseParser}，职责分离。
 *
 * <p>ES 6.x 聚合响应由 route 低级搜索 helper 返回原始 JSON，
 * 在本类转换为 search 模块的 {@link AggResponse}。
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

    @Autowired
    private AggRequestValidatorChain validatorChain;

    @Autowired
    private AggregationResponseParser responseParser;

    // ==================== 抽象方法实现 ====================

    @Override
    protected void validateRequest(AggRequest request) {
        // 纯校验委托给 chain（index 非空、aggs 非空）
        validatorChain.validate(request, properties);
        // default-date-range 注入依赖 mappingManager，保留在此处
        String defaultDateRange = properties.getQueryLimits().getDefaultDateRange();
        ResolvedIndexConfig resolvedIndexConfig = mappingManager.findResolvedIndexConfig(request.getIndex());
        if (StringUtils.hasText(defaultDateRange)
                && request.getQuery() == null
                && isWildcardIndex(resolvedIndexConfig, request.getIndex())) {
            QueryRequest.DateRange range = TimeRangeHelper.buildRecentRange(defaultDateRange);
            request.setQuery(buildDateRangeQuery(range, resolvedIndexConfig, request.getIndex()));
            log.debug("Applied default date range [{}] for wildcard index [{}]",
                    defaultDateRange, request.getIndex());
        }
    }

    @Override
    protected boolean needsDowngradeRetry(AggRequest request, ResolvedIndexConfig resolvedIndexConfig,
                                          IndexMetadata metadata) {
        QueryRequest.DateRange dateRange = resolveDateRange(request, metadata);
        return properties.getDowngrade().isEnabled()
                && !resolvedIndexConfig.isWildcardMatched()
                && metadata.isDateSplit()
                && dateRange != null;
    }

    @Override
    protected AggResponse executeOnce(AggRequest request, ResolvedIndexConfig resolvedIndexConfig,
                                      IndexMetadata metadata, long startTime,
                                      DowngradeLevel level) throws IOException {
        QueryRequest.DateRange dateRange = resolveDateRange(request, metadata);
        SearchRequest searchRequest = buildSearchRequest(request, resolvedIndexConfig, metadata, dateRange, level);

        log.debug("Executing aggregation: indices={}, dsl={}",
                String.join(",", searchRequest.indices()),
                searchRequest.source().toString());

        String datasourceKey = routeResolver.resolveDataSource(request.getIndex());
        log.debug("Index [{}] routed to datasource [{}]", request.getIndex(), datasourceKey);
        RestHighLevelClient client = registry.getHighLevelClient(datasourceKey);

        AggResponse response = executeAggregationSearch(client, datasourceKey, searchRequest, request.getIndex());

        response.setTook(System.currentTimeMillis() - startTime);
        log.debug("Aggregation executed: index={}, downgradeLevel={}, took={}ms",
                request.getIndex(), level, response.getTook());

        try {
            AggExecutionContext context = AggExecutionContext.builder()
                    .actualIndices(searchRequest.indices())
                    .datasource(datasourceKey)
                    .downgradeLevel(level.getValue())
                    .sourceType(request.getSourceType())
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

    @Override
    protected void onExecutionError(AggRequest request, Throwable error) {
        try {
            String datasource = null;
            try {
                datasource = routeResolver.resolveDataSource(request.getIndex());
            } catch (Exception ignored) {
            }
            int downgradeLevel = DowngradeLevel.LEVEL_0.getValue();
            if (error instanceof DowngradeFailedException) {
                downgradeLevel = ((DowngradeFailedException) error).getFinalLevel().getValue();
            }
            AggExecutionContext context = AggExecutionContext.builder()
                    .datasource(datasource)
                    .downgradeLevel(downgradeLevel)
                    .sourceType(request.getSourceType())
                    .build();
            eventPublisher.publishEvent(new EsAggErrorEvent(this, request, error, datasource,
                    downgradeLevel, context));
        } catch (Exception e) {
            log.warn("Failed to publish EsAggErrorEvent", e);
        }
    }

    // ==================== 钩子方法覆盖 ====================

    @Override
    protected DowngradeLevel estimateDowngradeLevel(AggRequest request, ResolvedIndexConfig resolvedIndexConfig,
                                                    IndexMetadata metadata) {
        if (!properties.getDowngrade().isEnableEstimate()) {
            return DowngradeLevel.LEVEL_0;
        }
        QueryRequest.DateRange dateRange = resolveDateRange(request, metadata);
        if (dateRange == null) {
            return DowngradeLevel.LEVEL_0;
        }
        String[] estimatedIndices = indexRouteProcessor.route(resolvedIndexConfig, metadata, dateRange);
        DowngradeLevel level = indexRouteProcessor.detectDowngradeLevelFromIndices(estimatedIndices);
        if (level != DowngradeLevel.LEVEL_0) {
            log.info("Pre-estimated downgrade to {} for index [{}]", level, request.getIndex());
        }
        return level;
    }

    // ==================== 私有方法 ====================

    private boolean isWildcardIndex(ResolvedIndexConfig resolvedIndexConfig, String index) {
        return index != null && (index.contains(SimpleElasticsearchSearchConstant.WILDCARD_STAR)
                || index.contains(SimpleElasticsearchSearchConstant.WILDCARD_QUESTION));
    }

    private QueryCondition buildDateRangeQuery(QueryRequest.DateRange range,
                                               ResolvedIndexConfig resolvedIndexConfig,
                                               String index) {
        try {
            IndexMetadata metadata = resolvedIndexConfig == null
                    ? mappingManager.getMetadata(index)
                    : mappingManager.getMetadata(resolvedIndexConfig);
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

    private SearchRequest buildSearchRequest(AggRequest request, ResolvedIndexConfig resolvedIndexConfig,
                                             IndexMetadata metadata, QueryRequest.DateRange dateRange,
                                             DowngradeLevel level) {
        String[] indices = indexRouteProcessor.routeWithDowngrade(resolvedIndexConfig, metadata, dateRange, level);
        SearchRequest searchRequest = new SearchRequest(indices);

        if (properties.getQueryLimits().isIgnoreUnavailableIndices()) {
            searchRequest.indicesOptions(IndicesOptions.lenientExpandOpen());
        }

        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        QueryBuilder queryBuilder = request.getQuery() != null
                ? queryDslBuilder.build(metadata, request.getQuery())
                : QueryBuilders.matchAllQuery();
        sourceBuilder.query(queryBuilder);

        AggregationBuilder[] aggBuilders = aggDslBuilder.build(
                metadata, request.getAggs(), request.getAfter());
        for (AggregationBuilder aggBuilder : aggBuilders) {
            sourceBuilder.aggregation(aggBuilder);
        }

        sourceBuilder.size(SimpleElasticsearchSearchConstant.AGG_NO_DOCS_SIZE);
        searchRequest.source(sourceBuilder);
        return searchRequest;
    }

    private QueryRequest.DateRange resolveDateRange(AggRequest request, IndexMetadata metadata) {
        // 显式 dateRange 优先，fallback 到从 query 条件推断
        if (request.getDateRange() != null) {
            return request.getDateRange();
        }
        return extractDateRangeFromQuery(request.getQuery(), metadata);
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

    private AggResponse executeAggregationSearch(RestHighLevelClient client, String datasourceKey,
                                                 SearchRequest searchRequest, String index) throws IOException {
        ClusterInfo clusterInfo = registry.getClusterInfo(datasourceKey);
        if (ElasticsearchVersionHelper.isEs6(clusterInfo)) {
            return executeLowLevelAggregation(client, searchRequest, index);
        }
        try {
            return processResponse(client.search(searchRequest, org.elasticsearch.client.RequestOptions.DEFAULT));
        } catch (org.elasticsearch.ElasticsearchStatusException e) {
            if (ElasticsearchVersionHelper.isUnknown(clusterInfo)
                    && ElasticsearchResponseHelper.shouldFallbackToLowLevel(e)) {
                log.warn("高级 API 遇到版本兼容问题，降级为低级 API 执行聚合，数据源：{}", datasourceKey);
                return executeLowLevelAggregation(client, searchRequest, index);
            }
            throw e;
        }
    }

    private AggResponse executeLowLevelAggregation(RestHighLevelClient client,
                                                   SearchRequest searchRequest,
                                                   String index) throws IOException {
        LowLevelSearchResult result = executeEs6AggregationSearch(client, searchRequest);
        if (result.isRawAggregationResponse()) {
            log.debug("ES 6.x aggregation response detected, using manual JSON parsing for index [{}]", index);
            try {
                return parseEs6xAggregationResponse(result.getRawResponseBody());
            } catch (Exception parseException) {
                log.error("Failed to manually parse ES 6.x aggregation response", parseException);
                throw new AggregationException(ErrorCode.AGG_EXECUTION_FAILED,
                        "Failed to parse ES 6.x aggregation response: " + parseException.getMessage(),
                        parseException);
            }
        }
        return processResponse(result.getSearchResponse());
    }

    private LowLevelSearchResult executeEs6AggregationSearch(RestHighLevelClient client,
                                                             SearchRequest searchRequest) throws IOException {
        String jsonBody = searchRequest != null && searchRequest.source() != null
                ? searchRequest.source().toString()
                : null;
        jsonBody = ElasticsearchDslCompatibilityHelper.removeEs7CompositeUnsupportedFieldsForEs6(jsonBody);
        String[] indices = searchRequest == null ? null : searchRequest.indices();
        org.elasticsearch.client.Request request = ElasticsearchLowLevelRequestHelper.newSearchRequest(indices, jsonBody, null);
        if (searchRequest != null) {
            ElasticsearchRequestOptionHelper.applyIndicesOptions(request, searchRequest.indicesOptions());
        }
        org.elasticsearch.client.Response response = ElasticsearchLowLevelRequestHelper.execute(client.getLowLevelClient(), request);
        byte[] responseBytes = ElasticsearchLowLevelRequestHelper.readResponseBytes(response);
        String responseBody = new String(responseBytes, java.nio.charset.StandardCharsets.UTF_8);
        boolean containsAggregations = ElasticsearchLowLevelRequestHelper.containsAggregations(responseBody);
        if (containsAggregations) {
            return LowLevelSearchResult.builder()
                    .rawResponseBody(responseBody)
                    .containsAggregations(true)
                    .rawAggregationResponse(true)
                    .build();
        }
        return LowLevelSearchResult.builder()
                .searchResponse(XContentCompatibilityHelper.parseSearchResponse(responseBytes))
                .rawResponseBody(responseBody)
                .containsAggregations(false)
                .rawAggregationResponse(false)
                .build();
    }

    private AggResponse processResponse(SearchResponse searchResponse) {
        Map<String, Object> data = new HashMap<>();
        Map<String, Map<String, Object>> afterKey = new HashMap<>();

        Aggregations aggregations = searchResponse.getAggregations();
        if (aggregations != null) {
            for (Aggregation aggregation : aggregations) {
                if (aggregation instanceof CompositeAggregation) {
                    CompositeAggregation composite = (CompositeAggregation) aggregation;
                    data.put(aggregation.getName(), responseParser.parseComposite(composite));
                    Map<String, Object> key = composite.afterKey();
                    if (key != null && !key.isEmpty()) {
                        afterKey.put(aggregation.getName(), key);
                    }
                } else {
                    data.put(aggregation.getName(), responseParser.parse(aggregation));
                }
            }
        }

        return AggResponse.builder()
                .aggregations(data)
                .afterKey(afterKey.isEmpty() ? null : afterKey)
                .build();
    }

    @SuppressWarnings("unchecked")
    private AggResponse parseEs6xAggregationResponse(String responseJson) throws Exception {
        Map<String, Object> rawAggregations = responseParser.parseEs6xResponse(responseJson);
        if (rawAggregations.isEmpty()) {
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
                    parsedAggregations.put(aggName, parseEs6xComposite(aggMap));
                    continue;
                }
            }
            parsedAggregations.put(aggName, responseParser.parseEs6xValue(aggValue));
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
    private java.util.List<Map<String, Object>> parseEs6xComposite(Map<String, Object> aggMap) {
        java.util.List<Map<String, Object>> buckets = new java.util.ArrayList<>();
        Object bucketsValue = aggMap.get(SimpleElasticsearchSearchConstant.ES_JSON_BUCKETS);
        if (!(bucketsValue instanceof java.util.List)) {
            return buckets;
        }
        for (Object bucketObj : (java.util.List<?>) bucketsValue) {
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
                    parsedBucket.put(k, responseParser.parseEs6xValue(e.getValue()));
                }
            }
            buckets.add(parsedBucket);
        }
        return buckets;
    }
}
