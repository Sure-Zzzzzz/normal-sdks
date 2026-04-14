package io.github.surezzzzzz.sdk.elasticsearch.search.query.pagination;

import io.github.surezzzzzz.sdk.elasticsearch.search.annotation.SimpleElasticsearchSearchComponent;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.PaginationInfo;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryRequest;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.builder.SearchSourceBuilder;

/**
 * offset 分页策略（from + size）
 *
 * @author surezzzzzz
 */
@SimpleElasticsearchSearchComponent
public class OffsetPaginationStrategy implements PaginationStrategy {

    @Override
    public void applyToRequest(SearchSourceBuilder sourceBuilder,
                               SearchRequest searchRequest,
                               PaginationInfo pagination,
                               QueryRequest request) {
        int from = (pagination.getPage() - 1) * pagination.getSize();
        sourceBuilder.from(from);
        sourceBuilder.size(pagination.getSize());
        applySortFields(sourceBuilder, pagination);
    }

    @Override
    public QueryResponse.PaginationResult buildResult(SearchResponse searchResponse,
                                                      PaginationInfo pagination) {
        boolean hasMore = searchResponse.getHits().getHits().length == pagination.getSize();
        return QueryResponse.PaginationResult.builder()
                .type(pagination.getType())
                .hasMore(hasMore)
                .build();
    }
}
