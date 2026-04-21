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
 * terms 聚合策略（分组聚合，类似 SQL GROUP BY）
 *
 * @author surezzzzzz
 */
@SimpleElasticsearchSearchComponent
public class TermsAggregationStrategy implements AggregationStrategy {

    @Override
    public AggregationBuilder build(AggDefinition definition, IndexMetadata metadata,
                                    Map<String, Object> after) {
        int size = definition.getSize() != null
                ? definition.getSize()
                : SimpleElasticsearchSearchConstant.DEFAULT_TERMS_SIZE;
        return AggregationBuilders.terms(definition.getName())
                .field(definition.getField())
                .size(size);
    }
}
