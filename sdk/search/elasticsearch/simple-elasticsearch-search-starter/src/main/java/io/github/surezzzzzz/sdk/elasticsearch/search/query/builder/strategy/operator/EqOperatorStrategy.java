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
 * eq 操作符策略
 * TEXT 类型使用 match，其他类型使用 term
 *
 * @author surezzzzzz
 */
@SimpleElasticsearchSearchComponent
public class EqOperatorStrategy implements OperatorStrategy {

    @Override
    public QueryBuilder build(String fieldName, QueryCondition condition, FieldMetadata fieldMetadata) {
        return buildEqualQuery(fieldName, condition.getValue(), fieldMetadata);
    }

    /**
     * 构建相等查询，供 NE 策略复用
     */
    public QueryBuilder buildEqualQuery(String fieldName, Object value, FieldMetadata fieldMetadata) {
        // 优先用 keyword sub-field 做精确匹配
        if (fieldMetadata.getSubFields() != null
                && fieldMetadata.getSubFields().containsKey(SimpleElasticsearchSearchConstant.SUB_FIELD_KEYWORD)) {
            return QueryBuilders.termQuery(
                    String.format(SimpleElasticsearchSearchConstant.TEMPLATE_KEYWORD_SUB_FIELD, fieldName),
                    value);
        }
        if (fieldMetadata.getType() == FieldType.TEXT) {
            return QueryBuilders.matchQuery(fieldName, value);
        }
        return QueryBuilders.termQuery(fieldName, value);
    }
}
