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
import java.util.IdentityHashMap;
import java.util.Map;
import org.antlr.v4.runtime.Token;

/**
 * AST 构建器
 * 实现 ANTLR Visitor，将 ANTLR 的 ParseTree 转换成我们自己的 AST
 *
 * @author surezzzzzz
 */
public class AstBuilder extends ConditionExprBaseVisitor<Expression> {

    private final ValueParser valueParser;
    private final String originalExpression;
    private final ParseOptions limits;
    private final Map<Expression, Integer> depths = new IdentityHashMap<>();
    private int nodes;
    private int conditions;

    /** 使用默认构树容量；必须由调用方先完成有界语法分析。 */
    public AstBuilder(ValueParser valueParser, String originalExpression) {
        this(valueParser, originalExpression, new ParseOptions());
    }

    /** 低层构树入口，不替代主入口的词法和递归保护。 */
    public AstBuilder(ValueParser valueParser, String originalExpression, ParseOptions options) {
        if (valueParser == null) throw new IllegalArgumentException(io.github.surezzzzzz.sdk.expression.condition.parser.constant.ErrorMessage.INVALID_CONFIGURATION);
        this.valueParser = valueParser;
        this.originalExpression = originalExpression;
        this.limits = ParseOptions.snapshot(options);
    }

    // ========== 顶层入口 ==========

    /** 将当前语法节点转换为受限的表达式节点。 */
    @Override
    public Expression visitParse(ConditionExprParser.ParseContext ctx) {
        return visit(ctx.expression());
    }

    // ========== OR 表达式 ==========

    /** 将当前语法节点转换为受限的表达式节点。 */
    @Override
    public Expression visitExpression(ConditionExprParser.ExpressionContext ctx) {
        List<ConditionExprParser.AndExpressionContext> andExprs = ctx.andExpression();
        if (andExprs.size() == 1) {
            return visit(andExprs.get(0));
        }

        Expression result = visit(andExprs.get(0));
        for (int i = 1; i < andExprs.size(); i++) {
            result = bounded(BinaryExpression.builder()
                    .left(result)
                    .operator(LogicalOperator.OR)
                    .right(visit(andExprs.get(i)))
                    .build());
        }
        return result;
    }

    // ========== AND 表达式 ==========

    /** 将当前语法节点转换为受限的表达式节点。 */
    @Override
    public Expression visitAndExpression(ConditionExprParser.AndExpressionContext ctx) {
        List<ConditionExprParser.UnaryExpressionContext> unaryExprs = ctx.unaryExpression();
        if (unaryExprs.size() == 1) {
            return visit(unaryExprs.get(0));
        }

        Expression result = visit(unaryExprs.get(0));
        for (int i = 1; i < unaryExprs.size(); i++) {
            result = bounded(BinaryExpression.builder()
                    .left(result)
                    .operator(LogicalOperator.AND)
                    .right(visit(unaryExprs.get(i)))
                    .build());
        }
        return result;
    }

    // ========== NOT 表达式 ==========

    /** 将当前语法节点转换为受限的表达式节点。 */
    @Override
    public Expression visitNotExpr(ConditionExprParser.NotExprContext ctx) {
        return bounded(UnaryExpression.builder()
                .operator(UnaryOperator.NOT)
                .operand(visit(ctx.unaryExpression()))
                .build());
    }

    /** 将当前语法节点转换为受限的表达式节点。 */
    @Override
    public Expression visitPrimaryExpr(ConditionExprParser.PrimaryExprContext ctx) {
        return visit(ctx.primaryExpression());
    }

    // ========== 括号表达式 ==========

    /** 将当前语法节点转换为受限的表达式节点。 */
    @Override
    public Expression visitParenExpr(ConditionExprParser.ParenExprContext ctx) {
        return bounded(ParenthesisExpression.builder()
                .expression(visit(ctx.expression()))
                .build());
    }

    /** 将当前语法节点转换为受限的表达式节点。 */
    @Override
    public Expression visitConditionExpr(ConditionExprParser.ConditionExprContext ctx) {
        return visit(ctx.condition());
    }

    // ========== 比较条件 ==========

