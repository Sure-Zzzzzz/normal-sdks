package io.github.surezzzzzz.sdk.elasticsearch.search.agg.builder.strategy.bucket;

import io.github.surezzzzzz.sdk.elasticsearch.search.agg.builder.strategy.AggregationStrategy;
import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.AggDefinition;
import io.github.surezzzzzz.sdk.elasticsearch.search.annotation.SimpleElasticsearchSearchComponent;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.model.IndexMetadata;
import io.github.surezzzzzz.sdk.elasticsearch.search.support.DateIntervalHelper;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;

import java.util.Map;

/**
 * date_histogram 聚合策略（按时间间隔分组）
 *
 * @author surezzzzzz
 */
@SimpleElasticsearchSearchComponent
public class DateHistogramAggregationStrategy implements AggregationStrategy {

    @Override
    public AggregationBuilder build(AggDefinition definition, IndexMetadata metadata,
                                    Map<String, Object> after) {
        return AggregationBuilders.dateHistogram(definition.getName())
                .field(definition.getField())
                .dateHistogramInterval(DateIntervalHelper.parse(definition.getInterval()));
    }
}
