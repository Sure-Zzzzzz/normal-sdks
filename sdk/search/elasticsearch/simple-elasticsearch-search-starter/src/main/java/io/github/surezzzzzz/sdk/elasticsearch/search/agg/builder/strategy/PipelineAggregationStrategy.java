package io.github.surezzzzzz.sdk.elasticsearch.search.agg.builder.strategy;

import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.PipelineAggDefinition;
import org.elasticsearch.search.aggregations.PipelineAggregationBuilder;

/**
 * Pipeline 聚合策略接口
 * 实现类通过 {@link AggregationStrategyRegistry} 注册，
 * 由 {@link io.github.surezzzzzz.sdk.elasticsearch.search.agg.builder.AggregationDslBuilder}
 * 在构建 bucket 聚合时调用。
 *
 * @author surezzzzzz
 */
public interface PipelineAggregationStrategy {

    /**
     * 构建 ES PipelineAggregationBuilder
     *
     * @param definition pipeline 聚合定义
     * @return ES PipelineAggregationBuilder
     */
    PipelineAggregationBuilder build(PipelineAggDefinition definition);
}
