package io.github.surezzzzzz.sdk.elasticsearch.search.query.builder.strategy.operator;

import io.github.surezzzzzz.sdk.elasticsearch.search.annotation.SimpleElasticsearchSearchComponent;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.model.FieldMetadata;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.builder.strategy.OperatorStrategy;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryCondition;
import lombok.RequiredArgsConstructor;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;

/**
 * not_in 操作符策略
 * 复用 InOperatorStrategy 构建 mustNot
 *
 * @author surezzzzzz
 */
@SimpleElasticsearchSearchComponent
@RequiredArgsConstructor
public class NotInOperatorStrategy implements OperatorStrategy {

    private final InOperatorStrategy inStrategy;

    @Override
    public QueryBuilder build(String fieldName, QueryCondition condition, FieldMetadata fieldMetadata) {
        return QueryBuilders.boolQuery().mustNot(
                inStrategy.buildInQuery(fieldName, condition.getValues(), fieldMetadata));
    }
}
