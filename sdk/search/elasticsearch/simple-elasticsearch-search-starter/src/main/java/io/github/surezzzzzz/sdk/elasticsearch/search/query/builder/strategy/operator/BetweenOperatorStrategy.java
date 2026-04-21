package io.github.surezzzzzz.sdk.elasticsearch.search.query.builder.strategy.operator;

import io.github.surezzzzzz.sdk.elasticsearch.search.annotation.SimpleElasticsearchSearchComponent;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.ErrorCode;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.SimpleElasticsearchSearchConstant;
import io.github.surezzzzzz.sdk.elasticsearch.search.exception.SimpleElasticsearchSearchException;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.model.FieldMetadata;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.builder.strategy.OperatorStrategy;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryCondition;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;

/**
 * between 操作符策略（范围查询，含两端）
 *
 * @author surezzzzzz
 */
@SimpleElasticsearchSearchComponent
public class BetweenOperatorStrategy implements OperatorStrategy {

    @Override
    public QueryBuilder build(String fieldName, QueryCondition condition, FieldMetadata fieldMetadata) {
        if (condition.getValues() == null
                || condition.getValues().size() != SimpleElasticsearchSearchConstant.BETWEEN_REQUIRED_VALUES) {
            throw new SimpleElasticsearchSearchException(
                    ErrorCode.BETWEEN_VALUES_INVALID, ErrorMessage.BETWEEN_VALUES_INVALID);
        }
        return QueryBuilders.rangeQuery(fieldName)
                .gte(condition.getValues().get(SimpleElasticsearchSearchConstant.BETWEEN_FROM_INDEX))
                .lte(condition.getValues().get(SimpleElasticsearchSearchConstant.BETWEEN_TO_INDEX));
    }
}
