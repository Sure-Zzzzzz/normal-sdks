package io.github.surezzzzzz.sdk.elasticsearch.search.query.builder.strategy.operator;

import io.github.surezzzzzz.sdk.elasticsearch.search.annotation.SimpleElasticsearchSearchComponent;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.model.FieldMetadata;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.builder.strategy.OperatorStrategy;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryCondition;
import lombok.RequiredArgsConstructor;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;

/**
 * not_suffix 操作符策略（后缀不匹配）
 * 将 suffix 查询（wildcard *value）包装在 bool.must_not 中实现取反
 *
 * @author surezzzzzz
 */
@SimpleElasticsearchSearchComponent
@RequiredArgsConstructor
public class NotSuffixOperatorStrategy implements OperatorStrategy {

    private final SuffixOperatorStrategy suffixStrategy;

    @Override
    public QueryBuilder build(String fieldName, QueryCondition condition, FieldMetadata fieldMetadata) {
        return QueryBuilders.boolQuery().mustNot(
                suffixStrategy.build(fieldName, condition, fieldMetadata));
    }
}