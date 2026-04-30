package io.github.surezzzzzz.sdk.elasticsearch.search.query.pagination;

import io.github.surezzzzzz.sdk.elasticsearch.search.annotation.SimpleElasticsearchSearchComponent;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.PaginationInfo;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryRequest;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import java.util.Arrays;

/**
 * search_after + none 分页策略
 *
 * <p>不追加任何 tiebreaker，适合排序字段本身已唯一的场景（如按唯一 ID 排序）。
 * 调用方需自行保证排序字段唯一性，否则可能出现翻页丢数据。
 *
 * @author surezzzzzz
 */
@SimpleElasticsearchSearchComponent
public class NonePaginationStrategy implements PaginationStrategy {

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