    /** 将当前语法节点转换为受限的表达式节点。 */
    @Override
    public Expression visitComparisonCondition(ConditionExprParser.ComparisonConditionContext ctx) {
        String field = parseField(ctx.field());
        ComparisonOperator operator = parseComparisonOperator(ctx.comparisonOp());
        ValueNode value = parseValue(ctx.value());

        return bounded(ComparisonExpression.builder()
                .field(field)
                .operator(operator)
                .value(value)
                .build());
    }

    // ========== IN 条件 ==========

    /** 将当前语法节点转换为受限的表达式节点。 */
    @Override
    public Expression visitInCondition(ConditionExprParser.InConditionContext ctx) {
        String field = parseField(ctx.field());
        List<ValueNode> values = parseValueList(ctx.valueList());

        if (values.isEmpty()) {
            throw ConditionExpressionParseException.emptyInList(
                    originalExpression,
                    ctx.start.getLine(),
                    ctx.start.getCharPositionInLine());
        }

        return bounded(InExpression.builder()
                .field(field)
                .notIn(false)
                .values(values)
                .build());
    }

    /** 将当前语法节点转换为受限的表达式节点。 */
    @Override
    public Expression visitNotInCondition(ConditionExprParser.NotInConditionContext ctx) {
        String field = parseField(ctx.field());
        List<ValueNode> values = parseValueList(ctx.valueList());

        if (values.isEmpty()) {
            throw ConditionExpressionParseException.emptyInList(
                    originalExpression,
                    ctx.start.getLine(),
                    ctx.start.getCharPositionInLine());
        }

        return bounded(InExpression.builder()
                .field(field)
                .notIn(true)
                .values(values)
                .build());
    }

    // ========== LIKE 条件 ==========

    /** 将当前语法节点转换为受限的表达式节点。 */
    @Override
    public Expression visitLikeCondition(ConditionExprParser.LikeConditionContext ctx) {
        String field = parseField(ctx.field());
        ValueNode value = parseValue(ctx.value());

        return bounded(LikeExpression.builder()
                .field(field)
                .operator(MatchOperator.LIKE)
                .value(value)
                .build());
    }

    /** 将当前语法节点转换为受限的表达式节点。 */
    @Override
    public Expression visitPrefixLikeCondition(ConditionExprParser.PrefixLikeConditionContext ctx) {
        String field = parseField(ctx.field());
        ValueNode value = parseValue(ctx.value());

        return bounded(LikeExpression.builder()
                .field(field)
                .operator(MatchOperator.PREFIX)
                .value(value)
                .build());
    }

    /** 将当前语法节点转换为受限的表达式节点。 */
    @Override
    public Expression visitSuffixLikeCondition(ConditionExprParser.SuffixLikeConditionContext ctx) {
        String field = parseField(ctx.field());
        ValueNode value = parseValue(ctx.value());

        return bounded(LikeExpression.builder()
                .field(field)
                .operator(MatchOperator.SUFFIX)
                .value(value)
                .build());
    }

    /** 将当前语法节点转换为受限的表达式节点。 */
    @Override
    public Expression visitNotLikeCondition(ConditionExprParser.NotLikeConditionContext ctx) {
        String field = parseField(ctx.field());
        ValueNode value = parseValue(ctx.value());

        return bounded(LikeExpression.builder()
                .field(field)
                .operator(MatchOperator.NOT_LIKE)
                .value(value)
                .build());
    }

    /** 将当前语法节点转换为受限的表达式节点。 */
    @Override
    public Expression visitNotPrefixLikeCondition(ConditionExprParser.NotPrefixLikeConditionContext ctx) {
        String field = parseField(ctx.field());
        ValueNode value = parseValue(ctx.value());
        return bounded(LikeExpression.builder()
                .field(field)
                .operator(MatchOperator.NOT_PREFIX)
                .value(value)
                .build());
    }

    /** 将当前语法节点转换为受限的表达式节点。 */
    @Override
    public Expression visitNotSuffixLikeCondition(ConditionExprParser.NotSuffixLikeConditionContext ctx) {
        String field = parseField(ctx.field());
        ValueNode value = parseValue(ctx.value());
        return bounded(LikeExpression.builder()
                .field(field)
                .operator(MatchOperator.NOT_SUFFIX)
                .value(value)
                .build());
    }

    // ========== NULL 条件 ==========

