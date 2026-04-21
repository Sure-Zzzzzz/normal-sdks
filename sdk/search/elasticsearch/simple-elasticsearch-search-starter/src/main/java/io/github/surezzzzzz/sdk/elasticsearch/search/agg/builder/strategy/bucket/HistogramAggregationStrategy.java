package io.github.surezzzzzz.sdk.elasticsearch.search.agg.builder.strategy.bucket;

import io.github.surezzzzzz.sdk.elasticsearch.search.agg.builder.strategy.AggregationStrategy;
import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.AggDefinition;
import io.github.surezzzzzz.sdk.elasticsearch.search.annotation.SimpleElasticsearchSearchComponent;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.SimpleElasticsearchSearchConstant;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.model.IndexMetadata;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;

import java.util.Map;

/**
 * histogram 聚合策略（按数值间隔分组）
 *
 * @author surezzzzzz
 */
@SimpleElasticsearchSearchComponent
public class HistogramAggregationStrategy implements AggregationStrategy {

    @Override
    public AggregationBuilder build(AggDefinition definition, IndexMetadata metadata,
                                    Map<String, Object> after) {
        double interval = definition.getSize() != null
                ? definition.getSize()
                : SimpleElasticsearchSearchConstant.DEFAULT_HISTOGRAM_INTERVAL;
        return AggregationBuilders.histogram(definition.getName())
                .field(definition.getField())
                .interval(interval);
    }
}
