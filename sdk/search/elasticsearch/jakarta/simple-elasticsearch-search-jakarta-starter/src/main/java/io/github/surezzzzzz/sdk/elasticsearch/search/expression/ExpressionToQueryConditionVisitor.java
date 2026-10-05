package io.github.surezzzzzz.sdk.elasticsearch.search.expression;

import io.github.surezzzzzz.sdk.elasticsearch.search.constant.QueryOperator;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.model.FieldMetadata;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryCondition;
import io.github.surezzzzzz.sdk.expression.condition.parser.constant.ComparisonOperator;
import io.github.surezzzzzz.sdk.expression.condition.parser.constant.LogicalOperator;
import io.github.surezzzzzz.sdk.expression.condition.parser.constant.TimeRange;
import io.github.surezzzzzz.sdk.expression.condition.parser.constant.ValueType;
import io.github.surezzzzzz.sdk.expression.condition.parser.model.*;
import io.github.surezzzzzz.sdk.expression.condition.parser.visitor.ExpressionVisitor;
import lombok.RequiredArgsConstructor;

import java.time.DayOfWeek;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

import static io.github.surezzzzzz.sdk.elasticsearch.search.constant.SearchProtocolConstant.*;
import static io.github.surezzzzzz.sdk.elasticsearch.search.constant.SimpleElasticsearchSearchConstant.MONTHS_PER_QUARTER;
import static io.github.surezzzzzz.sdk.elasticsearch.search.support.SearchValidationHelper.field;
import static io.github.surezzzzzz.sdk.elasticsearch.search.support.SearchValidationHelper.invalid;

/**
 * 每次翻译独占上下文；字段映射只作用于 AST 字段，时间计算使用同一固定锚点。
 */
@RequiredArgsConstructor
public class ExpressionToQueryConditionVisitor implements ExpressionVisitor<QueryCondition> {
    private final Map<String, FieldMetadata> fields;
    private final Map<String, String> labels;
    private final ZonedDateTime anchor;
    private final TimeRangeEnd end;
    private final boolean requireExplicitAnchor;
    private final boolean explicitAnchor;

    /**
     * 保持 AND/OR 优先级和括号结构。
     */
    @Override
    public QueryCondition visitBinary(BinaryExpression expression) {
        return group(expression.getOperator() == LogicalOperator.AND ? VALUE_AND : VALUE_OR,
                expression.getLeft().accept(this), expression.getRight().accept(this));
    }

    /**
     * 保留真正的 NOT，不反转叶子比较符。
     */
    @Override
    public QueryCondition visitUnary(UnaryExpression expression) {
        return group(VALUE_NOT, expression.getOperand().accept(this));
    }

    /**
     * 数字和布尔使用 parser 的实际类型，时间关键字转成固定范围。
     */
    @Override
    public QueryCondition visitComparison(ComparisonExpression expression) {
        String field = resolveField(expression.getField());
        if (expression.getValue().getType() == ValueType.TIME_RANGE)
            return time(field, expression.getOperator(), expression.getValue().asTimeRange());
        QueryOperator operator = QueryOperator.fromString(expression.getOperator().name().toLowerCase(Locale.ROOT));
        if (operator == null) throw invalid();
        return leaf(field, operator, value(field, expression.getValue()));
    }

    /**
     * IN 值只能是可查询标量，时间范围不能偷偷转换成字符串。
     */
    @Override
    public QueryCondition visitIn(InExpression expression) {
        String field = resolveField(expression.getField());
        List<Object> values = new ArrayList<>();
        for (ValueNode item : expression.getValues()) values.add(value(field, item));
        return QueryCondition.builder().field(field)
                .op((expression.isNotIn() ? QueryOperator.NOT_IN : QueryOperator.IN).getOperator()).values(values).build();
    }

    /**
     * 匹配及存在性沿用 Search 的已校验操作符。
     */
    @Override
    public QueryCondition visitLike(LikeExpression expression) {
        QueryOperator operator = QueryOperator.fromString(expression.getOperator().name().toLowerCase(Locale.ROOT));
        if (operator == null) throw invalid();
        return leaf(resolveField(expression.getField()), operator,
                expression.getValue() == null ? null : pattern(expression.getValue()));
    }

    /**
     * NULL 与 ES 字段不存在语义一致，不读取源文档值。
     */
    @Override
    public QueryCondition visitNull(NullExpression expression) {
        return leaf(resolveField(expression.getField()), expression.isNull() ? QueryOperator.IS_NULL : QueryOperator.IS_NOT_NULL, null);
    }

    /**
     * 括号只控制语义，不新增无效结构化节点。
     */
    @Override
    public QueryCondition visitParenthesis(ParenthesisExpression expression) {
        return expression.getExpression().accept(this);
    }

    private String resolveField(String input) {
        String mapped = labels.get(input);
        if (mapped != null && fields.containsKey(input) && !input.equals(mapped)) throw field();
        return mapped == null ? input : mapped;
    }

