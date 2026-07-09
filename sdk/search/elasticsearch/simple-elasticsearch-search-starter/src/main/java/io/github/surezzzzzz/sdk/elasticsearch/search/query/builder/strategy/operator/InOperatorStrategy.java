package io.github.surezzzzzz.sdk.elasticsearch.search.query.builder.strategy.operator;

import io.github.surezzzzzz.sdk.elasticsearch.search.annotation.SimpleElasticsearchSearchComponent;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.ErrorCode;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.FieldType;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.SimpleElasticsearchSearchConstant;
import io.github.surezzzzzz.sdk.elasticsearch.search.exception.SimpleElasticsearchSearchException;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.model.FieldMetadata;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.builder.strategy.OperatorStrategy;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryCondition;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;

import java.util.List;

/**
 * in 操作符策略
 * TEXT 类型使用 should + match，其他类型使用 terms
 *
 * @author surezzzzzz
 */
@SimpleElasticsearchSearchComponent
public class InOperatorStrategy implements OperatorStrategy {

    @Override
    public QueryBuilder build(String fieldName, QueryCondition condition, FieldMetadata fieldMetadata) {
        return buildInQuery(fieldName, condition.getValues(), fieldMetadata);
    }

    /**
     * 构建 IN 查询，供 NotInOperatorStrategy 复用
     */
    public QueryBuilder buildInQuery(String fieldName, List<Object> values, FieldMetadata fieldMetadata) {
        if (values == null || values.isEmpty()) {
            throw new SimpleElasticsearchSearchException(ErrorCode.IN_VALUES_REQUIRED, ErrorMessage.IN_VALUES_REQUIRED);
        }
        QueryBuilder queryBuilder = buildMergedInQuery(values,
                fieldMetadata.getExactQueryFields(), fieldMetadata.getMatchQueryFields());
        if (queryBuilder != null) {
            return queryBuilder;
        }
        if (fieldMetadata.getType() == FieldType.TEXT) {
            BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
            for (Object value : values) {
                boolQuery.should(QueryBuilders.matchQuery(fieldName, value));
            }
            boolQuery.minimumShouldMatch(SimpleElasticsearchSearchConstant.OR_MINIMUM_SHOULD_MATCH);
            return boolQuery;
        }
        return QueryBuilders.termsQuery(fieldName, values);
    }

    private QueryBuilder buildMergedInQuery(List<Object> values, List<String> exactQueryFields,
                                            List<String> matchQueryFields) {
        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        int queryCount = 0;
        if (exactQueryFields != null) {
            for (String exactQueryField : exactQueryFields) {
                boolQuery.should(QueryBuilders.termsQuery(exactQueryField, values));
                queryCount++;
            }
        }
        if (matchQueryFields != null) {
            for (String matchQueryField : matchQueryFields) {
                for (Object value : values) {
                    boolQuery.should(QueryBuilders.matchQuery(matchQueryField, value));
                    queryCount++;
                }
            }
        }
        if (queryCount == 0) {
            return null;
        }
        if (queryCount == 1 && exactQueryFields != null && exactQueryFields.size() == 1
                && (matchQueryFields == null || matchQueryFields.isEmpty())) {
            return QueryBuilders.termsQuery(exactQueryFields.get(0), values);
        }
        boolQuery.minimumShouldMatch(SimpleElasticsearchSearchConstant.OR_MINIMUM_SHOULD_MATCH);
        return boolQuery;
    }
}