    /** 将当前语法节点转换为受限的表达式节点。 */
    @Override
    public Expression visitIsNullCondition(ConditionExprParser.IsNullConditionContext ctx) {
        String field = parseField(ctx.field());

        return bounded(NullExpression.builder()
                .field(field)
                .isNull(true)
                .build());
    }

    /** 将当前语法节点转换为受限的表达式节点。 */
    @Override
    public Expression visitIsNotNullCondition(ConditionExprParser.IsNotNullConditionContext ctx) {
        String field = parseField(ctx.field());

        return bounded(NullExpression.builder()
                .field(field)
                .isNull(false)
                .build());
    }

    // ========== EXISTS 条件 ==========

    /** 将当前语法节点转换为受限的表达式节点。 */
    @Override
    public Expression visitExistsCondition(ConditionExprParser.ExistsConditionContext ctx) {
        String field = parseField(ctx.field());
        return bounded(LikeExpression.builder()
                .field(field)
                .operator(MatchOperator.EXISTS)
                .value(null)
                .build());
    }

    /** 将当前语法节点转换为受限的表达式节点。 */
    @Override
    public Expression visitNotExistsCondition(ConditionExprParser.NotExistsConditionContext ctx) {
        String field = parseField(ctx.field());
        return bounded(LikeExpression.builder()
                .field(field)
                .operator(MatchOperator.NOT_EXISTS)
                .value(null)
                .build());
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
                ctx.getText(), io.github.surezzzzzz.sdk.expression.condition.parser.constant.ErrorMessage.CHECK_SYNTAX);
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

        try {
            return valueParser.parse(rawValue);
        } catch (ConditionExpressionParseException ex) {
            throw ConditionExpressionParseException.builder(ConditionExpressionParseException.ErrorType.INVALID_VALUE)
                    .expression(originalExpression).line(ctx.start.getLine())
                    .column(ctx.start.getCharPositionInLine()).offendingToken(ctx.getText()).build();
        }
    }

    /**
     * 解析值列表
     */
    private List<ValueNode> parseValueList(ConditionExprParser.ValueListContext ctx) {
        if (ctx.value().size() > limits.getMaxInValues())
            throw rejected(ConditionExpressionParseException.ErrorType.IN_VALUE_LIMIT, ctx.start);
        List<ValueNode> values = new ArrayList<>();
        for (ConditionExprParser.ValueContext valueCtx : ctx.value()) {
            values.add(parseValue(valueCtx));
        }
        return values;
    }

    /** 去掉反引号但保留字段内容；点路径由语法组装，不做全局替换。 */
    private String parseField(ConditionExprParser.FieldContext ctx) {
        String text = ctx.getText();
        return ctx.BACKTICK_FIELD() == null ? text : text.substring(1, text.length() - 1);
    }

    // 每个已构建节点立即校验，长平链也不会绕过树深度保护。
    private Expression bounded(Expression node) {
        int depth = 1;
        if (node instanceof BinaryExpression) {
            BinaryExpression binary = (BinaryExpression) node;
            depth += Math.max(depths.get(binary.getLeft()), depths.get(binary.getRight()));
        } else if (node instanceof UnaryExpression) {
            depth += depths.get(((UnaryExpression) node).getOperand());
        } else if (node instanceof ParenthesisExpression) {
            depth += depths.get(((ParenthesisExpression) node).getExpression());
        } else {
            if (++conditions > limits.getMaxConditions())
                throw rejected(ConditionExpressionParseException.ErrorType.CONDITION_LIMIT, null);
        }
        if (++nodes > limits.getMaxAstNodes())
            throw rejected(ConditionExpressionParseException.ErrorType.AST_NODE_LIMIT, null);
        if (depth > limits.getMaxAstDepth())
            throw rejected(ConditionExpressionParseException.ErrorType.AST_DEPTH_LIMIT, null);
        depths.put(node, depth);
        return node;
    }

    private ConditionExpressionParseException rejected(ConditionExpressionParseException.ErrorType type, Token token) {
        ConditionExpressionParseException.Builder builder = ConditionExpressionParseException.builder(type).expression(originalExpression);
        if (token != null) builder.line(token.getLine()).column(token.getCharPositionInLine());
        return builder.build();
    }
}
