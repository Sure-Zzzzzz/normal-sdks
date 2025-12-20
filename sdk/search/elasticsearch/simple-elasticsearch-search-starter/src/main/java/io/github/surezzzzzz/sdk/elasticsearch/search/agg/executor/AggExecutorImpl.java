package io.github.surezzzzzz.sdk.elasticsearch.search.agg.executor;

import io.github.surezzzzzz.sdk.elasticsearch.route.registry.SimpleElasticsearchRouteRegistry;
import io.github.surezzzzzz.sdk.elasticsearch.route.support.RouteResolver;
import io.github.surezzzzzz.sdk.elasticsearch.search.agg.builder.AggregationDslBuilder;
import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.AggRequest;
import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.AggResponse;
import io.github.surezzzzzz.sdk.elasticsearch.search.annotation.SimpleElasticsearchSearchComponent;
import io.github.surezzzzzz.sdk.elasticsearch.search.configuration.SimpleElasticsearchSearchProperties;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.ErrorCode;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.QueryOperator;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.SimpleElasticsearchSearchConstant;
import io.github.surezzzzzz.sdk.elasticsearch.search.exception.AggregationException;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.MappingManager;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.model.IndexMetadata;
import io.github.surezzzzzz.sdk.elasticsearch.search.processor.IndexRouteProcessor;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.builder.QueryDslBuilder;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryCondition;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryRequest;
import io.github.surezzzzzz.sdk.elasticsearch.search.support.ElasticsearchCompatibilityHelper;
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
import org.elasticsearch.search.aggregations.metrics.NumericMetricsAggregation;
import org.elasticsearch.search.aggregations.metrics.Stats;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.annotation.Autowired;

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

    @Override
    public AggResponse execute(AggRequest request) {
        long startTime = System.currentTimeMillis();

        try {
            // 1. 参数验证
            validateRequest(request);

            // 2. 获取索引元数据
            IndexMetadata metadata = mappingManager.getMetadata(request.getIndex());

            // 3. 构建 ES 查询
            SearchRequest searchRequest = buildSearchRequest(request, metadata);

            // 4. 执行查询

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

            // 5. 处理结果
            AggResponse response = processResponse(searchResponse);

            // 6. 计算耗时
            long took = System.currentTimeMillis() - startTime;
            response.setTook(took);

            log.debug("Aggregation executed: index={}, took={}ms", request.getIndex(), took);

            return response;

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
     * 手动解析 ES 6.x 聚合响应 JSON
     * 保持与 processResponse() 相同的数据结构
     */
    private AggResponse parseEs6xAggregationResponse(String responseJson) throws Exception {
        com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

        @SuppressWarnings("unchecked")
        Map<String, Object> responseMap = objectMapper.readValue(responseJson,
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});

        // 提取聚合结果
        @SuppressWarnings("unchecked")
        Map<String, Object> rawAggregations = (Map<String, Object>) responseMap.get(SimpleElasticsearchSearchConstant.ES_JSON_AGGREGATIONS);

        if (rawAggregations == null) {
            return AggResponse.builder()
                    .aggregations(new HashMap<>())
                    .build();
        }

        // 手动解析聚合结果，提取实际值（和 processResponse 保持一致）
        Map<String, Object> parsedAggregations = new HashMap<>();
        for (Map.Entry<String, Object> entry : rawAggregations.entrySet()) {
            String aggName = entry.getKey();
            Object aggValue = entry.getValue();

            Object parsedValue = parseAggregationValue(aggValue);
            parsedAggregations.put(aggName, parsedValue);
        }

        // ✅ 构建响应，根据配置决定是否包含原始聚合数据
        AggResponse.AggResponseBuilder builder = AggResponse.builder()
                .aggregations(parsedAggregations);

        // 如果配置启用了 include-raw-response，添加原始聚合数据（未解析的）
        if (properties.getApi().isIncludeRawResponse()) {
            builder.rawResponse(rawAggregations);
            log.debug("Included raw ES 6.x aggregations in AggResponse (config enabled)");
        }

        return builder.build();
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
    }

    /**
     * 构建 ES 搜索请求
     */
    private SearchRequest buildSearchRequest(AggRequest request, IndexMetadata metadata) {
        // ✅ 1. 计算需要查询的索引列表（索引路由）
        // 从 query 条件中提取日期范围（如果是日期分割索引）
        QueryRequest.DateRange dateRange = extractDateRangeFromQuery(request.getQuery(), metadata);
        String[] indices = indexRouteProcessor.route(metadata, dateRange);
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
        AggregationBuilder[] aggBuilders = aggDslBuilder.build(request.getIndex(), request.getAggs());
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

        Aggregations aggregations = searchResponse.getAggregations();
        if (aggregations != null) {
            for (Aggregation aggregation : aggregations) {
                Object result = parseAggregation(aggregation);
                data.put(aggregation.getName(), result);
            }
        }

        return AggResponse.builder()
                .aggregations(data)
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
