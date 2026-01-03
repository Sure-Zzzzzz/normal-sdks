package io.github.surezzzzzz.sdk.elasticsearch.search.nl.translator;

import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.AggDefinition;
import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.AggRequest;
import io.github.surezzzzzz.sdk.elasticsearch.search.annotation.SimpleElasticsearchSearchComponent;
import io.github.surezzzzzz.sdk.elasticsearch.search.configuration.SimpleElasticsearchSearchProperties;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.SimpleElasticsearchSearchConstant;
import io.github.surezzzzzz.sdk.elasticsearch.search.exception.NLDslTranslationException;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.PaginationInfo;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryCondition;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryRequest;
import io.github.surezzzzzz.sdk.naturallanguage.parser.binder.IntentTranslator;
import io.github.surezzzzzz.sdk.naturallanguage.parser.binder.TranslateContext;
import io.github.surezzzzzz.sdk.naturallanguage.parser.model.*;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * Simple Elasticsearch Intent转换器
 * <p>
 * 职责：纯粹的数据结构转换，将natural-language-parser的Intent对象转换为search-starter的Request对象
 * 不包含业务逻辑，不调用外部服务
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleElasticsearchSearchComponent
public class SimpleElasticsearchIntentTranslator implements IntentTranslator<Object> {

    private final SimpleElasticsearchSearchProperties properties;

    public SimpleElasticsearchIntentTranslator(SimpleElasticsearchSearchProperties properties) {
        this.properties = properties;
    }

    @Override
    public Object translate(Intent intent, TranslateContext context) {
        if (intent instanceof QueryIntent) {
            return translate((QueryIntent) intent, context.getDataSource());
        } else if (intent instanceof AnalyticsIntent) {
            return translate((AnalyticsIntent) intent, context.getDataSource());
        } else {
            throw new NLDslTranslationException("不支持的Intent类型: " + intent.getClass().getSimpleName());
        }
    }

    @Override
    public String getDataSourceType() {
        return "elasticsearch";
    }

    /**
     * 转换QueryIntent为QueryRequest
     *
     * @param queryIntent 查询意图
     * @param index       索引名
     * @return QueryRequest
     */
    public QueryRequest translate(QueryIntent queryIntent, String index) {
        QueryRequest.QueryRequestBuilder builder = QueryRequest.builder()
                .index(index);

        // 转换查询条件
        if (queryIntent.hasCondition()) {
            builder.query(translateCondition(queryIntent.getCondition()));
        }

        // 转换分页信息（总是添加，即使Intent没有pagination，也会生成默认值）
        builder.pagination(translatePagination(queryIntent.getPagination(), queryIntent.getSorts()));

        return builder.build();
    }

    /**
     * 转换AnalyticsIntent为AggRequest
     *
     * @param analyticsIntent 分析意图
     * @param index           索引名
     * @return AggRequest
     */
    public AggRequest translate(AnalyticsIntent analyticsIntent, String index) {
        AggRequest.AggRequestBuilder builder = AggRequest.builder()
                .index(index);

        // 转换查询条件（过滤条件）
        if (analyticsIntent.hasCondition()) {
            builder.query(translateCondition(analyticsIntent.getCondition()));
        }

        // 转换聚合定义
        if (analyticsIntent.hasAggregation()) {
            List<AggDefinition> aggDefinitions = new ArrayList<>();
            for (AggregationIntent aggIntent : analyticsIntent.getAggregations()) {
                aggDefinitions.add(translateAggregation(aggIntent));
            }
            builder.aggs(aggDefinitions);
        }

        return builder.build();
    }

    /**
     * 转换ConditionIntent为QueryCondition
     */
    private QueryCondition translateCondition(ConditionIntent conditionIntent) {
        if (conditionIntent == null) {
            return null;
        }

        // 如果是逻辑组合条件
        if (conditionIntent.isLogicCondition()) {
            QueryCondition.QueryConditionBuilder builder = QueryCondition.builder();
            builder.logic(conditionIntent.getLogic().name().toLowerCase());

            List<QueryCondition> childConditions = new ArrayList<>();

            // 把当前条件本身作为第一个子条件
            QueryCondition currentCondition = QueryCondition.builder()
                    .field(conditionIntent.getFieldHint())
                    .op(conditionIntent.getOperator().getCode())
                    .value(conditionIntent.getValue())
                    .values(conditionIntent.getValues())
                    .build();
            childConditions.add(currentCondition);

            // 转换所有子条件
            for (ConditionIntent child : conditionIntent.getChildren()) {
                childConditions.add(translateCondition(child));
            }

            builder.conditions(childConditions);
            return builder.build();
        } else {
            // 简单条件
            return QueryCondition.builder()
                    .field(conditionIntent.getFieldHint())
                    .op(conditionIntent.getOperator().getCode())
                    .value(conditionIntent.getValue())
                    .values(conditionIntent.getValues())
                    .build();
        }
    }

