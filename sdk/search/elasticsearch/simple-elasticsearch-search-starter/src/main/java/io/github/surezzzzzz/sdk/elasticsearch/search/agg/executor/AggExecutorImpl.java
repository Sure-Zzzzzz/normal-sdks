package io.github.surezzzzzz.sdk.elasticsearch.search.agg.executor;

import io.github.surezzzzzz.sdk.elasticsearch.route.registry.SimpleElasticsearchRouteRegistry;
import io.github.surezzzzzz.sdk.elasticsearch.route.resolver.RouteResolver;
import io.github.surezzzzzz.sdk.elasticsearch.search.agg.builder.AggregationDslBuilder;
import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.AggRequest;
import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.AggResponse;
import io.github.surezzzzzz.sdk.elasticsearch.search.annotation.SimpleElasticsearchSearchComponent;
import io.github.surezzzzzz.sdk.elasticsearch.search.configuration.SimpleElasticsearchSearchProperties;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.*;
import io.github.surezzzzzz.sdk.elasticsearch.search.core.event.EsAggEvent;
import io.github.surezzzzzz.sdk.elasticsearch.search.core.model.AggExecutionContext;
import io.github.surezzzzzz.sdk.elasticsearch.search.exception.AggregationException;
import io.github.surezzzzzz.sdk.elasticsearch.search.exception.DowngradeFailedException;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.MappingManager;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.model.IndexMetadata;
import io.github.surezzzzzz.sdk.elasticsearch.search.processor.IndexRouteProcessor;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.builder.QueryDslBuilder;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryCondition;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryRequest;
import io.github.surezzzzzz.sdk.elasticsearch.search.support.ElasticsearchCompatibilityHelper;
import io.github.surezzzzzz.sdk.elasticsearch.search.support.TimeRangeHelper;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.ElasticsearchException;
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
import org.springframework.context.ApplicationEventPublisher;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 聚合执行器实现
 *
 * <p><b>版本兼容性说明：</b>
 * <ul>
 *   <li>使用 simple-elasticsearch-route-starter 提供的 SimpleElasticsearchRouteRegistry</li>
 *   <li>根据索引名称通过 RouteResolver 路由到对应数据源</li>
 *   <li>获取该数据源版本自适应的 RestHighLevelClient，避免版本兼容性问题</li>
 *   <li>对于 ES 6.x，自动使用低级 API 绕过参数兼容性问题（如 ignore_throttled）</li>
 * </ul>
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleElasticsearchSearchComponent
public class AggExecutorImpl implements AggExecutor {

    @Autowired
    private SimpleElasticsearchSearchProperties properties;

    @Autowired
    private MappingManager mappingManager;

    @Autowired
    private QueryDslBuilder queryDslBuilder;

    @Autowired
    private AggregationDslBuilder aggDslBuilder;

    @Autowired
    private IndexRouteProcessor indexRouteProcessor;  // ✅ 新增：索引路由处理器

    @Autowired
    private SimpleElasticsearchRouteRegistry registry;

    @Autowired
    private RouteResolver routeResolver;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Override
    public AggResponse execute(AggRequest request) {
        long startTime = System.currentTimeMillis();

        try {
            // 1. 参数验证
            validateRequest(request);

            // 2. 获取索引元数据
            IndexMetadata metadata = mappingManager.getMetadata(request.getIndex());

            // 3. 提取日期范围
            QueryRequest.DateRange dateRange = extractDateRangeFromQuery(request.getQuery(), metadata);

            // 4. 执行查询（带降级重试）
            if (properties.getDowngrade().isEnabled() && metadata.isDateSplit() && dateRange != null) {
                return executeWithDowngradeRetry(request, metadata, dateRange, startTime);
            } else {
                return executeOnce(request, metadata, dateRange, startTime, DowngradeLevel.LEVEL_0);
            }

        } catch (ElasticsearchCompatibilityHelper.Es6xAggregationResponseException e) {
            // ES 6.x 聚合响应解析异常，手动解析 JSON
            log.debug("ES 6.x aggregation response detected, using manual JSON parsing for index [{}]", request.getIndex());
            try {
                AggResponse response = parseEs6xAggregationResponse(e.getResponseJson());
                long took = System.currentTimeMillis() - startTime;
                response.setTook(took);
                log.debug("ES 6.x aggregation executed with manual parsing: index={}, took={}ms",
                        request.getIndex(), took);
                return response;
            } catch (Exception parseException) {
                log.error("Failed to manually parse ES 6.x aggregation response", parseException);
                throw new AggregationException(ErrorCode.AGG_EXECUTION_FAILED,
                        "Failed to parse ES 6.x aggregation response: " + parseException.getMessage(),
                        parseException);
            }
        } catch (IOException e) {
            log.error("Aggregation execution failed: index={}", request.getIndex(), e);
            throw new AggregationException(ErrorCode.AGG_EXECUTION_FAILED, ErrorMessage.AGG_EXECUTION_FAILED, e);
        }
    }

