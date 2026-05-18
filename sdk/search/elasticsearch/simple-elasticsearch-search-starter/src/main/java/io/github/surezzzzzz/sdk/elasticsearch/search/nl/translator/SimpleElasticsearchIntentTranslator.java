package io.github.surezzzzzz.sdk.elasticsearch.search.nl.translator;

import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.AggDefinition;
import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.AggRequest;
import io.github.surezzzzzz.sdk.elasticsearch.search.annotation.SimpleElasticsearchSearchComponent;
import io.github.surezzzzzz.sdk.elasticsearch.search.binder.FieldBinder;
import io.github.surezzzzzz.sdk.elasticsearch.search.configuration.SimpleElasticsearchSearchProperties;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.SimpleElasticsearchSearchConstant;
import io.github.surezzzzzz.sdk.elasticsearch.search.exception.NLDslTranslationException;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.PaginationInfo;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryCondition;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryRequest;
import io.github.surezzzzzz.sdk.naturallanguage.parser.binder.TranslateContext;
import io.github.surezzzzzz.sdk.naturallanguage.parser.model.*;
import io.github.surezzzzzz.sdk.naturallanguage.parser.translator.IntentTranslator;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * Simple Elasticsearch Intent转换器
 * <p>
 * 职责：纯粹的数据结构转换，将 natural-language-parser 的 Intent 对象转换为 search-starter 的 Request 对象
 * 不包含业务逻辑，不调用外部服务
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleElasticsearchSearchComponent
public class SimpleElasticsearchIntentTranslator implements IntentTranslator<Object> {

    private final SimpleElasticsearchSearchProperties properties;
    private final FieldBinder fieldBinder;

    public SimpleElasticsearchIntentTranslator(
            SimpleElasticsearchSearchProperties properties,
            FieldBinder fieldBinder) {
        this.properties = properties;
        this.fieldBinder = fieldBinder;
    }

    @Override
    public Object translate(Intent intent, TranslateContext context) {
        String index = context.getDataSource();
        if (intent instanceof QueryIntent) {
            return translateQuery((QueryIntent) intent, index);
        } else if (intent instanceof AnalyticsIntent) {
            return translateAnalytics((AnalyticsIntent) intent, index);
        } else {
            throw new NLDslTranslationException("不支持的Intent类型: " + intent.getClass().getSimpleName());
        }
    }

    @Override
    public String getDataSourceType() {
        return "elasticsearch";
    }

    // ==================== 字段绑定 ====================

    private String bindField(String fieldHint, String index) {
        return fieldBinder != null ? fieldBinder.bind(fieldHint, index) : fieldHint;
    }

    // ==================== 查询转换 ====================

    private QueryRequest translateQuery(QueryIntent queryIntent, String index) {
        QueryRequest.QueryRequestBuilder builder = QueryRequest.builder()
                .index(index);

        // 转换查询条件
        if (queryIntent.hasCondition()) {
            builder.query(translateCondition(queryIntent.getCondition(), index));
        }

        // 转换字段折叠
        if (queryIntent.hasCollapse()) {
            builder.collapse(translateCollapse(queryIntent.getCollapse(), index));
        }

        // 转换日期范围
        if (queryIntent.hasDateRange()) {
            builder.dateRange(translateDateRange(queryIntent.getDateRange()));
        }

        // 转换分页信息（总是添加，即使 Intent 没有 pagination，也会生成默认值）
        builder.pagination(translatePagination(queryIntent.getPagination(), queryIntent.getSorts(), index));

        return builder.build();
    }

