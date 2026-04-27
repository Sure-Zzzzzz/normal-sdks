package io.github.surezzzzzz.sdk.elasticsearch.search.agg.builder.strategy.metric;

import io.github.surezzzzzz.sdk.elasticsearch.search.agg.builder.strategy.AggregationStrategy;
import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.AggDefinition;
import io.github.surezzzzzz.sdk.elasticsearch.search.annotation.SimpleElasticsearchSearchComponent;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.model.IndexMetadata;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.metrics.PercentilesAggregationBuilder;
import org.springframework.util.CollectionUtils;

import java.util.Map;

/**
 * percentiles 聚合策略
 * 计算数值字段的百分位数，如 P50/P95/P99 等。
 *
 * @author surezzzzzz
 */
@SimpleElasticsearchSearchComponent
public class PercentilesAggregationStrategy implements AggregationStrategy {

    @Override
    public AggregationBuilder build(AggDefinition definition, IndexMetadata metadata,
                                    Map<String, Object> after) {
        PercentilesAggregationBuilder builder =
                AggregationBuilders.percentiles(definition.getName()).field(definition.getField());
        if (!CollectionUtils.isEmpty(definition.getPercents())) {
            builder.percentiles(definition.getPercents().stream().mapToDouble(Double::doubleValue).toArray());
        }
        return builder;
    }
}
