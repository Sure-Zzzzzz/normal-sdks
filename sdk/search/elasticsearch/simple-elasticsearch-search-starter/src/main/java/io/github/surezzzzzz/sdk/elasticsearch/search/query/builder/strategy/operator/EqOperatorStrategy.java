package io.github.surezzzzzz.sdk.elasticsearch.search.query.builder.strategy.operator;

import io.github.surezzzzzz.sdk.elasticsearch.search.annotation.SimpleElasticsearchSearchComponent;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.FieldType;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.SimpleElasticsearchSearchConstant;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.model.FieldMetadata;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.builder.strategy.OperatorStrategy;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryCondition;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;

import java.util.ArrayList;
import java.util.List;

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
        QueryBuilder queryBuilder = buildMergedQuery(value,
                fieldMetadata.getExactQueryFields(), fieldMetadata.getMatchQueryFields());
        if (queryBuilder != null) {
            return queryBuilder;
        }
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

    private QueryBuilder buildMergedQuery(Object value, List<String> exactQueryFields, List<String> matchQueryFields) {
        List<QueryBuilder> queries = new ArrayList<>();
        if (exactQueryFields != null) {
            for (String exactQueryField : exactQueryFields) {
                queries.add(QueryBuilders.termQuery(exactQueryField, value));
            }
        }
        if (matchQueryFields != null) {
            for (String matchQueryField : matchQueryFields) {
                queries.add(QueryBuilders.matchQuery(matchQueryField, value));
            }
        }
        if (queries.isEmpty()) {
            return null;
        }
        if (queries.size() == 1) {
            return queries.get(0);
        }
        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        for (QueryBuilder query : queries) {
            boolQuery.should(query);
        }
        boolQuery.minimumShouldMatch(SimpleElasticsearchSearchConstant.OR_MINIMUM_SHOULD_MATCH);
        return boolQuery;
    }
}
