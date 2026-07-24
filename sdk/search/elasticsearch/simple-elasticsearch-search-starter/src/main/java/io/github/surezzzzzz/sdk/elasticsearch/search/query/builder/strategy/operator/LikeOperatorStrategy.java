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
 * like 操作符策略（通配符查询）
 *
 * @author surezzzzzz
 */
@SimpleElasticsearchSearchComponent
public class LikeOperatorStrategy implements OperatorStrategy {

    @Override
    public QueryBuilder build(String fieldName, QueryCondition condition, FieldMetadata fieldMetadata) {
        String pattern = normalizePattern(condition.getValue().toString());
        QueryBuilder mergedQuery = buildMergedQuery(pattern,
                fieldMetadata.getExactQueryFields(), fieldMetadata.getMatchQueryFields());
        if (mergedQuery != null) {
            return mergedQuery;
        }
        if (fieldMetadata.getType() == FieldType.TEXT && fieldMetadata.getSubFields() != null) {
            FieldMetadata keywordSubField = fieldMetadata.getSubFields()
                    .get(SimpleElasticsearchSearchConstant.SUB_FIELD_KEYWORD);
            if (keywordSubField != null && keywordSubField.getType() == FieldType.KEYWORD) {
                return QueryBuilders.wildcardQuery(
                        String.format(SimpleElasticsearchSearchConstant.TEMPLATE_KEYWORD_SUB_FIELD, fieldName),
                        pattern);
            }
        }
        return QueryBuilders.wildcardQuery(fieldName, pattern);
    }

    private String normalizePattern(String value) {
        if (value.contains(SimpleElasticsearchSearchConstant.WILDCARD_STAR)
                || value.contains(SimpleElasticsearchSearchConstant.WILDCARD_QUESTION)) {
            return value;
        }
        return SimpleElasticsearchSearchConstant.WILDCARD_STAR + value
                + SimpleElasticsearchSearchConstant.WILDCARD_STAR;
    }

    private QueryBuilder buildMergedQuery(String pattern, List<String> exactQueryFields,
                                          List<String> matchQueryFields) {
        List<QueryBuilder> queries = new ArrayList<>();
        addWildcardQueries(queries, exactQueryFields, pattern);
        addWildcardQueries(queries, matchQueryFields, pattern);
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

    private void addWildcardQueries(List<QueryBuilder> queries, List<String> fields, String pattern) {
        if (fields == null) {
            return;
        }
        for (String field : fields) {
            queries.add(QueryBuilders.wildcardQuery(field, pattern));
        }
    }
}
