package io.github.surezzzzzz.sdk.elasticsearch.search.agg.builder.strategy.metric;

import io.github.surezzzzzz.sdk.elasticsearch.search.agg.builder.strategy.AggregationStrategy;
import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.AggDefinition;
import io.github.surezzzzzz.sdk.elasticsearch.search.annotation.SimpleElasticsearchSearchComponent;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.model.IndexMetadata;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;

import java.util.Map;

/**
 * sum 聚合策略
 *
 * @author surezzzzzz
 */
@SimpleElasticsearchSearchComponent
public class SumAggregationStrategy implements AggregationStrategy {

    @Override
    public AggregationBuilder build(AggDefinition definition, IndexMetadata metadata,
                                    Map<String, Object> after) {
        return AggregationBuilders.sum(definition.getName()).field(definition.getField());
    }
}
