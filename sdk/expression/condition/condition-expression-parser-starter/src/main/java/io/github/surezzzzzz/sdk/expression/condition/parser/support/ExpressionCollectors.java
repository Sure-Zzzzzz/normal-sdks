package io.github.surezzzzzz.sdk.expression.condition.parser.support;

import io.github.surezzzzzz.sdk.expression.condition.parser.model.*;

import java.util.*;

/**
 * 表达式信息收集工具
 * <p>
 * 提供从 AST 中收集字段名、值节点等信息的静态方法，线程安全。
 *
 * @author surezzzzzz
 * @since 1.0.1
 */
public final class ExpressionCollectors {

    private ExpressionCollectors() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * 收集表达式中所有字段名（去重）
     * <p>
     * 使用场景：验证表达式中的字段是否都在允许的白名单中
     *
     * @param expr 表达式
     * @return 字段名集合
     */
    public static Set<String> collectFields(Expression expr) {
        Set<String> fields = new LinkedHashSet<>();
        collectFieldsRecursive(expr, fields);
        return Collections.unmodifiableSet(fields);
    }

    /**
     * 收集表达式中所有值节点
     * <p>
     * 使用场景：提取所有参数值，用于参数化查询或日志记录
     *
     * @param expr 表达式
     * @return 值节点列表（保持顺序）
     */
    public static List<ValueNode> collectValues(Expression expr) {
        List<ValueNode> values = new ArrayList<>();
        collectValuesRecursive(expr, values);
        return Collections.unmodifiableList(values);
    }

    /**
     * 递归收集字段名
     */
    private static void collectFieldsRecursive(Expression expr, Set<String> fields) {
        if (expr instanceof ComparisonExpression) {
            ComparisonExpression comp = (ComparisonExpression) expr;
            fields.add(comp.getField());

        } else if (expr instanceof InExpression) {
            InExpression in = (InExpression) expr;
            fields.add(in.getField());

        } else if (expr instanceof LikeExpression) {
            LikeExpression like = (LikeExpression) expr;
            fields.add(like.getField());

        } else if (expr instanceof NullExpression) {
            NullExpression nullExpr = (NullExpression) expr;
            fields.add(nullExpr.getField());

        } else if (expr instanceof BinaryExpression) {
            BinaryExpression binary = (BinaryExpression) expr;
            collectFieldsRecursive(binary.getLeft(), fields);
            collectFieldsRecursive(binary.getRight(), fields);

        } else if (expr instanceof UnaryExpression) {
            UnaryExpression unary = (UnaryExpression) expr;
            collectFieldsRecursive(unary.getOperand(), fields);

        } else if (expr instanceof ParenthesisExpression) {
            ParenthesisExpression paren = (ParenthesisExpression) expr;
            collectFieldsRecursive(paren.getExpression(), fields);
        }
    }

    /**
     * 递归收集值节点
     */
    private static void collectValuesRecursive(Expression expr, List<ValueNode> values) {
        if (expr instanceof ComparisonExpression) {
            ComparisonExpression comp = (ComparisonExpression) expr;
            values.add(comp.getValue());

        } else if (expr instanceof InExpression) {
            InExpression in = (InExpression) expr;
            values.addAll(in.getValues());

        } else if (expr instanceof LikeExpression) {
            LikeExpression like = (LikeExpression) expr;
            values.add(like.getValue());

        } else if (expr instanceof BinaryExpression) {
            BinaryExpression binary = (BinaryExpression) expr;
            collectValuesRecursive(binary.getLeft(), values);
            collectValuesRecursive(binary.getRight(), values);

        } else if (expr instanceof UnaryExpression) {
            UnaryExpression unary = (UnaryExpression) expr;
            collectValuesRecursive(unary.getOperand(), values);

        } else if (expr instanceof ParenthesisExpression) {
            ParenthesisExpression paren = (ParenthesisExpression) expr;
            collectValuesRecursive(paren.getExpression(), values);
        }
        // NullExpression 没有值节点
    }
}
