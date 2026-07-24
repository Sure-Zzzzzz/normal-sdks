package io.github.surezzzzzz.sdk.elasticsearch.search.expression.visitor;

import io.github.surezzzzzz.sdk.elasticsearch.search.constant.FieldType;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.QueryOperator;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.SimpleElasticsearchSearchConstant;
import io.github.surezzzzzz.sdk.elasticsearch.search.expression.TimeRangeEnd;
import io.github.surezzzzzz.sdk.elasticsearch.search.exception.MappingException;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.model.FieldMetadata;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.MappingManager;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.model.IndexMetadata;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryCondition;
import io.github.surezzzzzz.sdk.expression.condition.parser.constant.*;
import io.github.surezzzzzz.sdk.expression.condition.parser.model.*;
import io.github.surezzzzzz.sdk.expression.condition.parser.visitor.ExpressionVisitor;
import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 将条件表达式 AST 翻译为 QueryCondition 树
 * 注册表仅缓存持有字段映射的共享实例，
 * 每次翻译创建独占上下文，避免请求状态串扰
 *
 * @author surezzzzzz
 */
@Slf4j
public class ExpressionToQueryConditionVisitor implements ExpressionVisitor<QueryCondition> {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    /**
     * 字段名映射，启动时注入，之后只读
     */
    private final Map<String, String> fieldMapping;
    private IndexMetadata indexMetadata;
    private boolean metadataResolved;
    private final MappingManager mappingManager;
    private final String index;
    private final TimeRangeEnd timeRangeEnd;
    private final Clock clock;

    public ExpressionToQueryConditionVisitor(Map<String, String> fieldMapping) {
        this(fieldMapping, null, null, TimeRangeEnd.NOW, Clock.systemDefaultZone());
    }

    public ExpressionToQueryConditionVisitor(Map<String, String> fieldMapping, MappingManager mappingManager,
                                             String index, TimeRangeEnd timeRangeEnd, Clock clock) {
        this.fieldMapping = fieldMapping;
        this.mappingManager = mappingManager;
        this.index = index;
        this.timeRangeEnd = timeRangeEnd == null ? TimeRangeEnd.NOW : timeRangeEnd;
        this.clock = clock == null ? Clock.systemDefaultZone() : clock;
    }

    public ExpressionToQueryConditionVisitor withTimeRangeContext(MappingManager manager, String requestIndex,
                                                                   TimeRangeEnd end, ZoneId zoneId) {
        return new ExpressionToQueryConditionVisitor(fieldMapping, manager, requestIndex, end,
                Clock.system(zoneId == null ? ZoneId.systemDefault() : zoneId));
    }

    @Override
    public QueryCondition visitBinary(BinaryExpression expr) {
        String logic = expr.getOperator() == LogicalOperator.OR
                ? SimpleElasticsearchSearchConstant.LOGIC_OR
                : SimpleElasticsearchSearchConstant.LOGIC_AND;
        List<QueryCondition> conditions = new ArrayList<>();
        conditions.add(expr.getLeft().accept(this));
        conditions.add(expr.getRight().accept(this));
        return QueryCondition.builder()
                .logic(logic)
                .conditions(conditions)
                .build();
    }

    @Override
    public QueryCondition visitUnary(UnaryExpression expr) {
        return negate(expr.getOperand().accept(this));
    }

    @Override
    public QueryCondition visitComparison(ComparisonExpression expr) {
        String field = resolveField(expr.getField());
        ValueNode value = expr.getValue();

        if (value.getType() == ValueType.TIME_RANGE) {
            return buildTimeRangeCondition(field, expr.getOperator(), value.asTimeRange());
        }

        return QueryCondition.builder()
                .field(field)
                .op(comparisonOp(expr.getOperator()))
                .value(extractValue(value))
                .build();
    }

    @Override
    public QueryCondition visitIn(InExpression expr) {
        String field = resolveField(expr.getField());
        List<Object> values = new ArrayList<>();
        for (ValueNode v : expr.getValues()) {
            values.add(extractValue(v));
        }
        return QueryCondition.builder()
                .field(field)
                .op(expr.isNotIn() ? "not_in" : "in")
                .values(values)
                .build();
    }

