package io.github.surezzzzzz.sdk.elasticsearch.search.query.builder.strategy.operator;

import io.github.surezzzzzz.sdk.elasticsearch.search.annotation.SimpleElasticsearchSearchComponent;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.model.FieldMetadata;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.builder.strategy.OperatorStrategy;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryCondition;
import lombok.RequiredArgsConstructor;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;

/**
 * ne 操作符策略（not equal）
 * 复用 EqOperatorStrategy 构建 mustNot
 *
 * @author surezzzzzz
 */
@SimpleElasticsearchSearchComponent
@RequiredArgsConstructor
public class NeOperatorStrategy implements OperatorStrategy {

    private final EqOperatorStrategy eqStrategy;

    @Override
    public QueryBuilder build(String fieldName, QueryCondition condition, FieldMetadata fieldMetadata) {
        return QueryBuilders.boolQuery().mustNot(
                eqStrategy.buildEqualQuery(fieldName, condition.getValue(), fieldMetadata));
    }
}
