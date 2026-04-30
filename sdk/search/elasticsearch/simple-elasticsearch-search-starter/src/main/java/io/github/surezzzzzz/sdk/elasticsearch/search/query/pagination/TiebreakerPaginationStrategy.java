package io.github.surezzzzzz.sdk.elasticsearch.search.query.pagination;

import io.github.surezzzzzz.sdk.elasticsearch.search.annotation.SimpleElasticsearchSearchComponent;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.SimpleElasticsearchSearchConstant;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.PaginationInfo;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryRequest;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;

import java.util.Arrays;

/**
 * search_after + tiebreaker 分页策略
 *
 * <p>自动追加 {@code _id ASC} 作为 tiebreaker，保证非唯一排序字段下翻页稳定。
 * 注意：_id 排序会触发 ES fielddata，内存较小的集群慎用，可改用 {@link NonePaginationStrategy} 或 {@link PitPaginationStrategy}。
 *
 * @author surezzzzzz
 */
@SimpleElasticsearchSearchComponent
public class TiebreakerPaginationStrategy implements PaginationStrategy {

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
        // collapse 场景不追加 _id（ES 不允许 collapse + search_after 同时使用多个排序字段）
        if (request.getCollapse() == null) {
            sourceBuilder.sort(SimpleElasticsearchSearchConstant.ES_FIELD_ID, SortOrder.ASC);
        }
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

        if (hasMore && hits.length > 0 && hits[hits.length - 1].getSortValues().length > 0) {
            builder.nextSearchAfter(Arrays.asList(hits[hits.length - 1].getSortValues()));
        }

        return builder.build();
    }
}
