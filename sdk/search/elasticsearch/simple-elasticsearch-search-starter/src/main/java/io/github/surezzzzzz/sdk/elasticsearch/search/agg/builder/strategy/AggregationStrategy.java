package io.github.surezzzzzz.sdk.elasticsearch.search.agg.builder.strategy;

import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.AggDefinition;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.model.IndexMetadata;
import org.elasticsearch.search.aggregations.AggregationBuilder;

import java.util.Map;

/**
 * 聚合策略接口
 * 每种聚合类型对应一个实现，通过 {@link AggregationStrategyRegistry} 注册和查找
 *
 * @author surezzzzzz
 */
public interface AggregationStrategy {

    /**
     * 构建 ES AggregationBuilder
     *
     * @param definition 聚合定义
     * @param metadata   索引元数据
     * @param after      composite 翻页游标（非 composite 聚合传 null）
     * @return ES AggregationBuilder
     */
    AggregationBuilder build(AggDefinition definition, IndexMetadata metadata,
                             Map<String, Object> after);
}
