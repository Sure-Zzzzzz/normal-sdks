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

import java.util.ArrayList;
import java.util.List;

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
     * 构建查询（直接传入 metadata，避免重复查询）
     *
     * @param metadata  索引元数据
     * @param condition 查询条件
     * @return ES QueryBuilder
     */
    public QueryBuilder build(IndexMetadata metadata, QueryCondition condition) {
        if (condition == null) {
            return QueryBuilders.matchAllQuery();
        }
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
        String logic = defaultLogic(condition.getLogic());

        List<QueryBuilder> flatQueries = new ArrayList<>();
        collectFlatQueries(condition, metadata, flatQueries, logic);

        if (flatQueries.size() == 1) {
            return flatQueries.get(0);
        }

        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        for (QueryBuilder q : flatQueries) {
            if (SimpleElasticsearchSearchConstant.LOGIC_OR.equalsIgnoreCase(logic)) {
                boolQuery.should(q);
            } else {
                boolQuery.must(q);
            }
        }
        if (SimpleElasticsearchSearchConstant.LOGIC_OR.equalsIgnoreCase(logic)) {
            boolQuery.minimumShouldMatch(SimpleElasticsearchSearchConstant.OR_MINIMUM_SHOULD_MATCH);
        }
        return boolQuery;
    }

    /**
     * 递归收集子查询，扁平化逻辑与根节点相同的节点
     * - 逻辑与根节点相同（AND+AND 或 OR+OR）：继续扁平展开
     * - 逻辑与根节点不同（AND+OR 或 OR+AND）：将子节点构建为完整 bool 后加入
     *
     * @param condition 当前条件节点
     * @param metadata  索引元数据
     * @param results   收集结果
     * @param rootLogic 根节点的逻辑类型（用于判断是否需要展开）
     */
    private void collectFlatQueries(QueryCondition condition, IndexMetadata metadata,
                                     List<QueryBuilder> results, String rootLogic) {
        if (condition.isLogicCondition()) {
            String logic = defaultLogic(condition.getLogic());

            if (logic.equalsIgnoreCase(rootLogic)) {
                // 逻辑与根节点相同：扁平展开
                for (QueryCondition sub : condition.getConditions()) {
                    collectFlatQueries(sub, metadata, results, rootLogic);
                }
                return;
            }

            // 逻辑与根节点不同：将子节点构建为完整 bool 后加入
            QueryBuilder built = buildLogicAsCompleteUnit(logic, condition.getConditions(), metadata);
            results.add(built);
            return;
        }

        // 叶子节点：构建后加入
        results.add(buildFieldCondition(condition, metadata));
    }

    /**
     * 将一组条件构建为指定逻辑的完整 bool 查询（供 collectFlatQueries 在逻辑与父不同时调用）
     *
     * @param logic      逻辑类型（and/or）
     * @param conditions 条件列表
     * @param metadata   索引元数据
     * @return 完整的 BoolQueryBuilder
     */
    private QueryBuilder buildLogicAsCompleteUnit(String logic, List<QueryCondition> conditions,
                                                   IndexMetadata metadata) {
        List<QueryBuilder> subResults = new ArrayList<>();
        for (QueryCondition sub : conditions) {
            // 子节点相对于当前逻辑节点展开（same logic = flatten, diff = build unit）
            collectFlatQueries(sub, metadata, subResults, logic);
        }

        if (subResults.size() == 1) {
            return subResults.get(0);
        }

        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        for (QueryBuilder q : subResults) {
            if (SimpleElasticsearchSearchConstant.LOGIC_OR.equalsIgnoreCase(logic)) {
                boolQuery.should(q);
            } else {
                boolQuery.must(q);
            }
        }
        if (SimpleElasticsearchSearchConstant.LOGIC_OR.equalsIgnoreCase(logic)) {
            boolQuery.minimumShouldMatch(SimpleElasticsearchSearchConstant.OR_MINIMUM_SHOULD_MATCH);
        }
        return boolQuery;
    }

    private String defaultLogic(String logic) {
        return logic == null ? SimpleElasticsearchSearchConstant.LOGIC_AND : logic;
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
