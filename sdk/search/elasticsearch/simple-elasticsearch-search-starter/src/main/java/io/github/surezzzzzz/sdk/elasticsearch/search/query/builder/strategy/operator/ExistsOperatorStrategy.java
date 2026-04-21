package io.github.surezzzzzz.sdk.elasticsearch.search.query.builder.strategy.operator;

import io.github.surezzzzzz.sdk.elasticsearch.search.annotation.SimpleElasticsearchSearchComponent;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.model.FieldMetadata;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.builder.strategy.OperatorStrategy;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryCondition;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;

/**
 * exists 操作符策略（字段存在且非 null）
 *
 * @author surezzzzzz
 */
@SimpleElasticsearchSearchComponent
public class ExistsOperatorStrategy implements OperatorStrategy {

    @Override
    public QueryBuilder build(String fieldName, QueryCondition condition, FieldMetadata fieldMetadata) {
        return QueryBuilders.existsQuery(fieldName);
    }
}
