package io.github.surezzzzzz.sdk.elasticsearch.search.query.executor;

import io.github.surezzzzzz.sdk.elasticsearch.route.constant.SimpleElasticsearchRouteConstant;
import io.github.surezzzzzz.sdk.elasticsearch.route.model.ClusterInfo;
import io.github.surezzzzzz.sdk.elasticsearch.route.model.LowLevelSearchResult;
import io.github.surezzzzzz.sdk.elasticsearch.route.support.ElasticsearchEndpointHelper;
import io.github.surezzzzzz.sdk.elasticsearch.route.support.ElasticsearchLowLevelRequestHelper;
import io.github.surezzzzzz.sdk.elasticsearch.route.support.ElasticsearchRequestOptionHelper;
import io.github.surezzzzzz.sdk.elasticsearch.route.support.ElasticsearchResponseHelper;
import io.github.surezzzzzz.sdk.elasticsearch.route.support.ElasticsearchVersionHelper;
import io.github.surezzzzzz.sdk.elasticsearch.route.support.XContentCompatibilityHelper;
import io.github.surezzzzzz.sdk.elasticsearch.search.annotation.SimpleElasticsearchSearchComponent;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.DowngradeLevel;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.ErrorCode;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.SimpleElasticsearchSearchConstant;
import io.github.surezzzzzz.sdk.elasticsearch.search.core.event.EsQueryErrorEvent;
import io.github.surezzzzzz.sdk.elasticsearch.search.core.event.EsQueryEvent;
import io.github.surezzzzzz.sdk.elasticsearch.search.core.model.QueryExecutionContext;
import io.github.surezzzzzz.sdk.elasticsearch.search.exception.DowngradeFailedException;
import io.github.surezzzzzz.sdk.elasticsearch.search.exception.QueryException;
import io.github.surezzzzzz.sdk.elasticsearch.search.executor.AbstractExecutor;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.model.IndexMetadata;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.model.ResolvedIndexConfig;
import io.github.surezzzzzz.sdk.elasticsearch.search.processor.SensitiveFieldProcessor;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.builder.QueryDslBuilder;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.PaginationInfo;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryRequest;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryResponse;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.pagination.PaginationStrategy;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.pagination.PaginationStrategyRegistry;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.validator.QueryRequestValidatorChain;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.collapse.CollapseBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 查询执行器实现
 * 继承 {@link AbstractExecutor} 获得通用执行骨架（降级重试、异常处理），
 * 实现 query 特有的 DSL 构建、响应处理逻辑。
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleElasticsearchSearchComponent
public class QueryExecutor extends AbstractExecutor<QueryRequest, QueryResponse> {

    @Autowired
    private QueryDslBuilder queryDslBuilder;

    @Autowired
    private SensitiveFieldProcessor sensitiveFieldProcessor;

    @Autowired
    private PaginationStrategyRegistry paginationStrategyRegistry;

    @Autowired
    private QueryRequestValidatorChain validatorChain;

    // ==================== 抽象方法实现 ====================

    @Override
    protected void validateRequest(QueryRequest request) {
        validatorChain.validate(request, properties);
    }

    @Override
    protected boolean needsDowngradeRetry(QueryRequest request, ResolvedIndexConfig resolvedIndexConfig,
                                           IndexMetadata metadata) {
        return properties.getDowngrade().isEnabled()
                && !resolvedIndexConfig.isWildcardMatched()
                && metadata.isDateSplit()
                && request.getDateRange() != null;
    }