    private Object value(String field, ValueNode value) {
        if (value == null || !value.isValid() || value.isTimeRange() || value.isNull()) throw invalid();
        FieldMetadata metadata = fields.get(field);
        // 字符串字段和文档 ID 保留原拼写，不能把 001 或 TRUE 自动改为数值或小写布尔。
        if (VALUE_ES_ID.equals(field) || metadata != null && Arrays.asList(
                VALUE_TEXT, VALUE_KEYWORD, VALUE_CONSTANT_KEYWORD, VALUE_WILDCARD).contains(metadata.getType()))
            return pattern(value);
        return value.getParsedValue();
    }

    private String pattern(ValueNode value) {
        if (value == null || !value.isValid() || value.isNull()) throw invalid();
        // parser 已去掉外围引号；匹配语义保留 005、TRUE 等原模式，不额外解释反斜杠。
        String pattern = value.isString() ? value.asString() : value.getRawValue();
        if (pattern == null) throw invalid();
        return pattern;
    }

    private QueryCondition time(String field, ComparisonOperator operator, TimeRange range) {
        if (requireExplicitAnchor && !explicitAnchor) throw invalid();
        FieldMetadata metadata = fields.get(field);
        if (metadata == null || !Arrays.asList(VALUE_DATE, VALUE_DATE_NANOS, VALUE_LONG).contains(metadata.getType()))
            throw field();
        ZonedDateTime[] bounds = bounds(range);
        Object from = timeValue(metadata, bounds[0]), to = timeValue(metadata, bounds[1]);
        QueryCondition interval = group(VALUE_AND, leaf(field, QueryOperator.GTE, from), leaf(field, QueryOperator.LT, to));
        switch (operator) {
            case EQ:
                return interval;
            case NE:
                return group(VALUE_NOT, interval);
            case GT:
                return leaf(field, QueryOperator.GTE, to);
            case GTE:
                return leaf(field, QueryOperator.GTE, from);
            case LT:
                return leaf(field, QueryOperator.LT, from);
            case LTE:
                return leaf(field, QueryOperator.LT, to);
            default:
                throw invalid();
        }
    }

    private Object timeValue(FieldMetadata metadata, ZonedDateTime value) {
        return VALUE_LONG.equals(metadata.getType()) ? value.toEpochSecond() : value.toInstant().toString();
    }

    // 历史完整周期是左闭右开；日历运算按索引时区处理 DST，不把一天固定为 24 小时。
    private ZonedDateTime[] bounds(TimeRange range) {
        ZonedDateTime day = anchor.toLocalDate().atStartOfDay(anchor.getZone());
        if (range.isLastType()) {
            ZonedDateTime upper = end == TimeRangeEnd.TODAY_START ? day : anchor;
            return new ZonedDateTime[]{upper.minus(range.getAmount(), range.getUnit()), upper};
        }
        ZonedDateTime start;
        switch (range) {
            case TODAY:
                return new ZonedDateTime[]{day, anchor};
            case YESTERDAY:
            case DAY_BEFORE_YESTERDAY:
                start = day.plusDays(range.getAmount());
                return new ZonedDateTime[]{start, start.plusDays(1)};
            case THIS_WEEK:
            case LAST_WEEK:
                start = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                if (range == TimeRange.LAST_WEEK) return new ZonedDateTime[]{start.minusWeeks(1), start};
                return new ZonedDateTime[]{start, anchor};
            case THIS_MONTH:
            case PREVIOUS_MONTH:
                start = day.withDayOfMonth(1);
                if (range == TimeRange.PREVIOUS_MONTH) return new ZonedDateTime[]{start.minusMonths(1), start};
                return new ZonedDateTime[]{start, anchor};
            case THIS_QUARTER:
            case LAST_QUARTER:
                start = day.withDayOfMonth(1).withMonth((day.getMonthValue() - 1) / MONTHS_PER_QUARTER * MONTHS_PER_QUARTER + 1);
                if (range == TimeRange.LAST_QUARTER)
                    return new ZonedDateTime[]{start.minusMonths(MONTHS_PER_QUARTER), start};
                return new ZonedDateTime[]{start, anchor};
            case THIS_YEAR:
            case LAST_YEAR:
                start = day.withDayOfYear(1);
                if (range == TimeRange.LAST_YEAR) return new ZonedDateTime[]{start.minusYears(1), start};
                return new ZonedDateTime[]{start, anchor};
            default:
                throw invalid();
        }
    }

    private QueryCondition leaf(String field, QueryOperator operator, Object value) {
        return QueryCondition.builder().field(field).op(operator.getOperator()).value(value).build();
    }

    private QueryCondition group(String logic, QueryCondition... children) {
        return QueryCondition.builder().logic(logic).conditions(Arrays.asList(children)).build();
    }
}
