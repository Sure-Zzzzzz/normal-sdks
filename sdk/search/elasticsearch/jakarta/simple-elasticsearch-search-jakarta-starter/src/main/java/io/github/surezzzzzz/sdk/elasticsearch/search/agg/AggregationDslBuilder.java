package io.github.surezzzzzz.sdk.elasticsearch.search.agg;

import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.AggDefinition;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.model.FieldMetadata;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.model.ResolvedIndex;

import java.util.List;
import java.util.Map;

/**
 * 聚合 JSON 扩展口；after 按聚合名称独立绑定。
 */
public interface AggregationDslBuilder {
    /**
     * 将结构化定义编译为校验后的协议对象。
     */
    Map<String, Object> build(List<AggDefinition> definitions, Map<String, Map<String, Object>> after,
                              ResolvedIndex target, Map<String, FieldMetadata> fields);
}