    /**
     * 转换 ConditionIntent 为 QueryCondition
     * <p>
     * 字段绑定：通过 FieldBinder 将 fieldHint 映射为实际的 ES 字段名
     * 逻辑条件：递归转换子条件，不额外添加父条件（避免产生 null field 的无效子条件）
     */
    private QueryCondition translateCondition(ConditionIntent conditionIntent, String index) {
        if (conditionIntent == null) {
            return null;
        }

        if (conditionIntent.isLogicCondition()) {
            // 逻辑组合（AND/OR）：递归转换所有子条件
            List<QueryCondition> childConditions = new ArrayList<>();
            // nl-parser 1.x 将第一个叶子条件复用为逻辑节点（field/op/value 保留在父节点上）
            // 若父节点自身携带 field+operator+value，将其作为第一个子条件加入（AND 场景）
            // OR 场景下父节点 value=null，不重复添加（子条件已包含完整数据）
            if (conditionIntent.getFieldHint() != null && conditionIntent.getOperator() != null
                    && (conditionIntent.getValue() != null
                        || (conditionIntent.getValues() != null && !conditionIntent.getValues().isEmpty()))) {
                String fieldHint = conditionIntent.getFieldHint();
                String boundField = bindField(fieldHint, index);
                childConditions.add(QueryCondition.builder()
                        .field(boundField)
                        .op(conditionIntent.getOperator().getCode())
                        .value(conditionIntent.getValue())
                        .values(conditionIntent.getValues())
                        .build());
            }
            for (ConditionIntent child : conditionIntent.getChildren()) {
                childConditions.add(translateCondition(child, index));
            }
            return QueryCondition.builder()
                    .logic(conditionIntent.getLogic().getCode())
                    .conditions(childConditions)
                    .build();
        } else {
            // 叶子条件：通过 FieldBinder 绑定字段名
            String fieldHint = conditionIntent.getFieldHint();
            String boundField = bindField(fieldHint, index);
            return QueryCondition.builder()
                    .field(boundField)
                    .op(conditionIntent.getOperator().getCode())
                    .value(conditionIntent.getValue())
                    .values(conditionIntent.getValues())
                    .build();
        }
    }

    private QueryRequest.CollapseField translateCollapse(CollapseIntent collapse, String index) {
        String field = bindField(collapse.getFieldHint(), index);

        QueryRequest.CollapseField.CollapseFieldBuilder builder = QueryRequest.CollapseField.builder()
                .field(field);

        if (collapse.getMaxConcurrentGroupSearches() != null) {
            builder.maxConcurrentGroupSearches(collapse.getMaxConcurrentGroupSearches());
        }

        return builder.build();
    }

    // ==================== 聚合转换 ====================

    private AggRequest translateAnalytics(AnalyticsIntent analyticsIntent, String index) {
        AggRequest.AggRequestBuilder builder = AggRequest.builder()
                .index(index);

        // 转换查询条件（过滤条件）
        if (analyticsIntent.hasCondition()) {
            builder.query(translateCondition(analyticsIntent.getCondition(), index));
        }

        // 转换聚合定义
        if (analyticsIntent.hasAggregation()) {
            List<AggDefinition> aggDefinitions = new ArrayList<>();
            for (AggregationIntent aggIntent : analyticsIntent.getAggregations()) {
                aggDefinitions.add(translateAggregation(aggIntent, index));
            }
            builder.aggs(aggDefinitions);
        }

        return builder.build();
    }

    /**
     * 转换 AggregationIntent 为 AggDefinition
     * <p>
     * nl-parser 1.1.0 API：
     * - getName() → getNameHint()
     * - getChildren() → getSubAggs()
     * - getLimit() → getSize()
     * - FieldBinder 应用到 fieldHint 和 groupByFieldHint
     */
    private AggDefinition translateAggregation(AggregationIntent aggIntent, String index) {
        // 字段绑定：优先使用 groupByFieldHint（用于 terms 等桶聚合），否则使用 fieldHint
        String fieldHint = aggIntent.getGroupByFieldHint() != null
                ? aggIntent.getGroupByFieldHint()
                : aggIntent.getFieldHint();
        String boundField = bindField(fieldHint, index);

        AggDefinition.AggDefinitionBuilder builder = AggDefinition.builder()
                // 聚合名称：优先使用 nl-parser 返回的 nameHint，否则自动生成
                .name(generateAggName(aggIntent, index))
                .type(aggIntent.getType().getCode())
                .field(boundField);

        // 设置 size（用于 terms 聚合）
        if (aggIntent.getSize() != null) {
            builder.size(aggIntent.getSize());
        }

        // 设置 interval（用于 histogram/date_histogram）
        if (aggIntent.getInterval() != null) {
            builder.interval(aggIntent.getInterval());
        }

        // 转换嵌套聚合（nl-parser 1.1.0: getChildren() → getSubAggs()）
        if (aggIntent.getSubAggs() != null && !aggIntent.getSubAggs().isEmpty()) {
            List<AggDefinition> childAggs = new ArrayList<>();
            for (AggregationIntent childIntent : aggIntent.getSubAggs()) {
                childAggs.add(translateAggregation(childIntent, index));
            }
            builder.aggs(childAggs);
        }

        return builder.build();
    }

