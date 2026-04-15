package io.github.surezzzzzz.sdk.elasticsearch.search.query.pagination;

import io.github.surezzzzzz.sdk.elasticsearch.route.model.ClusterInfo;
import io.github.surezzzzzz.sdk.elasticsearch.route.registry.SimpleElasticsearchRouteRegistry;
import io.github.surezzzzzz.sdk.elasticsearch.route.resolver.RouteResolver;
import io.github.surezzzzzz.sdk.elasticsearch.search.annotation.SimpleElasticsearchSearchComponent;
import io.github.surezzzzzz.sdk.elasticsearch.search.configuration.SimpleElasticsearchSearchProperties;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.ErrorCode;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.elasticsearch.search.exception.QueryException;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.PaginationInfo;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryRequest;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryResponse;
import io.github.surezzzzzz.sdk.elasticsearch.search.support.TimeRangeHelper;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * search_after + PIT 分页策略
 *
 * <p>使用 Point In Time 快照翻页，不追加 _id，适合内存敏感场景。
 * PIT 生命周期由策略自动管理：首次请求自动 open，最后一页自动 close，中途放弃由 keepAlive 超时兜底。
 * 需要 ES 7.10+，版本校验在 {@code QueryExecutorImpl.validateRequest} 中完成。
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleElasticsearchSearchComponent
public class PitPaginationStrategy implements PaginationStrategy {

    private static final com.fasterxml.jackson.databind.ObjectMapper OBJECT_MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper();

    @Autowired
    private SimpleElasticsearchRouteRegistry registry;

    @Autowired
    private RouteResolver routeResolver;

    @Autowired
    private SimpleElasticsearchSearchProperties properties;

    @Override
    public void validate(QueryRequest request, PaginationInfo pagination) {
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
        if (!org.springframework.util.StringUtils.hasText(pagination.getPitKeepAlive())) {
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

    @Override
    public void applyToRequest(SearchSourceBuilder sourceBuilder,
                               SearchRequest searchRequest,
                               PaginationInfo pagination,
                               QueryRequest request) {
        sourceBuilder.size(pagination.getSize());
        if (pagination.getSearchAfter() != null) {
            sourceBuilder.searchAfter(pagination.getSearchAfter().toArray());
        }
        applySortFields(sourceBuilder, pagination);

        // 复用已有 PIT（续期 keepAlive）
        if (StringUtils.hasText(pagination.getPitId())) {
            searchRequest.source().pointInTimeBuilder(
                    new org.elasticsearch.search.builder.PointInTimeBuilder(pagination.getPitId())
                            .setKeepAlive(pagination.getPitKeepAlive())
            );
        }
    }

    @Override
    public QueryResponse.PaginationResult buildResult(SearchResponse searchResponse,
                                                      PaginationInfo pagination) {
        // 此方法不直接调用，PIT 需要 request 上下文，由 buildResultWithRequest 处理
        throw new UnsupportedOperationException("PitPaginationStrategy requires request context, use buildResultWithRequest");
    }

    /**
     * 构建 PIT 分页结果，同时管理 PIT 生命周期
     */
    public QueryResponse.PaginationResult buildResultWithRequest(SearchResponse searchResponse,
                                                                 PaginationInfo pagination,
                                                                 QueryRequest request) {
        SearchHit[] hits = searchResponse.getHits().getHits();
        boolean hasMore = hits.length == pagination.getSize();

        QueryResponse.PaginationResult.PaginationResultBuilder builder = QueryResponse.PaginationResult.builder()
                .type(pagination.getType())
                .hasMore(hasMore);

        if (hasMore) {
            // 有更多数据：open（首次）或复用 PIT，将 pitId 写入响应
            String pitId = openOrRenewPit(request, pagination);
            builder.pitId(pitId);
            if (hits.length > 0 && hits[hits.length - 1].getSortValues().length > 0) {
                builder.nextSearchAfter(Arrays.asList(hits[hits.length - 1].getSortValues()));
            }
        } else {
            // 最后一页：自动 close PIT
            closePitQuietly(pagination.getPitId(), request.getIndex());
        }

        return builder.build();
    }

    /**
     * 解析 keepAlive 字符串为毫秒，委托给 TimeRangeHelper
     */
    public long parseKeepAliveToMillis(String keepAlive) {
        if (!StringUtils.hasText(keepAlive)) {
            throw new QueryException(ErrorCode.PIT_KEEP_ALIVE_REQUIRED, ErrorMessage.PIT_KEEP_ALIVE_REQUIRED);
        }
        return TimeRangeHelper.parseToMillis(keepAlive);
    }

    private String openOrRenewPit(QueryRequest request, PaginationInfo pagination) {
        if (StringUtils.hasText(pagination.getPitId())) {
            return pagination.getPitId();
        }
        try {
            String datasourceKey = routeResolver.resolveDataSource(request.getIndex());
            RestHighLevelClient client = registry.getHighLevelClient(datasourceKey);
            String keepAlive = pagination.getPitKeepAlive();
            org.elasticsearch.client.Request pitRequest = new org.elasticsearch.client.Request(
                    "POST", "/" + request.getIndex() + "/_pit?keep_alive=" + keepAlive);
            org.elasticsearch.client.Response pitResponse = client.getLowLevelClient().performRequest(pitRequest);
            InputStream inputStream = pitResponse.getEntity().getContent();
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int len;
            while ((len = inputStream.read(chunk)) != -1) {
                buffer.write(chunk, 0, len);
            }
            String body = buffer.toString(StandardCharsets.UTF_8.name());
            return OBJECT_MAPPER.readTree(body).path("id").asText();
        } catch (Exception e) {
            log.warn("Failed to open PIT for index [{}]: {}", request.getIndex(), e.getMessage());
            return null;
        }
    }

    private void closePitQuietly(String pitId, String index) {
        if (!StringUtils.hasText(pitId)) {
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
}
