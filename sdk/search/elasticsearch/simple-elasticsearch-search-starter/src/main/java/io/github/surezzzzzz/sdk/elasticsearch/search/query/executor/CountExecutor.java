package io.github.surezzzzzz.sdk.elasticsearch.search.query.executor;

import io.github.surezzzzzz.sdk.elasticsearch.search.annotation.SimpleElasticsearchSearchComponent;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.*;
import io.github.surezzzzzz.sdk.elasticsearch.search.core.event.EsQueryErrorEvent;
import io.github.surezzzzzz.sdk.elasticsearch.search.core.event.EsQueryEvent;
import io.github.surezzzzzz.sdk.elasticsearch.search.core.model.QueryExecutionContext;
import io.github.surezzzzzz.sdk.elasticsearch.search.exception.DowngradeFailedException;
import io.github.surezzzzzz.sdk.elasticsearch.search.exception.QueryException;
import io.github.surezzzzzz.sdk.elasticsearch.search.executor.AbstractExecutor;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.model.IndexMetadata;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.builder.QueryDslBuilder;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryRequest;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryResponse;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.validator.CountOnlyValidator;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.validator.DefaultDateRangeValidator;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.validator.IndexAliasValidator;
import io.github.surezzzzzz.sdk.elasticsearch.search.support.ElasticsearchCompatibilityHelper;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;

/**
 * 总数查询执行器
 * <p>
 * 继承 {@link AbstractExecutor}，走 ES {@code _count} API 获取符合条件的文档总数，
 * 不返回文档内容，性能远高于 {@code _search + size=0}。
 *
 * @author surezzzzzz
 * @since 1.6.6
 */
@Slf4j
@SimpleElasticsearchSearchComponent
public class CountExecutor extends AbstractExecutor<QueryRequest, QueryResponse> {

    @Autowired
    private QueryDslBuilder queryDslBuilder;

    @Autowired
    private IndexAliasValidator indexAliasValidator;

    @Autowired
    private DefaultDateRangeValidator defaultDateRangeValidator;

    @Autowired
    private CountOnlyValidator countOnlyValidator;

    // ==================== 抽象方法实现 ====================

    @Override
    protected void validateRequest(QueryRequest request) {
        // 只校验对 count 有意义的项，不走 QueryRequestValidatorChain（那里有大量无关校验）
        indexAliasValidator.validate(request, properties);
        defaultDateRangeValidator.validate(request, properties);
        countOnlyValidator.validate(request, properties);
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
        // 1. 索引路由
        String[] indices = indexRouteProcessor.routeWithDowngrade(metadata, request.getDateRange(), level);

        // 2. 构建 query DSL
        QueryBuilder queryBuilder = queryDslBuilder.build(metadata, request.getQuery());

        // 3. 日期过滤（与 QueryExecutor.addDateRangeFilter 逻辑一致）
        if (request.getDateRange() != null && metadata.isDateSplit() && metadata.getDateField() != null
                && (properties.getQueryLimits().isStrictDateFilter() || needsDateFilter(request.getDateRange()))) {
            queryBuilder = addDateRangeFilter(queryBuilder, request.getDateRange(), metadata);
        }

        // 4. 执行 _count
        String queryJson = String.format(SimpleElasticsearchSearchConstant.ES_COUNT_QUERY_TEMPLATE,
                queryBuilder.toString());
        String datasourceKey = routeResolver.resolveDataSource(request.getIndex());
        long count = ElasticsearchCompatibilityHelper.executeCount(
                registry.getHighLevelClient(datasourceKey),
                datasourceKey,
                indices,
                queryJson,
                properties.getQueryLimits().isIgnoreUnavailableIndices());

        // 5. 构建响应
        QueryResponse response = QueryResponse.builder()
                .total(count)
                .items(null)
                .page(null)
                .size(null)
                .pagination(null)
                .took(System.currentTimeMillis() - startTime)
                .build();

        log.debug("Count executed: index={}, downgradeLevel={}, count={}, took={}ms",
                request.getIndex(), level, count, response.getTook());

        // 6. 发布事件（sourceType 强制覆盖为 COUNT_API）
        try {
            QueryExecutionContext context = QueryExecutionContext.builder()
                    .actualIndices(indices)
                    .datasource(datasourceKey)
                    .downgradeLevel(level.getValue())
                    .sourceType(SourceType.COUNT_API.getCode())
                    .build();
            eventPublisher.publishEvent(new EsQueryEvent(this, request, response, context));
        } catch (Exception e) {
            log.warn("Failed to publish EsQueryEvent for count", e);
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
                    .sourceType(SourceType.COUNT_API.getCode())
                    .build();
            eventPublisher.publishEvent(new EsQueryErrorEvent(this, request, error, datasource,
                    downgradeLevel, Boolean.TRUE.equals(request.getCountOnly()), context));
        } catch (Exception e) {
            log.warn("Failed to publish EsQueryErrorEvent for count", e);
        }
    }

    // ==================== 私有方法 ====================

    private boolean needsDateFilter(QueryRequest.DateRange dateRange) {
        boolean fromHasTime = dateRange.getFrom().contains(SimpleElasticsearchSearchConstant.DATE_TIME_SEPARATOR)
                && !dateRange.getFrom().endsWith(SimpleElasticsearchSearchConstant.DATE_START_OF_DAY)
                && !dateRange.getFrom().endsWith(SimpleElasticsearchSearchConstant.DATE_START_OF_DAY_MILLIS);
        boolean toHasTime = dateRange.getTo().contains(SimpleElasticsearchSearchConstant.DATE_TIME_SEPARATOR)
                && !dateRange.getTo().endsWith(SimpleElasticsearchSearchConstant.DATE_END_OF_DAY)
                && !dateRange.getTo().endsWith(SimpleElasticsearchSearchConstant.DATE_END_OF_DAY_MILLIS);
        return fromHasTime || toHasTime;
    }

    private QueryBuilder addDateRangeFilter(QueryBuilder originalQuery,
                                            QueryRequest.DateRange dateRange, IndexMetadata metadata) {
        String dateField = metadata.getDateField();
        if (dateField == null) {
            return originalQuery;
        }
        return QueryBuilders.boolQuery()
                .must(originalQuery)
                .filter(QueryBuilders.rangeQuery(dateField)
                        .gte(dateRange.getFrom())
                        .lte(dateRange.getTo()));
    }
}
