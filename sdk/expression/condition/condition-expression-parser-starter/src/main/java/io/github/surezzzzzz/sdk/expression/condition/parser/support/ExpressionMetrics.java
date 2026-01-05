package io.github.surezzzzzz.sdk.expression.condition.parser.support;

import io.github.surezzzzzz.sdk.expression.condition.parser.exception.ExpressionValidationException;
import io.github.surezzzzzz.sdk.expression.condition.parser.model.*;

/**
 * 表达式度量工具
 * <p>
 * 提供计算表达式复杂度、深度等度量指标的静态方法，线程安全。
 * <p>
 * 使用场景：防止恶意构造的超级复杂表达式，保护系统资源。
 *
 * @author surezzzzzz
 * @since 1.0.1
 */
public final class ExpressionMetrics {

    private ExpressionMetrics() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * 计算表达式树的最大深度
     * <p>
     * 深度定义：
     * - 叶子节点（ComparisonExpression等）深度为 1
     * - 逻辑组合节点深度为 max(左子树, 右子树) + 1
     * <p>
     * 使用场景：限制表达式嵌套层数，防止栈溢出
     *
     * @param expr 表达式
     * @return 最大深度
     */
    public static int calculateDepth(Expression expr) {
        if (expr == null) {
            return 0;
        }

        if (expr instanceof ComparisonExpression
                || expr instanceof InExpression
                || expr instanceof LikeExpression
                || expr instanceof NullExpression) {
            // 叶子节点
            return 1;

        } else if (expr instanceof BinaryExpression) {
            BinaryExpression binary = (BinaryExpression) expr;
            int leftDepth = calculateDepth(binary.getLeft());
            int rightDepth = calculateDepth(binary.getRight());
            return Math.max(leftDepth, rightDepth) + 1;

        } else if (expr instanceof UnaryExpression) {
            UnaryExpression unary = (UnaryExpression) expr;
            return calculateDepth(unary.getOperand()) + 1;

        } else if (expr instanceof ParenthesisExpression) {
            ParenthesisExpression paren = (ParenthesisExpression) expr;
            return calculateDepth(paren.getExpression());
        }

        return 0;
    }

    /**
     * 统计表达式中的条件总数
     * <p>
     * 条件定义：ComparisonExpression、InExpression、LikeExpression、NullExpression
     * <p>
     * 使用场景：限制表达式复杂度，控制查询成本
     *
     * @param expr 表达式
     * @return 条件总数
     */
    public static int countConditions(Expression expr) {
        if (expr == null) {
            return 0;
        }

        if (expr instanceof ComparisonExpression
                || expr instanceof InExpression
                || expr instanceof LikeExpression
                || expr instanceof NullExpression) {
            return 1;

        } else if (expr instanceof BinaryExpression) {
            BinaryExpression binary = (BinaryExpression) expr;
            return countConditions(binary.getLeft()) + countConditions(binary.getRight());

        } else if (expr instanceof UnaryExpression) {
            UnaryExpression unary = (UnaryExpression) expr;
            return countConditions(unary.getOperand());

        } else if (expr instanceof ParenthesisExpression) {
            ParenthesisExpression paren = (ParenthesisExpression) expr;
            return countConditions(paren.getExpression());
        }

        return 0;
    }

    /**
     * 验证表达式深度不超过指定限制
     *
     * @param expr     表达式
     * @param maxDepth 最大允许深度
     * @throws ExpressionValidationException 如果深度超过限制
     */
    public static void validateDepth(Expression expr, int maxDepth) {
        int depth = calculateDepth(expr);
        if (depth > maxDepth) {
            throw new ExpressionValidationException(
                    ExpressionValidationException.MetricType.DEPTH,
                    depth,
                    maxDepth
            );
        }
    }

    /**
     * 验证表达式条件数不超过指定限制
     *
     * @param expr          表达式
     * @param maxConditions 最大允许条件数
     * @throws ExpressionValidationException 如果条件数超过限制
     */
    public static void validateConditionCount(Expression expr, int maxConditions) {
        int count = countConditions(expr);
        if (count > maxConditions) {
            throw new ExpressionValidationException(
                    ExpressionValidationException.MetricType.CONDITION_COUNT,
                    count,
                    maxConditions
            );
        }
    }
}
