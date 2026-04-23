package io.github.surezzzzzz.sdk.elasticsearch.search.expression.visitor;

import io.github.surezzzzzz.sdk.elasticsearch.search.constant.SimpleElasticsearchSearchConstant;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryCondition;
import io.github.surezzzzzz.sdk.expression.condition.parser.constant.*;
import io.github.surezzzzzz.sdk.expression.condition.parser.model.*;
import io.github.surezzzzzz.sdk.expression.condition.parser.visitor.ExpressionVisitor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 将条件表达式 AST 翻译为 QueryCondition 树
 * 持有固定的 fieldMapping，由 ExpressionVisitorRegistry 在启动时创建并管理
 * 实例只读，并发安全
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

    public ExpressionToQueryConditionVisitor(Map<String, String> fieldMapping) {
        this.fieldMapping = fieldMapping;
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
                return "eq";
            case NE:
                return "ne";
            case GT:
                return "gt";
            case GTE:
                return "gte";
            case LT:
                return "lt";
            case LTE:
                return "lte";
            default:
                return "eq";
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
            default:
                return "like";
        }
    }

    private QueryCondition buildTimeRangeCondition(String field, ComparisonOperator operator, TimeRange timeRange) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime from = computeFrom(now, timeRange);

        if (operator == ComparisonOperator.EQ) {
            return QueryCondition.builder()
                    .field(field)
                    .op("between")
                    .values(Arrays.asList(from.format(FORMATTER), now.format(FORMATTER)))
                    .build();
        }
        return QueryCondition.builder()
                .field(field)
                .op(comparisonOp(operator))
                .value(from.format(FORMATTER))
                .build();
    }

    private LocalDateTime computeFrom(LocalDateTime now, TimeRange timeRange) {
        int amount = timeRange.getAmount();
        ChronoUnit unit = timeRange.getUnit();
        if (amount <= 0) {
            return truncateTo(now, unit);
        }
        switch (unit) {
            case MINUTES:
                return now.minusMinutes(amount);
            case HOURS:
                return now.minusHours(amount);
            case DAYS:
                return now.minusDays(amount);
            case WEEKS:
                return now.minusWeeks(amount);
            case MONTHS:
                return now.minusMonths(amount);
            case YEARS:
                return now.minusYears(amount);
            default:
                return now.minusDays(amount);
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
        return QueryCondition.builder()
                .field(condition.getField())
                .op(negateOp(condition.getOp()))
                .value(condition.getValue())
                .values(condition.getValues())
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
            case "is_null":
                return "is_not_null";
            case "is_not_null":
                return "is_null";
            default:
                return op;
        }
    }
}
