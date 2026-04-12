package io.github.surezzzzzz.sdk.elasticsearch.search.query.executor;

import io.github.surezzzzzz.sdk.elasticsearch.route.model.ClusterInfo;
import io.github.surezzzzzz.sdk.elasticsearch.route.registry.SimpleElasticsearchRouteRegistry;
import io.github.surezzzzzz.sdk.elasticsearch.route.resolver.RouteResolver;
import io.github.surezzzzzz.sdk.elasticsearch.search.annotation.SimpleElasticsearchSearchComponent;
import io.github.surezzzzzz.sdk.elasticsearch.search.configuration.SimpleElasticsearchSearchProperties;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.*;
import io.github.surezzzzzz.sdk.elasticsearch.search.core.event.EsQueryEvent;
import io.github.surezzzzzz.sdk.elasticsearch.search.core.model.QueryExecutionContext;
import io.github.surezzzzzz.sdk.elasticsearch.search.exception.DowngradeFailedException;
import io.github.surezzzzzz.sdk.elasticsearch.search.exception.QueryException;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.MappingManager;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.model.IndexMetadata;
import io.github.surezzzzzz.sdk.elasticsearch.search.processor.IndexRouteProcessor;
import io.github.surezzzzzz.sdk.elasticsearch.search.processor.SensitiveFieldProcessor;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.builder.QueryDslBuilder;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.PaginationInfo;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryRequest;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryResponse;
import io.github.surezzzzzz.sdk.elasticsearch.search.support.ElasticsearchCompatibilityHelper;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
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
 *   <li>对于 ES 6.x，自动使用低级 API 绕过参数兼容性问题（如 ignore_throttled）</li>
 * </ul>
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleElasticsearchSearchComponent
public class QueryExecutorImpl implements QueryExecutor {

    private static final com.fasterxml.jackson.databind.ObjectMapper OBJECT_MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper();

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

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Override
    public QueryResponse execute(QueryRequest request) {
        long startTime = System.currentTimeMillis();

        try {
            // 1. 参数验证
            validateRequest(request);

            // 2. 获取索引元数据
            IndexMetadata metadata = mappingManager.getMetadata(request.getIndex());

            // 3. 执行查询（带降级重试）
            if (properties.getDowngrade().isEnabled() && metadata.isDateSplit() && request.getDateRange() != null) {
                return executeWithDowngradeRetry(request, metadata, startTime);
            } else {
                return executeOnce(request, metadata, startTime, DowngradeLevel.LEVEL_0);
            }

        } catch (IOException e) {
            log.error("Query execution failed: index={}", request.getIndex(), e);
            throw new QueryException(ErrorCode.QUERY_EXECUTION_FAILED, ErrorMessage.QUERY_EXECUTION_FAILED, e);
        }
    }

    /**
     * 执行查询（带降级重试）
     */
    private QueryResponse executeWithDowngradeRetry(QueryRequest request, IndexMetadata metadata, long startTime) throws IOException {
        // 先进行降级预估，如果需要降级，直接从预估的级别开始
        DowngradeLevel currentLevel = DowngradeLevel.LEVEL_0;

        // 如果启用了预估，尝试预估降级级别
        if (properties.getDowngrade().isEnableEstimate()) {
            String[] estimatedIndices = indexRouteProcessor.route(metadata, request.getDateRange());
            // 从索引数组中检测降级级别
            currentLevel = indexRouteProcessor.detectDowngradeLevelFromIndices(estimatedIndices);
            if (currentLevel != DowngradeLevel.LEVEL_0) {
                log.info("Pre-estimated downgrade to {} for index [{}]", currentLevel, request.getIndex());
            }
        }

        while (true) {
            try {
                return executeOnce(request, metadata, startTime, currentLevel);

            } catch (ElasticsearchException | IOException e) {
                // 检查是否是 too_long_frame_exception
                if (!isTooLongFrameException(e)) {
                    throw e;
                }

                // 检查是否可以继续降级
                if (!currentLevel.hasNext() || currentLevel.getValue() >= properties.getDowngrade().getMaxLevel()) {
                    log.error("Query failed at max downgrade level {}: index={}", currentLevel, request.getIndex(), e);
                    throw new DowngradeFailedException(
                            ErrorCode.DOWNGRADE_FAILED,
                            String.format(ErrorMessage.DOWNGRADE_FAILED),
                            currentLevel,
                            e
                    );
                }

                // 降级到下一级别
                DowngradeLevel nextLevel = currentLevel.next();
                log.warn("Query failed with too_long_frame_exception at level {}, downgrading to {}: index={}",
                        currentLevel, nextLevel, request.getIndex());
                currentLevel = nextLevel;
            }
        }
    }

