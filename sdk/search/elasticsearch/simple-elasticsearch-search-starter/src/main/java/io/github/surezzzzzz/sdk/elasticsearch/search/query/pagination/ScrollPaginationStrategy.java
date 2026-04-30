package io.github.surezzzzzz.sdk.elasticsearch.search.query.pagination;

import io.github.surezzzzzz.sdk.elasticsearch.search.annotation.SimpleElasticsearchSearchComponent;
import io.github.surezzzzzz.sdk.elasticsearch.search.configuration.SimpleElasticsearchSearchProperties;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.ErrorCode;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.elasticsearch.search.exception.QueryException;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.PaginationInfo;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryRequest;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryResponse;
import io.github.surezzzzzz.sdk.elasticsearch.search.support.TimeRangeHelper;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;

/**
 * scroll 分页策略
 *
 * <p>适用于全量遍历/数据导出场景，具备快照一致性，不支持跳页。
 * scroll 上下文生命周期由策略自动管理：最后一页（hasMore=false）由 QueryExecutor 自动清除，
 * 中途放弃由 scrollTtl 超时兜底。
 * 兼容 ES 1.x+，使用低级 API 调用，与 PIT 实现方式一致。
 *
 * @author surezzzzzz
 */
@SimpleElasticsearchSearchComponent
public class ScrollPaginationStrategy implements PaginationStrategy {

    @Autowired
    private SimpleElasticsearchSearchProperties properties;

    @Override
    public void validate(QueryRequest request, PaginationInfo pagination) {
        // 1. scrollTtl 必填
        if (!StringUtils.hasText(pagination.getScrollTtl())) {
            throw new QueryException(ErrorCode.SCROLL_TTL_REQUIRED, ErrorMessage.SCROLL_TTL_REQUIRED);
        }

        // 2. scrollTtl 格式校验 + 上限校验
        try {
            long ttlMs = TimeRangeHelper.parseToMillis(pagination.getScrollTtl());
            String maxTtl = properties.getScroll().getMaxTtl();
            if (StringUtils.hasText(maxTtl)) {
                long maxMs = TimeRangeHelper.parseToMillis(maxTtl);
                if (ttlMs > maxMs) {
                    throw new QueryException(ErrorCode.SCROLL_TTL_EXCEEDED,
                            String.format(ErrorMessage.SCROLL_TTL_EXCEEDED,
                                    pagination.getScrollTtl(), maxTtl));
                }
            }
        } catch (QueryException e) {
            throw e;
        } catch (Exception e) {
            throw new QueryException(ErrorCode.SCROLL_TTL_INVALID_FORMAT,
                    String.format(ErrorMessage.SCROLL_TTL_INVALID_FORMAT, pagination.getScrollTtl()));
        }

        // 3. 第一页必须有 sort，后续翻页（带 scrollId）不需要
        if (!StringUtils.hasText(pagination.getScrollId())
                && (pagination.getSort() == null || pagination.getSort().isEmpty())) {
            throw new QueryException(ErrorCode.SCROLL_SORT_REQUIRED, ErrorMessage.SCROLL_SORT_REQUIRED);
        }

        // 4. 不支持与 collapse 同时使用
        if (request.getCollapse() != null) {
            throw new QueryException(ErrorCode.SCROLL_COLLAPSE_NOT_SUPPORTED,
                    ErrorMessage.SCROLL_COLLAPSE_NOT_SUPPORTED);
        }
    }

    @Override
    public void applyToRequest(SearchSourceBuilder sourceBuilder,
                               SearchRequest searchRequest,
                               PaginationInfo pagination,
                               QueryRequest request) {
        if (StringUtils.hasText(pagination.getScrollId())) {
            // 后续翻页：不使用 SearchRequest，改用 scroll API（在 QueryExecutor 中特殊处理）
            return;
        }

        // 第一页：正常构建 SearchRequest，附加 scroll 参数
        sourceBuilder.size(pagination.getSize());
        applySortFields(sourceBuilder, pagination);
        searchRequest.scroll(pagination.getScrollTtl());
    }

    @Override
    public QueryResponse.PaginationResult buildResult(SearchResponse searchResponse,
                                                      PaginationInfo pagination,
                                                      QueryRequest request) {
        SearchHit[] hits = searchResponse.getHits().getHits();
        boolean hasMore = hits.length == pagination.getSize();

        QueryResponse.PaginationResult.PaginationResultBuilder builder = QueryResponse.PaginationResult.builder()
                .type(pagination.getType())
                .hasMore(hasMore);

        if (hasMore) {
            builder.scrollId(searchResponse.getScrollId());
        }

        return builder.build();
    }
}
