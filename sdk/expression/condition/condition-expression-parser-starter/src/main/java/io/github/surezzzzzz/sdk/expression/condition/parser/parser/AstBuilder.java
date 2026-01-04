package io.github.surezzzzzz.sdk.expression.condition.parser.parser;

import io.github.surezzzzzz.sdk.expression.condition.parser.antlr.ConditionExprBaseVisitor;
import io.github.surezzzzzz.sdk.expression.condition.parser.antlr.ConditionExprParser;
import io.github.surezzzzzz.sdk.expression.condition.parser.constant.ComparisonOperator;
import io.github.surezzzzzz.sdk.expression.condition.parser.constant.LogicalOperator;
import io.github.surezzzzzz.sdk.expression.condition.parser.constant.MatchOperator;
import io.github.surezzzzzz.sdk.expression.condition.parser.exception.ConditionExpressionParseException;
import io.github.surezzzzzz.sdk.expression.condition.parser.model.*;

import java.util.ArrayList;
import java.util.List;

/**
 * AST 构建器
 * 实现 ANTLR Visitor，将 ANTLR 的 ParseTree 转换成我们自己的 AST
 *
 * @author surezzzzzz
 */
public class AstBuilder extends ConditionExprBaseVisitor<Expression> {

    private final ValueParser valueParser;
    private final String originalExpression;

    public AstBuilder(ValueParser valueParser, String originalExpression) {
        this.valueParser = valueParser;
        this.originalExpression = originalExpression;
    }

    // ========== 顶层入口 ==========

    @Override
    public Expression visitParse(ConditionExprParser.ParseContext ctx) {
        return visit(ctx.expression());
    }

    // ========== OR 表达式 ==========

    @Override
    public Expression visitExpression(ConditionExprParser.ExpressionContext ctx) {
        List<ConditionExprParser.AndExpressionContext> andExprs = ctx.andExpression();
        if (andExprs.size() == 1) {
            // 只有一个 AND 表达式，直接返回
            return visit(andExprs.get(0));
        }

        // 多个 AND 表达式用 OR 连接
        Expression result = visit(andExprs.get(0));
        for (int i = 1; i < andExprs.size(); i++) {
            result = BinaryExpression.builder()
                    .left(result)
                    .operator(LogicalOperator.OR)
                    .right(visit(andExprs.get(i)))
                    .build();
        }
        return result;
    }

    // ========== AND 表达式 ==========

    @Override
    public Expression visitAndExpression(ConditionExprParser.AndExpressionContext ctx) {
        List<ConditionExprParser.UnaryExpressionContext> unaryExprs = ctx.unaryExpression();
        if (unaryExprs.size() == 1) {
            // 只有一个一元表达式，直接返回
            return visit(unaryExprs.get(0));
        }

        // 多个一元表达式用 AND 连接
        Expression result = visit(unaryExprs.get(0));
        for (int i = 1; i < unaryExprs.size(); i++) {
            result = BinaryExpression.builder()
                    .left(result)
                    .operator(LogicalOperator.AND)
                    .right(visit(unaryExprs.get(i)))
                    .build();
        }
        return result;
    }

    // ========== NOT 表达式 ==========

    @Override
    public Expression visitNotExpr(ConditionExprParser.NotExprContext ctx) {
        return UnaryExpression.builder()
                .operator(io.github.surezzzzzz.sdk.expression.condition.parser.constant.UnaryOperator.NOT)
                .operand(visit(ctx.unaryExpression()))
                .build();
    }

    @Override
    public Expression visitPrimaryExpr(ConditionExprParser.PrimaryExprContext ctx) {
        return visit(ctx.primaryExpression());
    }

    // ========== 括号表达式 ==========

    @Override
    public Expression visitParenExpr(ConditionExprParser.ParenExprContext ctx) {
        return ParenthesisExpression.builder()
                .expression(visit(ctx.expression()))
                .build();
    }

    @Override
    public Expression visitConditionExpr(ConditionExprParser.ConditionExprContext ctx) {
        return visit(ctx.condition());
    }

    // ========== 比较条件 ==========

    @Override
    public Expression visitComparisonCondition(ConditionExprParser.ComparisonConditionContext ctx) {
        String field = ctx.field().getText();
        ComparisonOperator operator = parseComparisonOperator(ctx.comparisonOp().getText());
        ValueNode value = parseValue(ctx.value());

        return ComparisonExpression.builder()
                .field(field)
                .operator(operator)
                .value(value)
                .build();
    }

    // ========== IN 条件 ==========

    @Override
    public Expression visitInCondition(ConditionExprParser.InConditionContext ctx) {
        String field = ctx.field().getText();
        List<ValueNode> values = parseValueList(ctx.valueList());

        if (values.isEmpty()) {
            throw ConditionExpressionParseException.emptyInList(
                    originalExpression,
                    ctx.start.getLine(),
                    ctx.start.getCharPositionInLine()
            );
        }

        return InExpression.builder()
                .field(field)
                .notIn(false)
                .values(values)
                .build();
    }

