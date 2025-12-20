package io.github.surezzzzzz.sdk.elasticsearch.search.query.executor;

import io.github.surezzzzzz.sdk.elasticsearch.route.registry.SimpleElasticsearchRouteRegistry;
import io.github.surezzzzzz.sdk.elasticsearch.route.support.RouteResolver;
import io.github.surezzzzzz.sdk.elasticsearch.search.annotation.SimpleElasticsearchSearchComponent;
import io.github.surezzzzzz.sdk.elasticsearch.search.configuration.SimpleElasticsearchSearchProperties;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.ErrorCode;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.SimpleElasticsearchSearchConstant;
import io.github.surezzzzzz.sdk.elasticsearch.search.exception.QueryException;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.MappingManager;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.model.IndexMetadata;
import io.github.surezzzzzz.sdk.elasticsearch.search.processor.IndexRouteProcessor;
import io.github.surezzzzzz.sdk.elasticsearch.search.processor.SensitiveFieldProcessor;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.builder.QueryDslBuilder;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.PaginationInfo;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryRequest;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryResponse;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 查询执行器实现
 *
 * <p><b>版本兼容性说明：</b>
 * <ul>
 *   <li>使用 simple-elasticsearch-route-starter 提供的 SimpleElasticsearchRouteRegistry</li>
 *   <li>根据索引名称通过 RouteResolver 路由到对应数据源</li>
 *   <li>获取该数据源版本自适应的 RestHighLevelClient，避免版本兼容性问题</li>
 * </ul>
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleElasticsearchSearchComponent
public class QueryExecutorImpl implements QueryExecutor {

    @Autowired
    private SimpleElasticsearchSearchProperties properties;

    @Autowired
    private MappingManager mappingManager;

    @Autowired
    private QueryDslBuilder queryDslBuilder;

    @Autowired
    private IndexRouteProcessor indexRouteProcessor;

    @Autowired
    private SensitiveFieldProcessor sensitiveFieldProcessor;

    @Autowired
    private SimpleElasticsearchRouteRegistry registry;

    @Autowired
    private RouteResolver routeResolver;