    @Override
    public QueryCondition visitLike(LikeExpression expr) {
        return QueryCondition.builder()
                .field(resolveField(expr.getField()))
                .op(matchOp(expr.getOperator()))
                .value(extractValue(expr.getValue()))
                .build();
    }

    @Override
    public QueryCondition visitNull(NullExpression expr) {
        return QueryCondition.builder()
                .field(resolveField(expr.getField()))
                .op(expr.isNull() ? "is_null" : "is_not_null")
                .build();
    }

    @Override
    public QueryCondition visitParenthesis(ParenthesisExpression expr) {
        return expr.getExpression().accept(this);
    }

    private String resolveField(String field) {
        if (fieldMapping == null || !fieldMapping.containsKey(field)) {
            return field;
        }
        return fieldMapping.get(field);
    }

    private Object extractValue(ValueNode value) {
        if (value == null || value.getType() == ValueType.NULL) {
            return null;
        }
        return value.getRawValue();
    }

    private String comparisonOp(ComparisonOperator operator) {
        switch (operator) {
            case EQ:
                return QueryOperator.EQ.getOperator();
            case NE:
                return QueryOperator.NE.getOperator();
            case GT:
                return QueryOperator.GT.getOperator();
            case GTE:
                return QueryOperator.GTE.getOperator();
            case LT:
                return QueryOperator.LT.getOperator();
            case LTE:
                return QueryOperator.LTE.getOperator();
            default:
                return QueryOperator.EQ.getOperator();
        }
    }

    private String matchOp(MatchOperator operator) {
        switch (operator) {
            case LIKE:
                return "like";
            case NOT_LIKE:
                return "not_like";
            case PREFIX:
                return "prefix";
            case SUFFIX:
                return "suffix";
            case NOT_PREFIX:
                return "not_prefix";
            case NOT_SUFFIX:
                return "not_suffix";
            case EXISTS:
                return "exists";
            case NOT_EXISTS:
                return "not_exists";
            default:
                return "like";
        }
    }

    private QueryCondition buildTimeRangeCondition(String field, ComparisonOperator operator, TimeRange timeRange) {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime end = timeRange.isLastType() && timeRangeEnd == TimeRangeEnd.TODAY_START
                ? now.toLocalDate().atStartOfDay() : now;
        LocalDateTime from = computeFrom(end, timeRange);

        Object fromValue = convertTimeValue(field, from);
        Object toValue = convertTimeValue(field, end);
        if (operator == ComparisonOperator.EQ) {
            return QueryCondition.builder()
                    .field(field)
                    .op(QueryOperator.BETWEEN.getOperator())
                    .values(Arrays.asList(fromValue, toValue))
                    .build();
        }
        if (operator == ComparisonOperator.NE) {
            return buildOutsideRangeCondition(field, fromValue, toValue);
        }
        return QueryCondition.builder()
                .field(field)
                .op(comparisonOp(operator))
                .value(fromValue)
                .build();
    }

    private Object convertTimeValue(String field, LocalDateTime value) {
        FieldMetadata fieldMetadata = resolveFieldMetadata(field);
        if (fieldMetadata != null && fieldMetadata.getType() == FieldType.LONG) {
            return value.atZone(clock.getZone()).toEpochSecond();
        }
        return value.format(FORMATTER);
    }

    private FieldMetadata resolveFieldMetadata(String field) {
        if (!metadataResolved && mappingManager != null && mappingManager.findIndexConfig(index) != null) {
            metadataResolved = true;
            try {
                indexMetadata = mappingManager.getMetadata(index);
            } catch (MappingException e) {
                log.debug("无法加载索引 [{}] 的 mapping，时间边界回退为日期字符串", index);
            }
        }
        return indexMetadata == null ? null : indexMetadata.getField(field);
    }