    /**
     * 执行一次查询
     */
    private QueryResponse executeOnce(QueryRequest request, IndexMetadata metadata, long startTime, DowngradeLevel downgradeLevel) throws IOException {
        // 1. 构建 ES 查询
        SearchRequest searchRequest = buildSearchRequest(request, metadata, downgradeLevel);

        // 2. 执行查询
        log.debug("Executing query: indices={}, dsl={}",
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
        QueryResponse response = processResponse(request, searchResponse);

        // 4. 计算耗时
        long took = System.currentTimeMillis() - startTime;
        response.setTook(took);

        log.debug("Query executed: index={}, downgradeLevel={}, took={}ms, hits={}",
                request.getIndex(), downgradeLevel, took, response.getTotal());

        // 5. 发布查询事件
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

            // PIT 模式额外校验
            if (SearchAfterMode.PIT == pagination.getSearchAfterModeEnum()) {
                validatePitMode(request, pagination);
            }
        }

        // collapse 必须有排序（深度分页需要）
        if (request.getCollapse() != null && request.getCollapse().getField() != null) {
            if (pagination.getSort() == null || pagination.getSort().isEmpty()) {
                throw new QueryException(ErrorCode.COLLAPSE_SORT_REQUIRED, "使用字段折叠（collapse）时必须指定排序字段");
            }
        }
    }

    /**
     * 校验 PIT 模式参数
     */
    private void validatePitMode(QueryRequest request, PaginationInfo pagination) {
        // 1. ES 版本校验（不暴露版本号，防止信息泄露）
        String datasourceKey = routeResolver.resolveDataSource(request.getIndex());
        ClusterInfo clusterInfo = registry.getClusterInfo(datasourceKey);
        if (clusterInfo == null || clusterInfo.getEffectiveVersion() == null) {
            throw new QueryException(ErrorCode.PIT_VERSION_NOT_READY, ErrorMessage.PIT_VERSION_NOT_READY);
        }
        int major = clusterInfo.getEffectiveVersion().getMajor();
        int minor = clusterInfo.getEffectiveVersion().getMinor();
        // PIT 需要 ES 7.10+（minor == -1 表示未解析到 minor，保守拒绝）
        boolean supported = major > 7 || (major == 7 && minor >= 10);
        if (!supported) {
            throw new QueryException(ErrorCode.PIT_NOT_SUPPORTED, ErrorMessage.PIT_NOT_SUPPORTED);
        }

        // 2. pitKeepAlive 必填
        if (pagination.getPitKeepAlive() == null || pagination.getPitKeepAlive().isBlank()) {
            throw new QueryException(ErrorCode.PIT_KEEP_ALIVE_REQUIRED, ErrorMessage.PIT_KEEP_ALIVE_REQUIRED);
        }

        // 3. pitKeepAlive 不能超过服务端上限
        String maxKeepAlive = properties.getPit().getMaxKeepAlive();
        long userMs = parseKeepAliveToMillis(pagination.getPitKeepAlive());
        long maxMs = parseKeepAliveToMillis(maxKeepAlive);
        if (userMs > maxMs) {
            throw new QueryException(ErrorCode.PIT_KEEP_ALIVE_EXCEEDED,
                    String.format(ErrorMessage.PIT_KEEP_ALIVE_EXCEEDED, pagination.getPitKeepAlive(), maxKeepAlive));
        }
    }

