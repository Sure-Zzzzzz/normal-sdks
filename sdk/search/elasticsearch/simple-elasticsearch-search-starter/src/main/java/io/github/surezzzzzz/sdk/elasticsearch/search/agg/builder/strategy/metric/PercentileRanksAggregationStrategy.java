package io.github.surezzzzzz.sdk.elasticsearch.search.agg.builder.strategy.metric;

import io.github.surezzzzzz.sdk.elasticsearch.search.agg.builder.strategy.AggregationStrategy;
import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.AggDefinition;
import io.github.surezzzzzz.sdk.elasticsearch.search.annotation.SimpleElasticsearchSearchComponent;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.ErrorCode;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.elasticsearch.search.exception.AggregationException;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.model.IndexMetadata;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.springframework.util.CollectionUtils;

import java.util.Map;

/**
 * percentile_ranks 聚合策略
 * 计算指定值在数值字段中的百分位排名，如"1000 元以下的订单占比"。
 *
 * @author surezzzzzz
 */
@SimpleElasticsearchSearchComponent
public class PercentileRanksAggregationStrategy implements AggregationStrategy {

    @Override
    public AggregationBuilder build(AggDefinition definition, IndexMetadata metadata,
                                    Map<String, Object> after) {
        if (CollectionUtils.isEmpty(definition.getValues())) {
            throw new AggregationException(ErrorCode.AGG_PERCENTILE_RANKS_VALUES_REQUIRED,
                    String.format(ErrorMessage.AGG_PERCENTILE_RANKS_VALUES_REQUIRED, definition.getName()));
        }
        return AggregationBuilders.percentileRanks(definition.getName(),
                        definition.getValues().stream().mapToDouble(Double::doubleValue).toArray())
                .field(definition.getField());
    }
}
