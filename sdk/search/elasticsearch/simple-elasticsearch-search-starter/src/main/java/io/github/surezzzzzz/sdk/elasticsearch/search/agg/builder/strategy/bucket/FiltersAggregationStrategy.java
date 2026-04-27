package io.github.surezzzzzz.sdk.elasticsearch.search.agg.builder.strategy.bucket;

import io.github.surezzzzzz.sdk.elasticsearch.search.agg.builder.strategy.AggregationStrategy;
import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.AggDefinition;
import io.github.surezzzzzz.sdk.elasticsearch.search.annotation.SimpleElasticsearchSearchComponent;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.ErrorCode;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.elasticsearch.search.exception.AggregationException;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.model.IndexMetadata;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.builder.QueryDslBuilder;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryCondition;
import lombok.RequiredArgsConstructor;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.filter.FiltersAggregator;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * filters 聚合策略（多命名过滤器聚合）
 * 同时定义多个命名过滤器，每个过滤器产生一个独立的 bucket，适合对比分析。
 *
 * @author surezzzzzz
 */
@SimpleElasticsearchSearchComponent
@RequiredArgsConstructor
public class FiltersAggregationStrategy implements AggregationStrategy {

    private final QueryDslBuilder queryDslBuilder;

    @Override
    public AggregationBuilder build(AggDefinition definition, IndexMetadata metadata,
                                    Map<String, Object> after) {
        if (CollectionUtils.isEmpty(definition.getFilters())) {
            throw new AggregationException(ErrorCode.AGG_FILTERS_REQUIRED,
                    String.format(ErrorMessage.AGG_FILTERS_REQUIRED, definition.getName()));
        }
        List<FiltersAggregator.KeyedFilter> keyedFilters = new ArrayList<>();
        for (Map.Entry<String, QueryCondition> entry : definition.getFilters().entrySet()) {
            QueryBuilder queryBuilder = queryDslBuilder.build(metadata, entry.getValue());
            keyedFilters.add(new FiltersAggregator.KeyedFilter(entry.getKey(), queryBuilder));
        }
        return AggregationBuilders.filters(definition.getName(),
                keyedFilters.toArray(new FiltersAggregator.KeyedFilter[0]));
    }
}
