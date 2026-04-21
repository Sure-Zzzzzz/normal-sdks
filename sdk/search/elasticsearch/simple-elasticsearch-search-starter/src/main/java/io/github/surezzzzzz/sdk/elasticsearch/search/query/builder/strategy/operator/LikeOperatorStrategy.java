package io.github.surezzzzzz.sdk.elasticsearch.search.query.builder.strategy.operator;

import io.github.surezzzzzz.sdk.elasticsearch.search.annotation.SimpleElasticsearchSearchComponent;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.FieldType;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.SimpleElasticsearchSearchConstant;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.model.FieldMetadata;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.builder.strategy.OperatorStrategy;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryCondition;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;

/**
 * like 操作符策略（模糊查询）
 * TEXT 类型使用 match，KEYWORD 类型使用 wildcard（自动补 *）
 *
 * @author surezzzzzz
 */
@SimpleElasticsearchSearchComponent
public class LikeOperatorStrategy implements OperatorStrategy {

    @Override
    public QueryBuilder build(String fieldName, QueryCondition condition, FieldMetadata fieldMetadata) {
        String valueStr = condition.getValue().toString();
        if (fieldMetadata.getType() == FieldType.TEXT) {
            return QueryBuilders.matchQuery(fieldName, valueStr);
        }
        if (!valueStr.contains(SimpleElasticsearchSearchConstant.WILDCARD_STAR)
                && !valueStr.contains(SimpleElasticsearchSearchConstant.WILDCARD_QUESTION)) {
            valueStr = SimpleElasticsearchSearchConstant.WILDCARD_STAR + valueStr
                    + SimpleElasticsearchSearchConstant.WILDCARD_STAR;
        }
        return QueryBuilders.wildcardQuery(fieldName, valueStr);
    }
}
