package io.github.surezzzzzz.sdk.elasticsearch.search.query.builder;

import io.github.surezzzzzz.sdk.elasticsearch.search.annotation.SimpleElasticsearchSearchComponent;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.ErrorCode;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.QueryOperator;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.SimpleElasticsearchSearchConstant;
import io.github.surezzzzzz.sdk.elasticsearch.search.exception.FieldException;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.MappingManager;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.model.FieldMetadata;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.model.IndexMetadata;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.builder.strategy.OperatorStrategyRegistry;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryCondition;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 查询构建器
 * 负责将 QueryCondition 转换为 ES QueryBuilder，操作符构建委托给 {@link OperatorStrategyRegistry}
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleElasticsearchSearchComponent
public class QueryDslBuilder {

    @Autowired
    private MappingManager mappingManager;

    @Autowired
    private OperatorStrategyRegistry operatorStrategyRegistry;

    /**
     * 构建查询
     *
     * @param indexAlias 索引别名
     * @param condition  查询条件
     * @return ES QueryBuilder
     */
    public QueryBuilder build(String indexAlias, QueryCondition condition) {
        if (condition == null) {
            return QueryBuilders.matchAllQuery();
        }
        IndexMetadata metadata = mappingManager.getMetadata(indexAlias);
        return buildCondition(condition, metadata);
    }

    /**
     * 构建条件（递归处理）
     */
    private QueryBuilder buildCondition(QueryCondition condition, IndexMetadata metadata) {
        if (condition.isLogicCondition()) {
            return buildLogicCondition(condition, metadata);
        }
        return buildFieldCondition(condition, metadata);
    }

    /**
     * 构建逻辑组合条件
     */
    private QueryBuilder buildLogicCondition(QueryCondition condition, IndexMetadata metadata) {
        String logic = condition.getLogic();
        if (logic == null) {
            logic = SimpleElasticsearchSearchConstant.LOGIC_AND;
        }

        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        for (QueryCondition subCondition : condition.getConditions()) {
            QueryBuilder subQuery = buildCondition(subCondition, metadata);
            if (SimpleElasticsearchSearchConstant.LOGIC_OR.equalsIgnoreCase(logic)) {
                boolQuery.should(subQuery);
            } else {
                boolQuery.must(subQuery);
            }
        }
        if (SimpleElasticsearchSearchConstant.LOGIC_OR.equalsIgnoreCase(logic)) {
            boolQuery.minimumShouldMatch(SimpleElasticsearchSearchConstant.OR_MINIMUM_SHOULD_MATCH);
        }
        return boolQuery;
    }

    /**
     * 构建字段条件，委托给 OperatorStrategyRegistry
     */
    private QueryBuilder buildFieldCondition(QueryCondition condition, IndexMetadata metadata) {
        String fieldName = condition.getField();

        FieldMetadata fieldMetadata = metadata.getField(fieldName);
        if (fieldMetadata == null) {
            throw new FieldException(ErrorCode.FIELD_NOT_FOUND,
                    String.format(ErrorMessage.FIELD_NOT_FOUND, fieldName));
        }
        if (!fieldMetadata.isSearchable()) {
            throw new FieldException(ErrorCode.FIELD_NOT_SEARCHABLE,
                    String.format(ErrorMessage.FIELD_NOT_SEARCHABLE, fieldName, fieldMetadata.getReason()));
        }

        QueryOperator operator = condition.getOperatorEnum();
        return operatorStrategyRegistry.resolve(operator).build(fieldName, condition, fieldMetadata);
    }
}
