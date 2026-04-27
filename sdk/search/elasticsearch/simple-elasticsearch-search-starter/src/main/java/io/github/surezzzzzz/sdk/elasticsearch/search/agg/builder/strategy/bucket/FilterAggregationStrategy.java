package io.github.surezzzzzz.sdk.elasticsearch.search.agg.builder.strategy.bucket;

import io.github.surezzzzzz.sdk.elasticsearch.search.agg.builder.strategy.AggregationStrategy;
import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.AggDefinition;
import io.github.surezzzzzz.sdk.elasticsearch.search.annotation.SimpleElasticsearchSearchComponent;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.ErrorCode;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.elasticsearch.search.exception.AggregationException;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.model.IndexMetadata;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.builder.QueryDslBuilder;
import lombok.RequiredArgsConstructor;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;

import java.util.Map;

/**
 * filter 聚合策略（单过滤器聚合）
 * 对文档集应用单个过滤条件，只统计满足条件的文档，通常配合嵌套 metrics 聚合使用。
 *
 * @author surezzzzzz
 */
@SimpleElasticsearchSearchComponent
@RequiredArgsConstructor
public class FilterAggregationStrategy implements AggregationStrategy {

    private final QueryDslBuilder queryDslBuilder;

    @Override
    public AggregationBuilder build(AggDefinition definition, IndexMetadata metadata,
                                    Map<String, Object> after) {
        if (definition.getQuery() == null) {
            throw new AggregationException(ErrorCode.AGG_FILTER_QUERY_REQUIRED,
                    String.format(ErrorMessage.AGG_FILTER_QUERY_REQUIRED, definition.getName()));
        }
        return AggregationBuilders.filter(definition.getName(),
                queryDslBuilder.build(metadata, definition.getQuery()));
    }
}