    /**
     * 解析 keepAlive 字符串为毫秒，支持 d/h/m/s 单位
     * 例如："5m" → 300000，"1h" → 3600000
     */
    private long parseKeepAliveToMillis(String keepAlive) {
        if (keepAlive == null || keepAlive.isBlank()) {
            throw new QueryException(ErrorCode.PIT_KEEP_ALIVE_REQUIRED, ErrorMessage.PIT_KEEP_ALIVE_REQUIRED);
        }
        String s = keepAlive.trim().toLowerCase();
        try {
            if (s.endsWith("d")) return Long.parseLong(s.substring(0, s.length() - 1)) * 86400_000L;
            if (s.endsWith("h")) return Long.parseLong(s.substring(0, s.length() - 1)) * 3600_000L;
            if (s.endsWith("m")) return Long.parseLong(s.substring(0, s.length() - 1)) * 60_000L;
            if (s.endsWith("s")) return Long.parseLong(s.substring(0, s.length() - 1)) * 1_000L;
        } catch (NumberFormatException ignored) {
        }
        throw new QueryException(ErrorCode.PIT_KEEP_ALIVE_INVALID_FORMAT,
                String.format(ErrorMessage.PIT_KEEP_ALIVE_INVALID_FORMAT, keepAlive));
    }

    /**
     * 构建 ES 搜索请求
     */
    private SearchRequest buildSearchRequest(QueryRequest request, IndexMetadata metadata, DowngradeLevel downgradeLevel) {
        // ✅ 1. 计算需要查询的索引列表（索引路由，带降级支持）
        String[] indices = indexRouteProcessor.routeWithDowngrade(metadata, request.getDateRange(), downgradeLevel);
        SearchRequest searchRequest = new SearchRequest(indices);

        // ✅ 允许查询不存在的索引（date-split 场景下部分索引可能不存在）
        if (properties.getQueryLimits().isIgnoreUnavailableIndices()) {
            searchRequest.indicesOptions(IndicesOptions.lenientExpandOpen());
            log.trace("Enabled ignoreUnavailableIndices for indices: {}", (Object) indices);
        }

        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();

        // 2. 构建查询条件
        QueryBuilder queryBuilder = queryDslBuilder.build(request.getIndex(), request.getQuery());
        sourceBuilder.query(queryBuilder);

        // ✅ 3. 添加日期范围过滤
        // strictDateFilter=true（默认）：始终过滤，防止索引内存在跨天数据
        // strictDateFilter=false：整天范围时跳过，依赖索引路由覆盖（仅在数据按事件时间严格路由时可用）
        if (request.getDateRange() != null && metadata.isDateSplit() && metadata.getDateField() != null
                && (properties.getQueryLimits().isStrictDateFilter() || needsDateFilter(request.getDateRange()))) {
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
            // PIT 模式：设置 pit（pitId 不为空时复用，为空时由 processResponse 在首次查询后 open）
            if (SearchAfterMode.PIT == pagination.getSearchAfterModeEnum()
                    && pagination.getPitId() != null && !pagination.getPitId().isBlank()) {
                searchRequest.source().pointInTimeBuilder(
                        new org.elasticsearch.search.builder.PointInTimeBuilder(pagination.getPitId())
                                .setKeepAlive(pagination.getPitKeepAlive())
                );
            }
        }

        // 5. 排序
        if (pagination.getSort() != null && !pagination.getSort().isEmpty()) {
            for (PaginationInfo.SortField sortField : pagination.getSort()) {
                SortOrder order = SimpleElasticsearchSearchConstant.SORT_ORDER_DESC.equalsIgnoreCase(sortField.getOrder()) ?
                        SortOrder.DESC : SortOrder.ASC;
                sourceBuilder.sort(sortField.getField(), order);
            }
            if (pagination.isSearchAfterPagination() && request.getCollapse() == null) {
                switch (pagination.getSearchAfterModeEnum()) {
                    case TIEBREAKER:
                        // 原有行为：追加 _id ASC 保证排序稳定性
                        sourceBuilder.sort("_id", SortOrder.ASC);
                        break;
                    case PIT:
                    case NONE:
                        // PIT 由快照保证一致性，NONE 由调用方保证唯一排序，均不追加 _id
                        break;
                }
            }
        }

        // 6. 字段投影
        if (request.getFields() != null && !request.getFields().isEmpty()) {
            sourceBuilder.fetchSource(
                    request.getFields().toArray(new String[0]),
                    null
            );
        }

        // 7. 字段折叠（去重）
        if (request.getCollapse() != null && request.getCollapse().getField() != null) {
            org.elasticsearch.search.collapse.CollapseBuilder collapseBuilder =
                    new org.elasticsearch.search.collapse.CollapseBuilder(request.getCollapse().getField());

            if (request.getCollapse().getMaxConcurrentGroupSearches() != null) {
                collapseBuilder.setMaxConcurrentGroupRequests(request.getCollapse().getMaxConcurrentGroupSearches());
            }

            sourceBuilder.collapse(collapseBuilder);
        }

        // 8. 是否返回 _score
        if (!properties.getApi().isIncludeScore()) {
            sourceBuilder.trackScores(false);
        }

        searchRequest.source(sourceBuilder);

        return searchRequest;
    }

