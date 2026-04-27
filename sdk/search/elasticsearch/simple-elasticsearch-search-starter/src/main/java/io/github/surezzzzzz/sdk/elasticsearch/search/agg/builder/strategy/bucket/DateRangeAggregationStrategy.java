package io.github.surezzzzzz.sdk.elasticsearch.search.agg.builder.strategy.bucket;

import io.github.surezzzzzz.sdk.elasticsearch.search.agg.builder.strategy.AggregationStrategy;
import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.AggDefinition;
import io.github.surezzzzzz.sdk.elasticsearch.search.annotation.SimpleElasticsearchSearchComponent;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.model.IndexMetadata;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.range.DateRangeAggregationBuilder;

import java.util.Map;

/**
 * date_range 聚合策略（日期范围分组）
 * 按日期范围分组，支持相对时间表达式（如 now-1M、now-1y）。
 *
 * @author surezzzzzz
 */
@SimpleElasticsearchSearchComponent
public class DateRangeAggregationStrategy implements AggregationStrategy {

    @Override
    public AggregationBuilder build(AggDefinition definition, IndexMetadata metadata,
                                    Map<String, Object> after) {
        DateRangeAggregationBuilder dateRangeAgg = AggregationBuilders.dateRange(definition.getName())
                .field(definition.getField());

        if (definition.getRanges() != null) {
            for (AggDefinition.Range range : definition.getRanges()) {
                String from = range.getFrom() != null ? range.getFrom().toString() : null;
                String to = range.getTo() != null ? range.getTo().toString() : null;
                if (from != null && to != null) {
                    dateRangeAgg.addRange(range.getKey(), from, to);
                } else if (from != null) {
                    dateRangeAgg.addUnboundedFrom(range.getKey(), from);
                } else if (to != null) {
                    dateRangeAgg.addUnboundedTo(range.getKey(), to);
                }
            }
        }

        return dateRangeAgg;
    }
}