    /**
     * 执行聚合查询（带降级重试）
     */
    private AggResponse executeWithDowngradeRetry(AggRequest request, IndexMetadata metadata,
                                                  QueryRequest.DateRange dateRange, long startTime) throws IOException {
        // ✅ 先进行降级预估，如果需要降级，直接从预估的级别开始
        DowngradeLevel currentLevel = DowngradeLevel.LEVEL_0;

        // 如果启用了预估，尝试预估降级级别
        if (properties.getDowngrade().isEnableEstimate()) {
            String[] estimatedIndices = indexRouteProcessor.route(metadata, dateRange);
            // 从索引数组中检测降级级别
            currentLevel = indexRouteProcessor.detectDowngradeLevelFromIndices(estimatedIndices);
            if (currentLevel != DowngradeLevel.LEVEL_0) {
                log.info("Pre-estimated downgrade to {} for index [{}]", currentLevel, request.getIndex());
            }
        }

        while (true) {
            try {
                return executeOnce(request, metadata, dateRange, startTime, currentLevel);

            } catch (ElasticsearchException | IOException e) {
                // 检查是否是 too_long_frame_exception
                if (!isTooLongFrameException(e)) {
                    throw e;
                }

                // 检查是否可以继续降级
                if (!currentLevel.hasNext() || currentLevel.getValue() >= properties.getDowngrade().getMaxLevel()) {
                    log.error("Aggregation failed at max downgrade level {}: index={}", currentLevel, request.getIndex(), e);
                    throw new DowngradeFailedException(
                            ErrorCode.DOWNGRADE_FAILED,
                            String.format(ErrorMessage.DOWNGRADE_FAILED),
                            currentLevel,
                            e
                    );
                }

                // 降级到下一级别
                DowngradeLevel nextLevel = currentLevel.next();
                log.warn("Aggregation failed with too_long_frame_exception at level {}, downgrading to {}: index={}",
                        currentLevel, nextLevel, request.getIndex());
                currentLevel = nextLevel;
            }
        }
    }