    /**
     * PIT 首次 open 或复用已有 pitId
     * 首次请求（pitId 为空）：调用 ES open PIT API，返回新 pitId
     * 后续翻页（pitId 不为空）：直接复用，keepAlive 已在 buildSearchRequest 中续期
     */
    private String openOrRenewPit(QueryRequest request, PaginationInfo pagination, SearchResponse searchResponse) {
        if (pagination.getPitId() != null && !pagination.getPitId().isBlank()) {
            // 复用已有 PIT，pitId 不变
            return pagination.getPitId();
        }
        // 首次：open PIT
        try {
            String datasourceKey = routeResolver.resolveDataSource(request.getIndex());
            RestHighLevelClient client = registry.getHighLevelClient(datasourceKey);
            // 使用低级 API 调用 open PIT（高级 API 在部分版本不支持）
            String keepAlive = pagination.getPitKeepAlive();
            org.elasticsearch.client.Request pitRequest = new org.elasticsearch.client.Request(
                    "POST", "/" + request.getIndex() + "/_pit?keep_alive=" + keepAlive);
            org.elasticsearch.client.Response pitResponse = client.getLowLevelClient().performRequest(pitRequest);
            String body = new String(pitResponse.getEntity().getContent().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8);
            // 解析 {"id":"..."}
            return OBJECT_MAPPER.readTree(body).path("id").asText();
        } catch (Exception e) {
            log.warn("Failed to open PIT for index [{}], PIT mode degraded silently: {}", request.getIndex(), e.getMessage());
            return null;
        }
    }

    /**
     * 静默关闭 PIT，失败只打 warn 不抛异常（不影响业务响应）
     */
    private void closePitQuietly(String pitId, String index) {
        if (pitId == null || pitId.isBlank()) {
            return;
        }
        try {
            String datasourceKey = routeResolver.resolveDataSource(index);
            RestHighLevelClient client = registry.getHighLevelClient(datasourceKey);
            org.elasticsearch.client.Request closeRequest = new org.elasticsearch.client.Request("DELETE", "/_pit");
            closeRequest.setJsonEntity("{\"id\":\"" + pitId + "\"}");
            client.getLowLevelClient().performRequest(closeRequest);
            log.debug("PIT closed: index={}", index);
        } catch (Exception e) {
            log.warn("Failed to close PIT [{}] for index [{}]: {}", pitId, index, e.getMessage());
        }
    }

    /**
     * 判断是否需要额外的日期过滤
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
        }

        builder.items(items);

        // 4. 分页结果
        boolean hasMore = items.size() == pagination.getSize();
        QueryResponse.PaginationResult paginationResult = QueryResponse.PaginationResult.builder()
                .type(pagination.getType())
                .hasMore(hasMore)
                .build();

        // search_after 的下一页参数，仅在 hasMore=true 时返回
        if (pagination.isSearchAfterPagination() && hasMore) {
            SearchHit[] hits = searchResponse.getHits().getHits();
            if (hits.length > 0 && hits[hits.length - 1].getSortValues() != null
                    && hits[hits.length - 1].getSortValues().length > 0) {
                paginationResult.setNextSearchAfter(Arrays.asList(hits[hits.length - 1].getSortValues()));
            }
        }

        // PIT 生命周期管理
        if (pagination.isSearchAfterPagination() && SearchAfterMode.PIT == pagination.getSearchAfterModeEnum()) {
            if (hasMore) {
                // 有更多数据：open（首次）或复用（后续）PIT，将 pitId 写入响应
                String pitId = openOrRenewPit(request, pagination, searchResponse);
                paginationResult.setPitId(pitId);
            } else {
                // 最后一页：自动 close PIT，释放 ES 资源
                closePitQuietly(pagination.getPitId(), request.getIndex());
            }
        }

        builder.pagination(paginationResult);

        return builder.build();
    }
}