    /**
     * 自动生成聚合名称
     * 使用绑定后的字段名，例如：AVG(age) → avg_age
     */
    private String generateAggName(AggregationIntent aggIntent, String index) {
        String fieldHint = aggIntent.getFieldHint();
        String boundField = bindField(fieldHint, index);
        log.debug("generateAggName: fieldHint={}, boundField={}, type={}", fieldHint, boundField, aggIntent.getType().getCode());
        return aggIntent.getType().getCode() + "_" + boundField;
    }

    // ==================== 分页、排序、日期范围转换 ====================

    private PaginationInfo translatePagination(PaginationIntent paginationIntent, List<SortIntent> sorts, String index) {
        PaginationInfo.PaginationInfoBuilder builder = PaginationInfo.builder();

        final int defaultPageSize = properties.getQueryLimits().getDefaultSize();
        final int DEFAULT_PAGE_NUMBER = 1;

        // search_after 分页
        if (paginationIntent != null && paginationIntent.getSearchAfter() != null && !paginationIntent.getSearchAfter().isEmpty()) {
            builder.type(SimpleElasticsearchSearchConstant.PAGINATION_TYPE_SEARCH_AFTER)
                    .searchAfter(paginationIntent.getSearchAfter())
                    // nl-parser 1.1.0: getLimit() → getSize()
                    .size(paginationIntent.getSize() != null ? paginationIntent.getSize() : defaultPageSize);
        } else {
            // offset 分页
            builder.type(SimpleElasticsearchSearchConstant.PAGINATION_TYPE_OFFSET);

            // 优先使用 page/size（nl-parser 1.1.0 使用 getPage()/getSize()）
            if (paginationIntent != null && paginationIntent.getPage() != null && paginationIntent.getSize() != null) {
                builder.page(paginationIntent.getPage())
                        .size(paginationIntent.getSize());
            } else if (paginationIntent != null && paginationIntent.getOffset() != null && paginationIntent.getSize() != null) {
                // 将 offset/size 转换为 page/size
                int page = (int) (paginationIntent.getOffset() / paginationIntent.getSize()) + 1;
                builder.page(page).size(paginationIntent.getSize());
            } else if (paginationIntent != null && paginationIntent.getSize() != null) {
                // 只有 size，默认第一页
                builder.page(DEFAULT_PAGE_NUMBER).size(paginationIntent.getSize());
            } else {
                // 使用默认值
                builder.page(DEFAULT_PAGE_NUMBER).size(defaultPageSize);
            }
        }

        // 转换排序字段（通过 FieldBinder 绑定）
        if (sorts != null && !sorts.isEmpty()) {
            List<PaginationInfo.SortField> sortFields = new ArrayList<>();
            for (SortIntent sortIntent : sorts) {
                String sortFieldHint = sortIntent.getFieldHint();
                String boundSortField = bindField(sortFieldHint, index);
                sortFields.add(PaginationInfo.SortField.builder()
                        .field(boundSortField)
                        .order(sortIntent.getOrder().getCode())
                        .build());
            }
            builder.sort(sortFields);
        }

        return builder.build();
    }

    private QueryRequest.DateRange translateDateRange(DateRangeIntent dateRangeIntent) {
        if (dateRangeIntent == null) {
            return null;
        }
        return QueryRequest.DateRange.builder()
                .from(dateRangeIntent.getFrom())
                .to(dateRangeIntent.getTo())
                .build();
    }
}
