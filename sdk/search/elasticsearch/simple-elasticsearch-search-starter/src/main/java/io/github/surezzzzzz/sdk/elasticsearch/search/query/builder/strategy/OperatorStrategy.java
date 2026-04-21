package io.github.surezzzzzz.sdk.elasticsearch.search.query.builder.strategy;

import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.model.FieldMetadata;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryCondition;
import org.elasticsearch.index.query.QueryBuilder;

/**
 * 查询操作符策略接口
 * 每种操作符对应一个实现，通过 {@link OperatorStrategyRegistry} 注册和查找
 *
 * @author surezzzzzz
 */
public interface OperatorStrategy {

    /**
     * 构建 ES QueryBuilder
     *
     * @param fieldName     字段名
     * @param condition     查询条件（含 value/values）
     * @param fieldMetadata 字段元数据（用于类型判断）
     * @return ES QueryBuilder
     */
    QueryBuilder build(String fieldName, QueryCondition condition, FieldMetadata fieldMetadata);
}
