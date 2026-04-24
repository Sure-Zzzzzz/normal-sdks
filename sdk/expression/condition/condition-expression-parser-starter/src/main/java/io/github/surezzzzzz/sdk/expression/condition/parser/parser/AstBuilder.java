package io.github.surezzzzzz.sdk.expression.condition.parser.parser;

import io.github.surezzzzzz.sdk.expression.condition.parser.antlr.ConditionExprBaseVisitor;
import io.github.surezzzzzz.sdk.expression.condition.parser.antlr.ConditionExprParser;
import io.github.surezzzzzz.sdk.expression.condition.parser.constant.ComparisonOperator;
import io.github.surezzzzzz.sdk.expression.condition.parser.constant.LogicalOperator;
import io.github.surezzzzzz.sdk.expression.condition.parser.constant.MatchOperator;
import io.github.surezzzzzz.sdk.expression.condition.parser.constant.UnaryOperator;
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
            return visit(andExprs.get(0));
        }

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
            return visit(unaryExprs.get(0));
        }

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
                .operator(UnaryOperator.NOT)
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
        ComparisonOperator operator = parseComparisonOperator(ctx.comparisonOp());
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
                    ctx.start.getCharPositionInLine());
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
                    ctx.start.getCharPositionInLine());
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
     * 根据比较运算符 Context 判断运算符类型
     * 直接使用 ANTLR 生成的 token 类型判断，避免重复映射
     */
    private ComparisonOperator parseComparisonOperator(ConditionExprParser.ComparisonOpContext ctx) {
        if (ctx.EQ() != null) return ComparisonOperator.EQ;
        if (ctx.NE() != null) return ComparisonOperator.NE;
        if (ctx.GT() != null) return ComparisonOperator.GT;
        if (ctx.GTE() != null) return ComparisonOperator.GTE;
        if (ctx.LT() != null) return ComparisonOperator.LT;
        if (ctx.LTE() != null) return ComparisonOperator.LTE;
        // 理论上不可达：g4 保证 comparisonOp 只能是以上 6 种 token
        throw ConditionExpressionParseException.syntaxError(
                originalExpression, ctx.start.getLine(), ctx.start.getCharPositionInLine(),
                ctx.getText(), "无法识别的比较运算符");
    }

    /**
     * 解析值
     */
    private ValueNode parseValue(ConditionExprParser.ValueContext ctx) {
        String rawValue;

        if (ctx instanceof ConditionExprParser.StringValueContext) {
            String text = ctx.getText();
            rawValue = text.substring(1, text.length() - 1);
        } else if (ctx instanceof ConditionExprParser.NumberValueContext) {
            rawValue = ctx.getText();
        } else if (ctx instanceof ConditionExprParser.BooleanValueContext) {
            rawValue = ctx.getText();
        } else if (ctx instanceof ConditionExprParser.TimeRangeValueContext) {
            rawValue = ctx.getText();
        } else {
            throw ConditionExpressionParseException.builder(ConditionExpressionParseException.ErrorType.INVALID_VALUE)
                    .expression(originalExpression)
                    .line(ctx.start.getLine())
                    .column(ctx.start.getCharPositionInLine())
                    .offendingToken(ctx.getText())
                    .build();
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
