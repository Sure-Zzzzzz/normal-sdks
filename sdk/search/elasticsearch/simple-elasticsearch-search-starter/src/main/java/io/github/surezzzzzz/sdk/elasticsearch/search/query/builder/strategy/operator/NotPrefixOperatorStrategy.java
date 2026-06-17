package io.github.surezzzzzz.sdk.elasticsearch.search.query.builder.strategy.operator;

import io.github.surezzzzzz.sdk.elasticsearch.search.annotation.SimpleElasticsearchSearchComponent;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.model.FieldMetadata;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.builder.strategy.OperatorStrategy;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryCondition;
import lombok.RequiredArgsConstructor;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;

/**
 * not_prefix 操作符策略（前缀不匹配）
 * 将 prefix 查询包装在 bool.must_not 中实现取反
 *
 * @author surezzzzzz
 */
@SimpleElasticsearchSearchComponent
@RequiredArgsConstructor
public class NotPrefixOperatorStrategy implements OperatorStrategy {

    private final PrefixOperatorStrategy prefixStrategy;

    @Override
    public QueryBuilder build(String fieldName, QueryCondition condition, FieldMetadata fieldMetadata) {
        return QueryBuilders.boolQuery().mustNot(
                prefixStrategy.build(fieldName, condition, fieldMetadata));
    }
}