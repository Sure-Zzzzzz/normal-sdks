package io.github.surezzzzzz.sdk.elasticsearch.route.model;

import lombok.Builder;
import lombok.Getter;
import org.elasticsearch.action.search.SearchResponse;

/**
 * 底层 _search 执行结果
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public class LowLevelSearchResult {

    private final SearchResponse searchResponse;
    private final String rawResponseBody;
    private final boolean containsAggregations;
    private final boolean rawAggregationResponse;
}
