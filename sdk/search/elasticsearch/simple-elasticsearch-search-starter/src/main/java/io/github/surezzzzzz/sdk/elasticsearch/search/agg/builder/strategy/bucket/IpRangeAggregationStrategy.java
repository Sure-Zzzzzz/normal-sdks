package io.github.surezzzzzz.sdk.elasticsearch.search.agg.builder.strategy.bucket;

import io.github.surezzzzzz.sdk.elasticsearch.search.agg.builder.strategy.AggregationStrategy;
import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.AggDefinition;
import io.github.surezzzzzz.sdk.elasticsearch.search.annotation.SimpleElasticsearchSearchComponent;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.model.IndexMetadata;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.range.IpRangeAggregationBuilder;

import java.util.Map;

/**
 * ip_range 聚合策略（IP 范围分组）
 * 按 IP 地址范围分组，支持 CIDR 表示法（如 10.0.0.0/8）。
 *
 * @author surezzzzzz
 */
@SimpleElasticsearchSearchComponent
public class IpRangeAggregationStrategy implements AggregationStrategy {

    @Override
    public AggregationBuilder build(AggDefinition definition, IndexMetadata metadata,
                                    Map<String, Object> after) {
        IpRangeAggregationBuilder ipRangeAgg = AggregationBuilders.ipRange(definition.getName())
                .field(definition.getField());

        if (definition.getRanges() != null) {
            for (AggDefinition.Range range : definition.getRanges()) {
                String from = range.getFrom() != null ? range.getFrom().toString() : null;
                String to = range.getTo() != null ? range.getTo().toString() : null;
                if (from != null && to != null) {
                    ipRangeAgg.addRange(range.getKey(), from, to);
                } else if (from != null) {
                    ipRangeAgg.addUnboundedFrom(range.getKey(), from);
                } else if (to != null) {
                    ipRangeAgg.addUnboundedTo(range.getKey(), to);
                }
            }
        }

        return ipRangeAgg;
    }
}