    @Override
    public Expression visitNotInCondition(ConditionExprParser.NotInConditionContext ctx) {
        String field = ctx.field().getText();
        List<ValueNode> values = parseValueList(ctx.valueList());

        if (values.isEmpty()) {
            throw ConditionExpressionParseException.emptyInList(
                    originalExpression,
                    ctx.start.getLine(),
                    ctx.start.getCharPositionInLine()
            );
        }

        return InExpression.builder()
                .field(field)
                .notIn(true)
                .values(values)
                .build();
    }

    // ========== LIKE 条件 ==========

    @Override
    public Expression visitLikeCondition(ConditionExprParser.LikeConditionContext ctx) {
        String field = ctx.field().getText();
        ValueNode value = parseValue(ctx.value());

        return LikeExpression.builder()
                .field(field)
                .operator(MatchOperator.LIKE)
                .value(value)
                .build();
    }

    @Override
    public Expression visitPrefixLikeCondition(ConditionExprParser.PrefixLikeConditionContext ctx) {
        String field = ctx.field().getText();
        ValueNode value = parseValue(ctx.value());

        return LikeExpression.builder()
                .field(field)
                .operator(MatchOperator.PREFIX)
                .value(value)
                .build();
    }

    @Override
    public Expression visitSuffixLikeCondition(ConditionExprParser.SuffixLikeConditionContext ctx) {
        String field = ctx.field().getText();
        ValueNode value = parseValue(ctx.value());

        return LikeExpression.builder()
                .field(field)
                .operator(MatchOperator.SUFFIX)
                .value(value)
                .build();
    }

    @Override
    public Expression visitNotLikeCondition(ConditionExprParser.NotLikeConditionContext ctx) {
        String field = ctx.field().getText();
        ValueNode value = parseValue(ctx.value());

        return LikeExpression.builder()
                .field(field)
                .operator(MatchOperator.NOT_LIKE)
                .value(value)
                .build();
    }

    // ========== NULL 条件 ==========

    @Override
    public Expression visitIsNullCondition(ConditionExprParser.IsNullConditionContext ctx) {
        String field = ctx.field().getText();

        return NullExpression.builder()
                .field(field)
                .isNull(true)
                .build();
    }

    @Override
    public Expression visitIsNotNullCondition(ConditionExprParser.IsNotNullConditionContext ctx) {
        String field = ctx.field().getText();

        return NullExpression.builder()
                .field(field)
                .isNull(false)
                .build();
    }

    // ========== 辅助方法 ==========

    /**
     * 解析比较运算符
     */
    private ComparisonOperator parseComparisonOperator(String opText) {
        switch (opText) {
            case "=":
            case "==":
            case "等于":
                return ComparisonOperator.EQ;
            case "!=":
            case "<>":
            case "不等于":
                return ComparisonOperator.NE;
            case ">":
            case "大于":
            case "晚于":
                return ComparisonOperator.GT;
            case ">=":
            case "大于等于":
            case "不小于":
                return ComparisonOperator.GTE;
            case "<":
            case "小于":
            case "早于":
                return ComparisonOperator.LT;
            case "<=":
            case "小于等于":
            case "不大于":
                return ComparisonOperator.LTE;
            default:
                throw new IllegalArgumentException("Unknown comparison operator: " + opText);
        }
    }

    /**
     * 解析值
     */
    private ValueNode parseValue(ConditionExprParser.ValueContext ctx) {
        String rawValue;

        if (ctx instanceof ConditionExprParser.StringValueContext) {
            // 字符串值，去掉引号
            String text = ctx.getText();
            rawValue = text.substring(1, text.length() - 1);
        } else if (ctx instanceof ConditionExprParser.NumberValueContext) {
            // 数字值
            rawValue = ctx.getText();
        } else if (ctx instanceof ConditionExprParser.BooleanValueContext) {
            // 布尔值
            rawValue = ctx.getText();
        } else if (ctx instanceof ConditionExprParser.TimeRangeValueContext) {
            // 时间范围
            rawValue = ctx.getText();
        } else {
            throw new IllegalArgumentException("Unknown value type: " + ctx.getClass().getSimpleName());
        }

        return valueParser.parse(rawValue);
    }

    /**
     * 解析值列表
     */
    private List<ValueNode> parseValueList(ConditionExprParser.ValueListContext ctx) {
        List<ValueNode> values = new ArrayList<>();
        for (ConditionExprParser.ValueContext valueCtx : ctx.value()) {
            values.add(parseValue(valueCtx));
        }
        return values;
    }
}