    @Override
    protected QueryResponse executeOnce(QueryRequest request, ResolvedIndexConfig resolvedIndexConfig,
                                        IndexMetadata metadata, long startTime,
                                        DowngradeLevel level) throws IOException {
        PaginationInfo pagination = request.getPagination();

        // scroll 后续翻页：使用 scroll API，不走 search API
        if (pagination != null && pagination.isScrollPagination()
                && StringUtils.hasText(pagination.getScrollId())) {
            SearchResponse searchResponse = executeScrollRequest(request);
            QueryResponse response = processResponse(request, resolvedIndexConfig, searchResponse);
            response.setTook(System.currentTimeMillis() - startTime);

            // 最后一页自动清除 scroll 上下文
            if (response.getPagination() != null
                    && Boolean.FALSE.equals(response.getPagination().getHasMore())
                    && searchResponse.getScrollId() != null) {
                closeScrollQuietly(searchResponse.getScrollId(), request.getIndex());
            }

            String datasourceKey = routeResolver.resolveDataSource(request.getIndex());
            try {
                QueryExecutionContext context = QueryExecutionContext.builder()
                        .actualIndices(new String[]{request.getIndex()})
                        .datasource(datasourceKey)
                        .downgradeLevel(0)
                        .sourceType(request.getSourceType())
                        .build();
                eventPublisher.publishEvent(new EsQueryEvent(this, request, response, context));
            } catch (Exception e) {
                log.warn("Failed to publish EsQueryEvent for scroll continuation", e);
            }

            return response;
        }

        SearchRequest searchRequest = buildSearchRequest(request, resolvedIndexConfig, metadata, level);

        log.debug("Executing query: indices={}, dsl={}",
                String.join(",", searchRequest.indices()),
                searchRequest.source().toString());

        String datasourceKey = routeResolver.resolveDataSource(request.getIndex());
        log.debug("Index [{}] routed to datasource [{}]", request.getIndex(), datasourceKey);
        RestHighLevelClient client = registry.getHighLevelClient(datasourceKey);

        SearchResponse searchResponse = executeSearch(client, datasourceKey, searchRequest, request);

        QueryResponse response = processResponse(request, resolvedIndexConfig, searchResponse);
        response.setTook(System.currentTimeMillis() - startTime);

        // scroll 第一页：如果 hasMore=false（数据量不足一页），也需要清除 scroll 上下文
        if (pagination != null && pagination.isScrollPagination()
                && response.getPagination() != null
                && Boolean.FALSE.equals(response.getPagination().getHasMore())
                && searchResponse.getScrollId() != null) {
            closeScrollQuietly(searchResponse.getScrollId(), request.getIndex());
        }

        log.debug("Query executed: index={}, downgradeLevel={}, took={}ms, hits={}",
                request.getIndex(), level, response.getTook(), response.getTotal());

        try {
            QueryExecutionContext context = QueryExecutionContext.builder()
                    .actualIndices(searchRequest.indices())
                    .datasource(datasourceKey)
                    .downgradeLevel(level.getValue())
                    .sourceType(request.getSourceType())
                    .build();
            eventPublisher.publishEvent(new EsQueryEvent(this, request, response, context));
        } catch (Exception e) {
            log.warn("Failed to publish EsQueryEvent", e);
        }

        return response;
    }

    @Override
    protected String getIndex(QueryRequest request) {
        return request.getIndex();
    }

    @Override
    protected RuntimeException wrapIoException(IOException e) {
        return new QueryException(ErrorCode.QUERY_EXECUTION_FAILED, ErrorMessage.QUERY_EXECUTION_FAILED, e);
    }

