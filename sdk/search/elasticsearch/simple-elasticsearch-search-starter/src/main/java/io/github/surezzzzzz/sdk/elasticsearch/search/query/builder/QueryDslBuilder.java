package io.github.surezzzzzz.sdk.elasticsearch.search.query.builder;

import io.github.surezzzzzz.sdk.elasticsearch.search.annotation.SimpleElasticsearchSearchComponent;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.Constants;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.ErrorMessages;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.FieldType;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.QueryOperator;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.MappingManager;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.model.FieldMetadata;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.model.IndexMetadata;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryCondition;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

/**
 * 查询构建器
 * 负责将 QueryCondition 转换为 ES QueryBuilder
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleElasticsearchSearchComponent
public class QueryDslBuilder {

    @Autowired
    private MappingManager mappingManager;

    /**
     * 构建查询
     *
     * @param indexAlias 索引别名
     * @param condition       查询条件
     * @return ES QueryBuilder
     */
    public QueryBuilder build(String indexAlias, QueryCondition condition) {
        if (condition == null) {
            return QueryBuilders.matchAllQuery();
        }

        // 获取索引元数据
        IndexMetadata metadata = mappingManager.getMetadata(indexAlias);

        return buildCondition(condition, metadata);
    }

    /**
     * 构建条件（递归处理）
     */
    private QueryBuilder buildCondition(QueryCondition condition, IndexMetadata metadata) {
        // 1. 逻辑组合条件（嵌套）
        if (condition.isLogicCondition()) {
            return buildLogicCondition(condition, metadata);
        }

        // 2. 单个字段条件
        return buildFieldCondition(condition, metadata);
    }

    /**
     * 构建逻辑组合条件
     */
    private QueryBuilder buildLogicCondition(QueryCondition condition, IndexMetadata metadata) {
        String logic = condition.getLogic();
        if (logic == null) {
            logic = Constants.LOGIC_AND; // 默认 AND
        }

        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();

        for (QueryCondition subCondition : condition.getConditions()) {
            QueryBuilder subQuery = buildCondition(subCondition, metadata);

            if (Constants.LOGIC_OR.equalsIgnoreCase(logic)) {
                boolQuery.should(subQuery);
            } else {
                // and
                boolQuery.must(subQuery);
            }
        }

        // OR 查询至少匹配一个
        if (Constants.LOGIC_OR.equalsIgnoreCase(logic)) {
            boolQuery.minimumShouldMatch(Constants.OR_MINIMUM_SHOULD_MATCH);
        }

        return boolQuery;
    }

    /**
     * 构建字段条件
     */
    private QueryBuilder buildFieldCondition(QueryCondition condition, IndexMetadata metadata) {
        String fieldName = condition.getField();
        QueryOperator operator = condition.getOperatorEnum();

        // 获取字段元数据
        FieldMetadata fieldMetadata = metadata.getField(fieldName);
        if (fieldMetadata == null) {
            throw new IllegalArgumentException(String.format(ErrorMessages.FIELD_NOT_FOUND, fieldName));
        }

        // 检查是否可查询
        if (!fieldMetadata.isSearchable()) {
            throw new IllegalArgumentException(String.format(ErrorMessages.FIELD_NOT_SEARCHABLE, fieldName, fieldMetadata.getReason()));
        }

        // 根据操作符构建查询
        switch (operator) {
            case EQ:
                return buildEqualQuery(fieldName, condition.getValue(), fieldMetadata);
            case NE:
                return QueryBuilders.boolQuery().mustNot(buildEqualQuery(fieldName, condition.getValue(), fieldMetadata));
            case GT:
                return QueryBuilders.rangeQuery(fieldName).gt(condition.getValue());
            case GTE:
                return QueryBuilders.rangeQuery(fieldName).gte(condition.getValue());
            case LT:
                return QueryBuilders.rangeQuery(fieldName).lt(condition.getValue());
            case LTE:
                return QueryBuilders.rangeQuery(fieldName).lte(condition.getValue());
            case IN:
                return buildInQuery(fieldName, condition.getValues(), fieldMetadata);
            case NOT_IN:
                return QueryBuilders.boolQuery().mustNot(buildInQuery(fieldName, condition.getValues(), fieldMetadata));
            case BETWEEN:
                return buildBetweenQuery(fieldName, condition.getValues());
            case LIKE:
                return buildLikeQuery(fieldName, condition.getValue(), fieldMetadata);
            case PREFIX:
                return QueryBuilders.prefixQuery(fieldName, condition.getValue().toString());
            case SUFFIX:
                return QueryBuilders.wildcardQuery(fieldName, Constants.WILDCARD_STAR + condition.getValue());
            case EXISTS:
                return QueryBuilders.existsQuery(fieldName);
            case NOT_EXISTS:
                return QueryBuilders.boolQuery().mustNot(QueryBuilders.existsQuery(fieldName));
            case IS_NULL:
                return QueryBuilders.boolQuery().mustNot(QueryBuilders.existsQuery(fieldName));
            case IS_NOT_NULL:
                return QueryBuilders.existsQuery(fieldName);
            case REGEX:
                return QueryBuilders.regexpQuery(fieldName, condition.getValue().toString());
            default:
                throw new IllegalArgumentException(String.format(ErrorMessages.UNSUPPORTED_OPERATOR, operator));
        }
    }

    /**
     * 构建相等查询
     */
    private QueryBuilder buildEqualQuery(String fieldName, Object value, FieldMetadata fieldMetadata) {
        FieldType fieldType = fieldMetadata.getType();

        if (fieldType == FieldType.TEXT) {
            // TEXT 类型使用 match 查询
            return QueryBuilders.matchQuery(fieldName, value);
        } else {
            // 其他类型使用 term 查询
            return QueryBuilders.termQuery(fieldName, value);
        }
    }

    /**
     * 构建 IN 查询
     */
    private QueryBuilder buildInQuery(String fieldName, List<Object> values, FieldMetadata fieldMetadata) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException(ErrorMessages.IN_VALUES_REQUIRED);
        }

        FieldType fieldType = fieldMetadata.getType();

        if (fieldType == FieldType.TEXT) {
            // TEXT 类型：使用 should + match
            BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
            for (Object value : values) {
                boolQuery.should(QueryBuilders.matchQuery(fieldName, value));
            }
            boolQuery.minimumShouldMatch(Constants.OR_MINIMUM_SHOULD_MATCH);
            return boolQuery;
        } else {
            // 其他类型：使用 terms 查询
            return QueryBuilders.termsQuery(fieldName, values);
        }
    }

    /**
     * 构建 BETWEEN 查询
     */
    private QueryBuilder buildBetweenQuery(String fieldName, List<Object> values) {
        if (values == null || values.size() != Constants.BETWEEN_REQUIRED_VALUES) {
            throw new IllegalArgumentException(ErrorMessages.BETWEEN_VALUES_INVALID);
        }
        return QueryBuilders.rangeQuery(fieldName)
                .gte(values.get(Constants.BETWEEN_FROM_INDEX))
                .lte(values.get(Constants.BETWEEN_TO_INDEX));
    }

    /**
     * 构建模糊查询
     */
    private QueryBuilder buildLikeQuery(String fieldName, Object value, FieldMetadata fieldMetadata) {
        String valueStr = value.toString();
        FieldType fieldType = fieldMetadata.getType();

        if (fieldType == FieldType.TEXT) {
            // TEXT 类型：使用 match 查询
            return QueryBuilders.matchQuery(fieldName, valueStr);
        } else {
            // KEYWORD 类型：使用 wildcard 查询
            // 自动添加通配符
            if (!valueStr.contains(Constants.WILDCARD_STAR) && !valueStr.contains(Constants.WILDCARD_QUESTION)) {
                valueStr = Constants.WILDCARD_STAR + valueStr + Constants.WILDCARD_STAR;
            }
            return QueryBuilders.wildcardQuery(fieldName, valueStr);
        }
    }
}
