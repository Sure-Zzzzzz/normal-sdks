package io.github.surezzzzzz.sdk.elasticsearch.search.query.executor;

import io.github.surezzzzzz.sdk.elasticsearch.search.annotation.SimpleElasticsearchSearchComponent;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.DowngradeLevel;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.ErrorCode;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.SimpleElasticsearchSearchConstant;
import io.github.surezzzzzz.sdk.elasticsearch.search.core.event.EsQueryEvent;
import io.github.surezzzzzz.sdk.elasticsearch.search.core.model.QueryExecutionContext;
import io.github.surezzzzzz.sdk.elasticsearch.search.exception.QueryException;
import io.github.surezzzzzz.sdk.elasticsearch.search.executor.AbstractExecutor;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.model.IndexMetadata;
import io.github.surezzzzzz.sdk.elasticsearch.search.processor.SensitiveFieldProcessor;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.builder.QueryDslBuilder;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.PaginationInfo;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryRequest;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryResponse;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.pagination.PaginationStrategy;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.pagination.PaginationStrategyRegistry;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.pagination.PitPaginationStrategy;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.validator.QueryRequestValidatorChain;
import io.github.surezzzzzz.sdk.elasticsearch.search.support.ElasticsearchCompatibilityHelper;
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
    protected boolean needsDowngradeRetry(QueryRequest request, IndexMetadata metadata) {
        return properties.getDowngrade().isEnabled()
                && metadata.isDateSplit()
                && request.getDateRange() != null;
    }

    @Override
    protected QueryResponse executeOnce(QueryRequest request, IndexMetadata metadata,
                                        long startTime, DowngradeLevel level) throws IOException {
        SearchRequest searchRequest = buildSearchRequest(request, metadata, level);

        log.debug("Executing query: indices={}, dsl={}",
                String.join(",", searchRequest.indices()),
                searchRequest.source().toString());

        String datasourceKey = routeResolver.resolveDataSource(request.getIndex());
        log.debug("Index [{}] routed to datasource [{}]", request.getIndex(), datasourceKey);
        RestHighLevelClient client = registry.getHighLevelClient(datasourceKey);

        SearchResponse searchResponse = ElasticsearchCompatibilityHelper.executeSearch(
                client, datasourceKey, searchRequest, registry);

        QueryResponse response = processResponse(request, searchResponse);
        response.setTook(System.currentTimeMillis() - startTime);

        log.debug("Query executed: index={}, downgradeLevel={}, took={}ms, hits={}",
                request.getIndex(), level, response.getTook(), response.getTotal());

        try {
            QueryExecutionContext context = QueryExecutionContext.builder()
                    .actualIndices(searchRequest.indices())
                    .datasource(datasourceKey)
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

    // ==================== 钩子方法覆盖 ====================

    @Override
    protected DowngradeLevel estimateDowngradeLevel(QueryRequest request, IndexMetadata metadata) {
        if (!properties.getDowngrade().isEnableEstimate()) {
            return DowngradeLevel.LEVEL_0;
        }
        String[] estimatedIndices = indexRouteProcessor.route(metadata, request.getDateRange());
        DowngradeLevel level = indexRouteProcessor.detectDowngradeLevelFromIndices(estimatedIndices);
        if (level != DowngradeLevel.LEVEL_0) {
            log.info("Pre-estimated downgrade to {} for index [{}]", level, request.getIndex());
        }
        return level;
    }

    // ==================== 私有方法 ====================

    private SearchRequest buildSearchRequest(QueryRequest request, IndexMetadata metadata,
                                             DowngradeLevel level) {
        String[] indices = indexRouteProcessor.routeWithDowngrade(metadata, request.getDateRange(), level);
        SearchRequest searchRequest = new SearchRequest(indices);

        if (properties.getQueryLimits().isIgnoreUnavailableIndices()) {
            searchRequest.indicesOptions(IndicesOptions.lenientExpandOpen());
        }

        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        QueryBuilder queryBuilder = queryDslBuilder.build(request.getIndex(), request.getQuery());
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
        boolean fromHasTime = dateRange.getFrom().contains("T")
                && !dateRange.getFrom().endsWith("T00:00:00")
                && !dateRange.getFrom().endsWith("T00:00:00.000");
        boolean toHasTime = dateRange.getTo().contains("T")
                && !dateRange.getTo().endsWith("T23:59:59")
                && !dateRange.getTo().endsWith("T23:59:59.999");
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

    private QueryResponse processResponse(QueryRequest request, SearchResponse searchResponse) {
        QueryResponse.QueryResponseBuilder builder = QueryResponse.builder();
        builder.total(searchResponse.getHits().getTotalHits().value);

        PaginationInfo pagination = request.getPagination();
        builder.page(pagination.getPage());
        builder.size(pagination.getSize());

        List<Map<String, Object>> items = new ArrayList<>();
        for (SearchHit hit : searchResponse.getHits().getHits()) {
            Map<String, Object> source = hit.getSourceAsMap();
            sensitiveFieldProcessor.process(request.getIndex(), source);
            source.put(SimpleElasticsearchSearchConstant.ES_FIELD_ID, hit.getId());
            if (properties.getApi().isIncludeScore()) {
                source.put(SimpleElasticsearchSearchConstant.ES_FIELD_SCORE, hit.getScore());
            }
            items.add(source);
        }
        builder.items(items);

        PaginationStrategy strategy = paginationStrategyRegistry.resolve(pagination);
        QueryResponse.PaginationResult paginationResult;
        if (strategy instanceof PitPaginationStrategy) {
            paginationResult = ((PitPaginationStrategy) strategy)
                    .buildResultWithRequest(searchResponse, pagination, request);
        } else {
            paginationResult = strategy.buildResult(searchResponse, pagination);
        }
        builder.pagination(paginationResult);

        return builder.build();
    }
}