    @Override
    protected void onExecutionError(QueryRequest request, Throwable error) {
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
            QueryExecutionContext context = QueryExecutionContext.builder()
                    .datasource(datasource)
                    .downgradeLevel(downgradeLevel)
                    .sourceType(request.getSourceType())
                    .build();
            eventPublisher.publishEvent(new EsQueryErrorEvent(this, request, error, datasource,
                    downgradeLevel, Boolean.TRUE.equals(request.getCountOnly()), context));
        } catch (Exception e) {
            log.warn("Failed to publish EsQueryErrorEvent", e);
        }
    }

    // ==================== 钩子方法覆盖 ====================

    @Override
    protected DowngradeLevel estimateDowngradeLevel(QueryRequest request, ResolvedIndexConfig resolvedIndexConfig,
                                                    IndexMetadata metadata) {
        if (!properties.getDowngrade().isEnableEstimate()) {
            return DowngradeLevel.LEVEL_0;
        }
        String[] estimatedIndices = indexRouteProcessor.route(resolvedIndexConfig, metadata, request.getDateRange());
        DowngradeLevel level = indexRouteProcessor.detectDowngradeLevelFromIndices(estimatedIndices);
        if (level != DowngradeLevel.LEVEL_0) {
            log.info("Pre-estimated downgrade to {} for index [{}]", level, request.getIndex());
        }
        return level;
    }

    // ==================== 私有方法 ====================

    private SearchRequest buildSearchRequest(QueryRequest request, ResolvedIndexConfig resolvedIndexConfig,
                                             IndexMetadata metadata, DowngradeLevel level) {
        String[] indices = indexRouteProcessor.routeWithDowngrade(resolvedIndexConfig, metadata, request.getDateRange(), level);
        SearchRequest searchRequest = new SearchRequest(indices);

        if (properties.getQueryLimits().isIgnoreUnavailableIndices()) {
            searchRequest.indicesOptions(IndicesOptions.lenientExpandOpen());
        }

        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        QueryBuilder queryBuilder = queryDslBuilder.build(metadata, request.getQuery());
        sourceBuilder.query(queryBuilder);

        if (request.getDateRange() != null && metadata.isDateSplit() && metadata.getDateField() != null
                && (properties.getQueryLimits().isStrictDateFilter() || needsDateFilter(request.getDateRange()))) {
            addDateRangeFilter(sourceBuilder, request.getDateRange(), metadata);
        }

        PaginationInfo pagination = request.getPagination();
        PaginationStrategy strategy = paginationStrategyRegistry.resolve(pagination);
        strategy.applyToRequest(sourceBuilder, searchRequest, pagination, request);

        if (request.getFields() != null && !request.getFields().isEmpty()) {
            sourceBuilder.fetchSource(request.getFields().toArray(new String[0]), null);
        }

        if (request.getCollapse() != null && request.getCollapse().getField() != null) {
            CollapseBuilder collapseBuilder = new CollapseBuilder(request.getCollapse().getField());
            if (request.getCollapse().getMaxConcurrentGroupSearches() != null) {
                collapseBuilder.setMaxConcurrentGroupRequests(
                        request.getCollapse().getMaxConcurrentGroupSearches());
            }
            sourceBuilder.collapse(collapseBuilder);
        }

        if (!properties.getApi().isIncludeScore()) {
            sourceBuilder.trackScores(false);
        }

        searchRequest.source(sourceBuilder);
        return searchRequest;
    }

    private boolean needsDateFilter(QueryRequest.DateRange dateRange) {
        boolean fromHasTime = dateRange.getFrom().contains(SimpleElasticsearchSearchConstant.DATE_TIME_SEPARATOR)
                && !dateRange.getFrom().endsWith(SimpleElasticsearchSearchConstant.DATE_START_OF_DAY)
                && !dateRange.getFrom().endsWith(SimpleElasticsearchSearchConstant.DATE_START_OF_DAY_MILLIS);
        boolean toHasTime = dateRange.getTo().contains(SimpleElasticsearchSearchConstant.DATE_TIME_SEPARATOR)
                && !dateRange.getTo().endsWith(SimpleElasticsearchSearchConstant.DATE_END_OF_DAY)
                && !dateRange.getTo().endsWith(SimpleElasticsearchSearchConstant.DATE_END_OF_DAY_MILLIS);
        return fromHasTime || toHasTime;
    }

    private void addDateRangeFilter(SearchSourceBuilder sourceBuilder,
                                    QueryRequest.DateRange dateRange, IndexMetadata metadata) {
        String dateField = metadata.getDateField();
        if (dateField == null) {
            return;
        }
        QueryBuilder dateFilter = QueryBuilders.rangeQuery(dateField)
                .gte(dateRange.getFrom())
                .lte(dateRange.getTo());
        QueryBuilder originalQuery = sourceBuilder.query();
        sourceBuilder.query(QueryBuilders.boolQuery().must(originalQuery).filter(dateFilter));
    }

    private QueryResponse processResponse(QueryRequest request, ResolvedIndexConfig resolvedIndexConfig,
                                          SearchResponse searchResponse) {
        QueryResponse.QueryResponseBuilder builder = QueryResponse.builder();
        builder.total(ElasticsearchResponseHelper.extractTotalHits(searchResponse.getHits()));

        PaginationInfo pagination = request.getPagination();
        builder.page(pagination.getPage());
        builder.size(pagination.getSize());

        List<Map<String, Object>> items = new ArrayList<>();
        for (SearchHit hit : searchResponse.getHits().getHits()) {
            Map<String, Object> source = hit.getSourceAsMap();
            sensitiveFieldProcessor.process(resolvedIndexConfig.getConfigIdentifier(), source);
            source.put(SimpleElasticsearchSearchConstant.ES_FIELD_ID, hit.getId());
            if (properties.getApi().isIncludeScore()) {
                source.put(SimpleElasticsearchSearchConstant.ES_FIELD_SCORE, hit.getScore());
            }
            items.add(source);
        }
        builder.items(items);

        PaginationStrategy strategy = paginationStrategyRegistry.resolve(pagination);
        QueryResponse.PaginationResult paginationResult = strategy.buildResult(searchResponse, pagination, request);
        builder.pagination(paginationResult);

        return builder.build();
    }

    private SearchResponse executeSearch(RestHighLevelClient client, String datasourceKey,
                                         SearchRequest searchRequest, QueryRequest request) throws IOException {
        ClusterInfo clusterInfo = registry.getClusterInfo(datasourceKey);
        if (ElasticsearchVersionHelper.isEs6(clusterInfo)) {
            return executeLowLevelSearch(client, searchRequest, request);
        }
        try {
            return client.search(searchRequest, org.elasticsearch.client.RequestOptions.DEFAULT);
        } catch (org.elasticsearch.ElasticsearchStatusException e) {
            if (ElasticsearchVersionHelper.isUnknown(clusterInfo)
                    && ElasticsearchResponseHelper.shouldFallbackToLowLevel(e)) {
                log.warn("高级 API 遇到版本兼容问题，降级为低级 API 执行，数据源：{}", datasourceKey);
                return executeLowLevelSearch(client, searchRequest, request);
            }
            throw e;
        }
    }

    private SearchResponse executeLowLevelSearch(RestHighLevelClient client,
                                                 SearchRequest searchRequest,
                                                 QueryRequest request) throws IOException {
        String scrollKeepAlive = null;
        PaginationInfo pagination = request == null ? null : request.getPagination();
        if (pagination != null && pagination.isScrollPagination()) {
            scrollKeepAlive = pagination.getScrollTtl();
        }
        String jsonBody = searchRequest != null && searchRequest.source() != null ? searchRequest.source().toString() : null;
        String[] indices = searchRequest == null ? null : searchRequest.indices();
        org.elasticsearch.client.Request lowLevelRequest = ElasticsearchLowLevelRequestHelper.newSearchRequest(
                indices, jsonBody, scrollKeepAlive);
        if (searchRequest != null) {
            ElasticsearchRequestOptionHelper.applyIndicesOptions(lowLevelRequest, searchRequest.indicesOptions());
        }
        org.elasticsearch.client.Response response = ElasticsearchLowLevelRequestHelper.execute(
                client.getLowLevelClient(), lowLevelRequest);
        byte[] responseBytes = ElasticsearchLowLevelRequestHelper.readResponseBytes(response);
        LowLevelSearchResult result = LowLevelSearchResult.builder()
                .searchResponse(XContentCompatibilityHelper.parseSearchResponse(responseBytes))
                .rawResponseBody(new String(responseBytes, java.nio.charset.StandardCharsets.UTF_8))
                .containsAggregations(false)
                .rawAggregationResponse(false)
                .build();
        return result.getSearchResponse();
    }

    private SearchResponse executeScrollRequest(QueryRequest request) throws IOException {
        PaginationInfo pagination = request.getPagination();
        String datasourceKey = routeResolver.resolveDataSource(request.getIndex());
        RestHighLevelClient client = registry.getHighLevelClient(datasourceKey);

        org.elasticsearch.client.Request scrollRequest = ElasticsearchLowLevelRequestHelper.newJsonRequest(
                SimpleElasticsearchRouteConstant.HTTP_METHOD_POST,
                ElasticsearchEndpointHelper.buildScrollEndpoint(),
                ElasticsearchEndpointHelper.buildScrollContinueBody(pagination.getScrollId(), pagination.getScrollTtl()));

        org.elasticsearch.client.Response response = client.getLowLevelClient().performRequest(scrollRequest);
        return XContentCompatibilityHelper.parseResponse(response, SearchResponse.class);
    }

    private void closeScrollQuietly(String scrollId, String index) {
        try {
            String datasourceKey = routeResolver.resolveDataSource(index);
            RestHighLevelClient client = registry.getHighLevelClient(datasourceKey);
            org.elasticsearch.client.Request closeRequest = ElasticsearchLowLevelRequestHelper.newJsonRequest(
                    SimpleElasticsearchRouteConstant.HTTP_METHOD_DELETE,
                    ElasticsearchEndpointHelper.buildScrollEndpoint(),
                    ElasticsearchEndpointHelper.buildScrollClearBody(scrollId));
            client.getLowLevelClient().performRequest(closeRequest);
            log.debug("Scroll context closed: index={}", index);
        } catch (Exception e) {
            log.warn("Failed to close scroll context for index [{}]: {}", index, e.getMessage());
        }
    }
}