    /**
     * 转换AggregationIntent为AggDefinition
     */
    private AggDefinition translateAggregation(AggregationIntent aggIntent) {
        AggDefinition.AggDefinitionBuilder builder = AggDefinition.builder()
                .name(aggIntent.getName() != null ? aggIntent.getName() : generateAggName(aggIntent))
                .type(aggIntent.getType().getCode());

        // 设置字段 - 优先使用groupByFieldHint（用于terms等桶聚合），否则使用fieldHint
        String field = aggIntent.getGroupByFieldHint() != null
                ? aggIntent.getGroupByFieldHint()
                : aggIntent.getFieldHint();
        builder.field(field);

        // 设置size（用于terms聚合）
        if (aggIntent.getSize() != null) {
            builder.size(aggIntent.getSize());
        }

        // 设置interval（用于histogram/date_histogram）
        if (aggIntent.getInterval() != null) {
            builder.interval(aggIntent.getInterval());
        }

        // 转换嵌套聚合
        if (aggIntent.getChildren() != null && !aggIntent.getChildren().isEmpty()) {
            List<AggDefinition> childAggs = new ArrayList<>();
            for (AggregationIntent childIntent : aggIntent.getChildren()) {
                childAggs.add(translateAggregation(childIntent));
            }
            builder.aggs(childAggs);
        }

        return builder.build();
    }

    /**
     * 生成聚合名称
     * 例如：AVG(年龄) -> avg_年龄
     */
    private String generateAggName(AggregationIntent aggIntent) {
        return aggIntent.getType().getCode() + "_" + aggIntent.getFieldHint();
    }

    /**
     * 转换PaginationIntent为PaginationInfo
     */
    private PaginationInfo translatePagination(PaginationIntent paginationIntent, List<SortIntent> sorts) {
        PaginationInfo.PaginationInfoBuilder builder = PaginationInfo.builder();

        // 从配置中获取默认分页大小
        final int defaultPageSize = properties.getQueryLimits().getDefaultSize();
        final int DEFAULT_PAGE_NUMBER = 1;

        // 判断分页类型
        if (paginationIntent != null && paginationIntent.getSearchAfter() != null && !paginationIntent.getSearchAfter().isEmpty()) {
            // search_after分页
            builder.type(SimpleElasticsearchSearchConstant.PAGINATION_TYPE_SEARCH_AFTER)
                    .searchAfter(paginationIntent.getSearchAfter())
                    .size(paginationIntent.getLimit() != null ? paginationIntent.getLimit() : defaultPageSize);
        } else {
            // offset分页
            builder.type(SimpleElasticsearchSearchConstant.PAGINATION_TYPE_OFFSET);

            // 优先使用page/size，否则使用offset/limit
            if (paginationIntent != null && paginationIntent.getPage() != null && paginationIntent.getSize() != null) {
                builder.page(paginationIntent.getPage())
                        .size(paginationIntent.getSize());
            } else if (paginationIntent != null && paginationIntent.getOffset() != null && paginationIntent.getLimit() != null) {
                // 将offset/limit转换为page/size
                int page = (paginationIntent.getOffset() / paginationIntent.getLimit()) + 1;
                builder.page(page)
                        .size(paginationIntent.getLimit());
            } else if (paginationIntent != null && paginationIntent.getLimit() != null) {
                // 只有limit，默认第一页
                builder.page(DEFAULT_PAGE_NUMBER)
                        .size(paginationIntent.getLimit());
            } else {
                // 使用默认分页（paginationIntent为null或没有任何分页信息）
                builder.page(DEFAULT_PAGE_NUMBER)
                        .size(defaultPageSize);
            }
        }

        // 添加排序信息
        if (sorts != null && !sorts.isEmpty()) {
            List<PaginationInfo.SortField> sortFields = new ArrayList<>();
            for (SortIntent sortIntent : sorts) {
                sortFields.add(translateSort(sortIntent));
            }
            builder.sort(sortFields);
        }

        return builder.build();
    }

    /**
     * 转换SortIntent为SortField
     */
    private PaginationInfo.SortField translateSort(SortIntent sortIntent) {
        return PaginationInfo.SortField.builder()
                .field(sortIntent.getFieldHint())
                .order(sortIntent.getOrder().name().toLowerCase())
                .build();
    }
}