    private LocalDateTime computeFrom(LocalDateTime end, TimeRange timeRange) {
        int amount = timeRange.getAmount();
        ChronoUnit unit = timeRange.getUnit();
        if (amount <= 0) {
            return truncateTo(end, unit);
        }
        switch (unit) {
            case MINUTES:
                return end.minusMinutes(amount);
            case HOURS:
                return end.minusHours(amount);
            case DAYS:
                return end.minusDays(amount);
            case WEEKS:
                return end.minusWeeks(amount);
            case MONTHS:
                return end.minusMonths(amount);
            case YEARS:
                return end.minusYears(amount);
            default:
                return end.minusDays(amount);
        }
    }

    private LocalDateTime truncateTo(LocalDateTime now, ChronoUnit unit) {
        switch (unit) {
            case DAYS:
                return now.toLocalDate().atStartOfDay();
            case WEEKS:
                return now.toLocalDate().atStartOfDay().minusDays(now.getDayOfWeek().getValue() - 1);
            case MONTHS:
                return now.withDayOfMonth(1).toLocalDate().atStartOfDay();
            case YEARS:
                return now.withDayOfYear(1).toLocalDate().atStartOfDay();
            default:
                return now.toLocalDate().atStartOfDay();
        }
    }

    private QueryCondition negate(QueryCondition condition) {
        if (condition.getLogic() != null && condition.getConditions() != null) {
            String negatedLogic = SimpleElasticsearchSearchConstant.LOGIC_OR.equalsIgnoreCase(condition.getLogic())
                    ? SimpleElasticsearchSearchConstant.LOGIC_AND
                    : SimpleElasticsearchSearchConstant.LOGIC_OR;
            List<QueryCondition> negatedChildren = new ArrayList<>();
            for (QueryCondition child : condition.getConditions()) {
                negatedChildren.add(negate(child));
            }
            return QueryCondition.builder()
                    .logic(negatedLogic)
                    .conditions(negatedChildren)
                    .build();
        }
        if (QueryOperator.BETWEEN.getOperator().equals(condition.getOp())) {
            List<Object> values = condition.getValues();
            return buildOutsideRangeCondition(condition.getField(),
                    values.get(SimpleElasticsearchSearchConstant.BETWEEN_FROM_INDEX),
                    values.get(SimpleElasticsearchSearchConstant.BETWEEN_TO_INDEX));
        }
        return QueryCondition.builder()
                .field(condition.getField())
                .op(negateOp(condition.getOp()))
                .value(condition.getValue())
                .values(condition.getValues())
                .build();
    }

    private QueryCondition buildOutsideRangeCondition(String field, Object from, Object to) {
        List<QueryCondition> conditions = new ArrayList<>();
        conditions.add(QueryCondition.builder().field(field).op(QueryOperator.LT.getOperator()).value(from).build());
        conditions.add(QueryCondition.builder().field(field).op(QueryOperator.GT.getOperator()).value(to).build());
        return QueryCondition.builder()
                .logic(SimpleElasticsearchSearchConstant.LOGIC_OR)
                .conditions(conditions)
                .build();
    }

    private String negateOp(String op) {
        if (op == null) return null;
        switch (op) {
            case "eq":
                return "ne";
            case "ne":
                return "eq";
            case "gt":
                return "lte";
            case "gte":
                return "lt";
            case "lt":
                return "gte";
            case "lte":
                return "gt";
            case "in":
                return "not_in";
            case "not_in":
                return "in";
            case "like":
                return "not_like";
            case "not_like":
                return "like";
            case "prefix":
                return "not_prefix";
            case "not_prefix":
                return "prefix";
            case "suffix":
                return "not_suffix";
            case "not_suffix":
                return "suffix";
            // 存在性：无 AST 专用语法，必须通过 negate() 处理，双向映射修复缺陷
            case "exists":
                return "not_exists";
            case "not_exists":
                return "exists";
            // 空值：无 AST 专用语法，采用双向映射
            case "is_null":
                return "is_not_null";
            case "is_not_null":
                return "is_null";
            default:
                return op;
        }
    }
}
