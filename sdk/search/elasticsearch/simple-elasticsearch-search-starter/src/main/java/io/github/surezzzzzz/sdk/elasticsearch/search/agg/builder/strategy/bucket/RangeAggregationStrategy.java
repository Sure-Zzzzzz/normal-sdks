package io.github.surezzzzzz.sdk.elasticsearch.search.agg.builder.strategy.bucket;

import io.github.surezzzzzz.sdk.elasticsearch.search.agg.builder.strategy.AggregationStrategy;
import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.AggDefinition;
import io.github.surezzzzzz.sdk.elasticsearch.search.annotation.SimpleElasticsearchSearchComponent;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.model.IndexMetadata;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.range.RangeAggregationBuilder;

import java.util.Map;

/**
 * range 聚合策略（数值范围分组）
 *
 * @author surezzzzzz
 */
@SimpleElasticsearchSearchComponent
public class RangeAggregationStrategy implements AggregationStrategy {

    @Override
    public AggregationBuilder build(AggDefinition definition, IndexMetadata metadata,
                                    Map<String, Object> after) {
        RangeAggregationBuilder rangeAgg = AggregationBuilders.range(definition.getName())
                .field(definition.getField());

        if (definition.getRanges() != null) {
            for (AggDefinition.Range range : definition.getRanges()) {
                if (range.getFrom() != null && range.getTo() != null) {
                    rangeAgg.addRange(range.getKey(),
                            ((Number) range.getFrom()).doubleValue(),
                            ((Number) range.getTo()).doubleValue());
                } else if (range.getFrom() != null) {
                    rangeAgg.addUnboundedFrom(range.getKey(), ((Number) range.getFrom()).doubleValue());
                } else if (range.getTo() != null) {
                    rangeAgg.addUnboundedTo(range.getKey(), ((Number) range.getTo()).doubleValue());
                }
            }
        }

        return rangeAgg;
    }
}