    @Override
    public QueryResponse execute(QueryRequest request) {
        long startTime = System.currentTimeMillis();

        try {
            // 1. 参数验证
            validateRequest(request);

            // 2. 获取索引元数据
            IndexMetadata metadata = mappingManager.getMetadata(request.getIndex());

            // 3. 构建 ES 查询
            SearchRequest searchRequest = buildSearchRequest(request, metadata);

            // 4. 执行查询

            log.debug("Executing query: indices={}, dsl={}",
                    String.join(",", searchRequest.indices()),
                    searchRequest.source().toString());

            // 根据索引名称路由到对应数据源，获取版本自适应的 RestHighLevelClient
            String datasourceKey = routeResolver.resolveDataSource(request.getIndex());
            log.debug("Index [{}] routed to datasource [{}]", request.getIndex(), datasourceKey);
            RestHighLevelClient client = registry.getHighLevelClient(datasourceKey);

            SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);

            // 5. 处理结果
            QueryResponse response = processResponse(request, searchResponse);

            // 6. 计算耗时
            long took = System.currentTimeMillis() - startTime;
            response.setTook(took);

            log.debug("Query executed: index={}, took={}ms, hits={}",
                    request.getIndex(), took, response.getTotal());

            return response;

        } catch (IOException e) {
            log.error("Query execution failed: index={}", request.getIndex(), e);
            throw new QueryException(ErrorCode.QUERY_EXECUTION_FAILED, ErrorMessage.QUERY_EXECUTION_FAILED, e);
        }
    }

    /**
     * 验证请求参数
     */
    private void validateRequest(QueryRequest request) {
        if (request.getIndex() == null || request.getIndex().trim().isEmpty()) {
            throw new QueryException(ErrorCode.INDEX_ALIAS_REQUIRED, ErrorMessage.INDEX_ALIAS_REQUIRED);
        }

        // 验证分页参数
        PaginationInfo pagination = request.getPagination();
        if (pagination == null) {
            // 使用默认分页
            pagination = PaginationInfo.builder()
                    .type(SimpleElasticsearchSearchConstant.PAGINATION_TYPE_OFFSET)
                    .page(1)
                    .size(properties.getQueryLimits().getDefaultSize())
                    .build();
            request.setPagination(pagination);
        }

        // 验证 size
        if (pagination.getSize() == null) {
            pagination.setSize(properties.getQueryLimits().getDefaultSize());
        }
        if (pagination.getSize() > properties.getQueryLimits().getMaxSize()) {
            throw new QueryException(ErrorCode.QUERY_SIZE_EXCEEDED,
                    String.format(ErrorMessage.QUERY_SIZE_EXCEEDED, properties.getQueryLimits().getMaxSize()));
        }

        // 验证 offset 分页深度
        if (pagination.isOffsetPagination()) {
            if (pagination.getPage() == null) {
                pagination.setPage(1);
            }
            int from = (pagination.getPage() - 1) * pagination.getSize();
            if (from + pagination.getSize() > properties.getQueryLimits().getMaxOffset()) {
                throw new QueryException(ErrorCode.OFFSET_PAGINATION_EXCEEDED,
                        String.format(ErrorMessage.OFFSET_PAGINATION_EXCEEDED, properties.getQueryLimits().getMaxOffset()));
            }
        }

        // search_after 必须有排序
        if (pagination.isSearchAfterPagination()) {
            if (pagination.getSort() == null || pagination.getSort().isEmpty()) {
                throw new QueryException(ErrorCode.SEARCH_AFTER_SORT_REQUIRED, ErrorMessage.SEARCH_AFTER_SORT_REQUIRED);
            }
        }
    }

    /**
     * 构建 ES 搜索请求
     */
    private SearchRequest buildSearchRequest(QueryRequest request, IndexMetadata metadata) {
        // ✅ 1. 计算需要查询的索引列表（索引路由）
        String[] indices = indexRouteProcessor.route(metadata, request.getDateRange());
        SearchRequest searchRequest = new SearchRequest(indices);

        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();

        // 2. 构建查询条件
        QueryBuilder queryBuilder = queryDslBuilder.build(request.getIndex(), request.getQuery());
        sourceBuilder.query(queryBuilder);

        // ✅ 3. 添加日期范围过滤（如果需要更精确的时间过滤）
        if (request.getDateRange() != null && metadata.isDateSplit() && metadata.getDateField() != null) {
            addDateRangeFilter(sourceBuilder, request.getDateRange(), metadata);
        }

        // 4. 分页
        PaginationInfo pagination = request.getPagination();
        if (pagination.isOffsetPagination()) {
            // offset 分页
            int from = (pagination.getPage() - 1) * pagination.getSize();
            sourceBuilder.from(from);
            sourceBuilder.size(pagination.getSize());
        } else {
            // search_after 分页
            sourceBuilder.size(pagination.getSize());
            if (pagination.getSearchAfter() != null) {
                sourceBuilder.searchAfter(pagination.getSearchAfter().toArray());
            }
        }

        // 5. 排序
        if (pagination.getSort() != null && !pagination.getSort().isEmpty()) {
            for (PaginationInfo.SortField sortField : pagination.getSort()) {
                SortOrder order = SimpleElasticsearchSearchConstant.SORT_ORDER_DESC.equalsIgnoreCase(sortField.getOrder()) ?
                        SortOrder.DESC : SortOrder.ASC;
                sourceBuilder.sort(sortField.getField(), order);
            }
        }

        // 6. 字段投影
        if (request.getFields() != null && !request.getFields().isEmpty()) {
            sourceBuilder.fetchSource(
                    request.getFields().toArray(new String[0]),
                    null
            );
        }

        // 7. 是否返回 _score
        if (!properties.getApi().isIncludeScore()) {
            sourceBuilder.trackScores(false);
        }

        searchRequest.source(sourceBuilder);

        return searchRequest;
    }

    /**
     * ✅ 判断是否需要额外的日期过滤
     * 如果时间范围精确到小时/分钟/秒，需要在查询中过滤
     * 如果只是日期范围（00:00:00 ~ 23:59:59），索引路由已经优化了，不需要额外过滤
     */
    private boolean needsDateFilter(QueryRequest.DateRange dateRange) {
        // 如果日期字符串包含时间部分（非 00:00:00 和 23:59:59），则需要过滤
        boolean fromHasTime = dateRange.getFrom().contains("T") &&
                !dateRange.getFrom().endsWith("T00:00:00") &&
                !dateRange.getFrom().endsWith("T00:00:00.000");

        boolean toHasTime = dateRange.getTo().contains("T") &&
                !dateRange.getTo().endsWith("T23:59:59") &&
                !dateRange.getTo().endsWith("T23:59:59.999");

        return fromHasTime || toHasTime;
    }

    /**
     * 添加日期范围过滤
     */
    private void addDateRangeFilter(SearchSourceBuilder sourceBuilder,
                                    QueryRequest.DateRange dateRange,
                                    IndexMetadata metadata) {
        String dateField = metadata.getDateField();
        if (dateField == null) {
            return;
        }

        QueryBuilder dateFilter = org.elasticsearch.index.query.QueryBuilders.rangeQuery(dateField)
                .gte(dateRange.getFrom())
                .lte(dateRange.getTo());

        // 将日期过滤添加到 query 中
        QueryBuilder originalQuery = sourceBuilder.query();
        sourceBuilder.query(
                org.elasticsearch.index.query.QueryBuilders.boolQuery()
                        .must(originalQuery)
                        .filter(dateFilter)
        );
    }

    /**
     * 处理查询响应
     */
    private QueryResponse processResponse(QueryRequest request, SearchResponse searchResponse) {
        QueryResponse.QueryResponseBuilder builder = QueryResponse.builder();

        // 1. 总数
        long total = searchResponse.getHits().getTotalHits().value;
        builder.total(total);

        // 2. 分页信息
        PaginationInfo pagination = request.getPagination();
        builder.page(pagination.getPage());
        builder.size(pagination.getSize());

        // 3. 处理数据
        List<Map<String, Object>> items = new ArrayList<>();
        Object[] lastSortValues = null;

        for (SearchHit hit : searchResponse.getHits().getHits()) {
            Map<String, Object> source = hit.getSourceAsMap();

            // 处理敏感字段
            sensitiveFieldProcessor.process(request.getIndex(), source);

            // 添加 _id（可选）
            source.put(SimpleElasticsearchSearchConstant.ES_FIELD_ID, hit.getId());

            // 添加 _score（如果需要）
            if (properties.getApi().isIncludeScore()) {
                source.put(SimpleElasticsearchSearchConstant.ES_FIELD_SCORE, hit.getScore());
            }

            items.add(source);

            // 记录最后一个文档的 sort 值（用于 search_after）
            if (hit.getSortValues() != null && hit.getSortValues().length > 0) {
                lastSortValues = hit.getSortValues();
            }
        }

        builder.items(items);

        // 4. 分页结果
        QueryResponse.PaginationResult paginationResult = QueryResponse.PaginationResult.builder()
                .type(pagination.getType())
                .hasMore(items.size() >= pagination.getSize())
                .build();

        // search_after 的下一页参数
        if (pagination.isSearchAfterPagination() && lastSortValues != null) {
            List<Object> nextSearchAfter = new ArrayList<>();
            for (Object value : lastSortValues) {
                nextSearchAfter.add(value);
            }
            paginationResult.setNextSearchAfter(nextSearchAfter);
        }

        builder.pagination(paginationResult);

        return builder.build();
    }
}