    /**
     * 执行一次聚合查询
     */
    private AggResponse executeOnce(AggRequest request, IndexMetadata metadata,
                                    QueryRequest.DateRange dateRange, long startTime, DowngradeLevel downgradeLevel) throws IOException {
        // 1. 构建 ES 查询
        SearchRequest searchRequest = buildSearchRequest(request, metadata, dateRange, downgradeLevel);

        // 2. 执行查询
        log.debug("Executing aggregation: indices={}, dsl={}",
                String.join(",", searchRequest.indices()),
                searchRequest.source().toString());

        // 根据索引名称路由到对应数据源，获取版本自适应的 RestHighLevelClient
        String datasourceKey = routeResolver.resolveDataSource(request.getIndex());
        log.debug("Index [{}] routed to datasource [{}]", request.getIndex(), datasourceKey);
        RestHighLevelClient client = registry.getHighLevelClient(datasourceKey);

        // 检测 ES 版本，决定使用高级 API 还是低级 API（使用工具类）
        SearchResponse searchResponse = ElasticsearchCompatibilityHelper.executeSearch(
                client, datasourceKey, searchRequest, registry);

        // 3. 处理结果
        AggResponse response = processResponse(searchResponse);

        // 4. 计算耗时
        long took = System.currentTimeMillis() - startTime;
        response.setTook(took);

        log.debug("Aggregation executed: index={}, downgradeLevel={}, took={}ms",
                request.getIndex(), downgradeLevel, took);

        // 5. 发布聚合事件
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

    /**
     * 判断异常是否为 too_long_frame_exception
     */
    private boolean isTooLongFrameException(Throwable e) {
        if (e == null) {
            return false;
        }

        String message = e.getMessage();
        if (message != null && message.contains("too_long_frame_exception")) {
            return true;
        }

        // 递归检查 cause
        return isTooLongFrameException(e.getCause());
    }

    /**
     * 手动解析 ES 6.x 聚合响应 JSON
     * 保持与 processResponse() 相同的数据结构
     */
    @SuppressWarnings("unchecked")
    private AggResponse parseEs6xAggregationResponse(String responseJson) throws Exception {
        com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

        Map<String, Object> responseMap = objectMapper.readValue(responseJson,
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
                });

        Map<String, Object> rawAggregations = (Map<String, Object>) responseMap.get(SimpleElasticsearchSearchConstant.ES_JSON_AGGREGATIONS);

        if (rawAggregations == null) {
            return AggResponse.builder()
                    .aggregations(new HashMap<>())
                    .build();
        }

        Map<String, Object> parsedAggregations = new HashMap<>();
        Map<String, Map<String, Object>> afterKey = new HashMap<>();

        for (Map.Entry<String, Object> entry : rawAggregations.entrySet()) {
            String aggName = entry.getKey();
            Object aggValue = entry.getValue();

            // 检测是否是 composite 聚合（含 after_key 字段）
            if (aggValue instanceof Map) {
                Map<String, Object> aggMap = (Map<String, Object>) aggValue;
                if (aggMap.containsKey(SimpleElasticsearchSearchConstant.ES_JSON_AFTER_KEY)) {
                    Map<String, Object> key = (Map<String, Object>) aggMap.get(SimpleElasticsearchSearchConstant.ES_JSON_AFTER_KEY);
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
            log.debug("Included raw ES 6.x aggregations in AggResponse (config enabled)");
        }

        return builder.build();
    }

    /**
     * 解析 composite 聚合的 buckets（ES 6.x 路径）
     * composite bucket 的 key 是 Map，单 source 时取第一个值作为展示 key
     */
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

            // composite bucket 的 key 是 Map，单 source 时直接取值
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

            // 嵌套 metrics
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

    /**
     * 解析聚合值（支持多种聚合类型）
     */
    @SuppressWarnings("unchecked")
    private Object parseAggregationValue(Object aggValue) {
        if (!(aggValue instanceof Map)) {
            return aggValue;
        }

        Map<String, Object> aggMap = (Map<String, Object>) aggValue;

        // Metrics 聚合：{ "value": 123.0 }
        if (aggMap.containsKey(SimpleElasticsearchSearchConstant.ES_JSON_VALUE) && aggMap.size() == 1) {
            return aggMap.get(SimpleElasticsearchSearchConstant.ES_JSON_VALUE);
        }

        // Stats 聚合：{ "count": 5, "min": 10, "max": 100, "avg": 50, "sum": 250 }
        if (aggMap.containsKey(SimpleElasticsearchSearchConstant.ES_JSON_COUNT)
                && aggMap.containsKey(SimpleElasticsearchSearchConstant.ES_JSON_MIN)
                && aggMap.containsKey(SimpleElasticsearchSearchConstant.ES_JSON_MAX)) {
            Map<String, Object> statsMap = new HashMap<>();
            statsMap.put(SimpleElasticsearchSearchConstant.AGG_RESULT_COUNT, aggMap.get(SimpleElasticsearchSearchConstant.ES_JSON_COUNT));
            statsMap.put(SimpleElasticsearchSearchConstant.STATS_RESULT_MIN, aggMap.get(SimpleElasticsearchSearchConstant.ES_JSON_MIN));
            statsMap.put(SimpleElasticsearchSearchConstant.STATS_RESULT_MAX, aggMap.get(SimpleElasticsearchSearchConstant.ES_JSON_MAX));
            statsMap.put(SimpleElasticsearchSearchConstant.STATS_RESULT_AVG, aggMap.get(SimpleElasticsearchSearchConstant.ES_JSON_AVG));
            statsMap.put(SimpleElasticsearchSearchConstant.STATS_RESULT_SUM, aggMap.get(SimpleElasticsearchSearchConstant.ES_JSON_SUM));
            return statsMap;
        }

        // Bucket 聚合：{ "buckets": [...] }
        if (aggMap.containsKey(SimpleElasticsearchSearchConstant.ES_JSON_BUCKETS)) {
            Object bucketsValue = aggMap.get(SimpleElasticsearchSearchConstant.ES_JSON_BUCKETS);
            if (bucketsValue instanceof List) {
                List<Map<String, Object>> buckets = new ArrayList<>();
                for (Object bucketObj : (List<?>) bucketsValue) {
                    if (bucketObj instanceof Map) {
                        Map<String, Object> bucket = (Map<String, Object>) bucketObj;
                        Map<String, Object> parsedBucket = new HashMap<>();

                        // Bucket key 和 doc_count
                        parsedBucket.put(SimpleElasticsearchSearchConstant.AGG_RESULT_KEY, bucket.get(SimpleElasticsearchSearchConstant.ES_JSON_KEY_AS_STRING));
                        if (parsedBucket.get(SimpleElasticsearchSearchConstant.AGG_RESULT_KEY) == null) {
                            parsedBucket.put(SimpleElasticsearchSearchConstant.AGG_RESULT_KEY, bucket.get(SimpleElasticsearchSearchConstant.ES_JSON_KEY));
                        }
                        parsedBucket.put(SimpleElasticsearchSearchConstant.AGG_RESULT_COUNT, bucket.get(SimpleElasticsearchSearchConstant.ES_JSON_DOC_COUNT));

                        // 递归处理嵌套聚合
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

        // 其他未知类型，返回原始值
        return aggValue;
    }

    /**
     * 验证请求参数
     */
    private void validateRequest(AggRequest request) {
        if (request.getIndex() == null || request.getIndex().trim().isEmpty()) {
            throw new AggregationException(ErrorCode.INDEX_ALIAS_REQUIRED, ErrorMessage.INDEX_ALIAS_REQUIRED);
        }

        if (request.getAggs() == null || request.getAggs().isEmpty()) {
            throw new AggregationException(ErrorCode.AGG_DEFINITION_REQUIRED, ErrorMessage.AGG_DEFINITION_REQUIRED);
        }

        // 通配索引默认时间范围补充
        String defaultDateRange = properties.getQueryLimits().getDefaultDateRange();
        if (org.springframework.util.StringUtils.hasText(defaultDateRange)
                && request.getQuery() == null
                && isWildcardIndex(request.getIndex())) {
            QueryRequest.DateRange range = TimeRangeHelper.buildRecentRange(defaultDateRange);
            // 将默认时间范围注入 query，供 extractDateRangeFromQuery 使用
            request.setQuery(buildDateRangeQuery(range, request.getIndex()));
            log.debug("Applied default date range [{}] for wildcard index [{}]", defaultDateRange, request.getIndex());
        }
    }

    /**
     * 判断索引名是否包含通配符
     */
    private boolean isWildcardIndex(String index) {
        return index != null && (index.contains(SimpleElasticsearchSearchConstant.WILDCARD_STAR)
                || index.contains(SimpleElasticsearchSearchConstant.WILDCARD_QUESTION));
    }

    /**
     * 根据 DateRange 构建 QueryCondition（用于聚合的默认时间范围注入）
     */
    private QueryCondition buildDateRangeQuery(QueryRequest.DateRange range, String index) {
        // 获取索引元数据，找到日期字段
        try {
            io.github.surezzzzzz.sdk.elasticsearch.search.metadata.model.IndexMetadata metadata =
                    mappingManager.getMetadata(index);
            String dateField = metadata.getDateField();
            if (dateField == null) {
                return null;
            }
            return QueryCondition.builder()
                    .field(dateField)
                    .op(QueryOperator.BETWEEN.getOperator())
                    .values(java.util.Arrays.asList(range.getFrom(), range.getTo()))
                    .build();
        } catch (Exception e) {
            log.debug("Could not build default date range query for index [{}]: {}", index, e.getMessage());
            return null;
        }
    }

    /**
     * 构建 ES 搜索请求
     */
    private SearchRequest buildSearchRequest(AggRequest request, IndexMetadata metadata,
                                             QueryRequest.DateRange dateRange, DowngradeLevel downgradeLevel) {
        // ✅ 1. 计算需要查询的索引列表（索引路由，带降级支持）
        String[] indices = indexRouteProcessor.routeWithDowngrade(metadata, dateRange, downgradeLevel);
        SearchRequest searchRequest = new SearchRequest(indices);

        // ✅ 允许查询不存在的索引（date-split 场景下部分索引可能不存在）
        if (properties.getQueryLimits().isIgnoreUnavailableIndices()) {
            searchRequest.indicesOptions(IndicesOptions.lenientExpandOpen());
            log.trace("Enabled ignoreUnavailableIndices for aggregation on indices: {}", (Object) indices);
        }

        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();

        // 2. 构建查询条件（过滤）
        QueryBuilder queryBuilder;
        if (request.getQuery() != null) {
            queryBuilder = queryDslBuilder.build(request.getIndex(), request.getQuery());
        } else {
            queryBuilder = QueryBuilders.matchAllQuery();
        }
        sourceBuilder.query(queryBuilder);

        // 3. 构建聚合
        AggregationBuilder[] aggBuilders = aggDslBuilder.build(request.getIndex(), request.getAggs(), request.getAfter());
        for (AggregationBuilder aggBuilder : aggBuilders) {
            sourceBuilder.aggregation(aggBuilder);
        }

        // 4. 不返回文档，只返回聚合结果
        sourceBuilder.size(SimpleElasticsearchSearchConstant.AGG_NO_DOCS_SIZE);

        searchRequest.source(sourceBuilder);

        return searchRequest;
    }

    /**
     * ✅ 从查询条件中提取日期范围（用于索引路由）
     * 只有在日期分割索引时才需要
     */
    private QueryRequest.DateRange extractDateRangeFromQuery(QueryCondition query, IndexMetadata metadata) {
        if (query == null || !metadata.isDateSplit()) {
            return null;
        }

        // 递归查找日期字段的 BETWEEN 或 RANGE 条件
        String dateField = metadata.getDateField();
        return findDateRange(query, dateField);
    }

    /**
     * ✅ 递归查找日期范围条件
     */
    private QueryRequest.DateRange findDateRange(QueryCondition condition, String dateField) {
        if (condition == null) {
            return null;
        }

        // 检查当前条件是否是日期字段的范围查询
        if (dateField.equals(condition.getField())) {
            // BETWEEN 操作符
            if (QueryOperator.BETWEEN.getOperator().equalsIgnoreCase(condition.getOp()) && condition.getValues() != null && condition.getValues().size() >= 2) {
                return QueryRequest.DateRange.builder()
                        .from(condition.getValues().get(0).toString())
                        .to(condition.getValues().get(1).toString())
                        .build();
            }

            // GTE + LTE 组合（可能在 AND 条件中）
            // 这种情况比较复杂，暂时不处理
        }

        // 递归查找子条件
        if (condition.getConditions() != null) {
            for (QueryCondition subCondition : condition.getConditions()) {
                QueryRequest.DateRange range = findDateRange(subCondition, dateField);
                if (range != null) {
                    return range;
                }
            }
        }

        return null;
    }

    /**
     * 处理聚合响应
     */
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

    /**
     * 解析聚合结果
     */
    private Object parseAggregation(Aggregation aggregation) {
        // 1. Metrics 聚合
        if (aggregation instanceof NumericMetricsAggregation.SingleValue) {
            return ((NumericMetricsAggregation.SingleValue) aggregation).value();
        }

        // 2. Stats 聚合
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

        // 3. Bucket 聚合
        if (aggregation instanceof MultiBucketsAggregation) {
            return parseBucketAggregation((MultiBucketsAggregation) aggregation);
        }

        // 4. 其他类型
        return aggregation.toString();
    }

    /**
     * 解析 Bucket 聚合
     */
    private List<Map<String, Object>> parseBucketAggregation(MultiBucketsAggregation aggregation) {
        List<Map<String, Object>> buckets = new ArrayList<>();

        for (MultiBucketsAggregation.Bucket bucket : aggregation.getBuckets()) {
            Map<String, Object> bucketMap = new HashMap<>();

            // Bucket key
            bucketMap.put(SimpleElasticsearchSearchConstant.AGG_RESULT_KEY, bucket.getKeyAsString());
            bucketMap.put(SimpleElasticsearchSearchConstant.AGG_RESULT_COUNT, bucket.getDocCount());

            // 嵌套聚合
            Aggregations subAggs = bucket.getAggregations();
            if (subAggs != null && !subAggs.asList().isEmpty()) {
                for (Aggregation subAgg : subAggs) {
                    Object subResult = parseAggregation(subAgg);
                    bucketMap.put(subAgg.getName(), subResult);
                }
            }

            buckets.add(bucketMap);
        }

        return buckets;
    }
}
