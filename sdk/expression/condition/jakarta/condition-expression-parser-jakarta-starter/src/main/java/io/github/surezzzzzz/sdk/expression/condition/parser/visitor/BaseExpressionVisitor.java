package io.github.surezzzzzz.sdk.expression.condition.parser.visitor;

import io.github.surezzzzzz.sdk.expression.condition.parser.constant.LogicalOperator;
import io.github.surezzzzzz.sdk.expression.condition.parser.constant.UnaryOperator;
import io.github.surezzzzzz.sdk.expression.condition.parser.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * 表达式访问者抽象基类
 * <p>
 * 提供默认的递归遍历实现和丰富的基础查询能力，简化自定义 Visitor 的开发。
 * 子类只需 override 关心的方法，无需处理所有表达式类型。
 * <p>
 * <b>提供的基础能力：</b>
 * <ul>
 *   <li>默认递归遍历实现</li>
 *   <li>组合结果的钩子方法</li>
 *   <li>类型检查方法（isLeafExpression、isLogicalExpression等）</li>
 *   <li>字段查询方法（containsField、getFieldValue等）</li>
 *   <li>逻辑分析方法（isAllAnd、isAllOr等）</li>
 *   <li>条件查找方法（findConditions、findByPredicate等）</li>
 * </ul>
 *
 * @param <R> 访问结果类型
 * @author surezzzzzz
 * @since 1.0.1
 */
public abstract class BaseExpressionVisitor<R> implements ExpressionVisitor<R> {

    /**
     * 访问比较表达式
     * <p>
     * 默认实现返回 {@link #getDefaultResult()}
     */
    @Override
    public R visitComparison(ComparisonExpression expression) {
        return getDefaultResult();
    }

    /**
     * 访问 IN 表达式
     * <p>
     * 默认实现返回 {@link #getDefaultResult()}
     */
    @Override
    public R visitIn(InExpression expression) {
        return getDefaultResult();
    }

    /**
     * 访问 LIKE 表达式
     * <p>
     * 默认实现返回 {@link #getDefaultResult()}
     */
    @Override
    public R visitLike(LikeExpression expression) {
        return getDefaultResult();
    }

    /**
     * 访问 NULL 检查表达式
     * <p>
     * 默认实现返回 {@link #getDefaultResult()}
     */
    @Override
    public R visitNull(NullExpression expression) {
        return getDefaultResult();
    }

    /**
     * 访问二元逻辑表达式（AND/OR）
     * <p>
     * 默认实现：递归访问左右子树，然后调用 {@link #combineBinaryResults}
     */
    @Override
    public R visitBinary(BinaryExpression expression) {
        R left = expression.getLeft().accept(this);
        R right = expression.getRight().accept(this);
        return combineBinaryResults(left, right, expression.getOperator());
    }

    /**
     * 访问一元逻辑表达式（NOT）
     * <p>
     * 默认实现：递归访问操作数，然后调用 {@link #combineUnaryResult}
     */
    @Override
    public R visitUnary(UnaryExpression expression) {
        R operand = expression.getOperand().accept(this);
        return combineUnaryResult(operand, expression.getOperator());
    }

    /**
     * 访问括号表达式
     * <p>
     * 默认实现：直接访问内部表达式
     */
    @Override
    public R visitParenthesis(ParenthesisExpression expression) {
        return expression.getExpression().accept(this);
    }

    // ========== 组合钩子方法 ==========

    /**
     * 组合二元逻辑表达式的左右结果
     * <p>
     * 子类可以 override 此方法来定义如何组合 AND/OR 的结果
     *
     * @param left     左子树结果
     * @param right    右子树结果
     * @param operator 逻辑运算符
     * @return 组合后的结果
     */
    protected R combineBinaryResults(R left, R right, LogicalOperator operator) {
        return getDefaultResult();
    }

    /**
     * 组合一元逻辑表达式的结果
     * <p>
     * 子类可以 override 此方法来定义如何处理 NOT 的结果
     *
     * @param operand  操作数结果
     * @param operator 逻辑运算符
     * @return 组合后的结果
     */
    protected R combineUnaryResult(R operand, UnaryOperator operator) {
        return getDefaultResult();
    }

    /**
     * 获取默认结果
     * <p>
     * 当表达式类型未被 override 时，返回此默认值
     *
     * @return 默认结果
     */
    protected abstract R getDefaultResult();

    // ========== 类型检查方法 ==========

    /**
     * 检查表达式是否为叶子节点（条件表达式）
     *
     * @param expr 表达式
     * @return 是否为叶子节点
     */
    public static boolean isLeafExpression(Expression expr) {
        return expr instanceof ComparisonExpression
                || expr instanceof InExpression
                || expr instanceof LikeExpression
                || expr instanceof NullExpression;
    }

    /**
     * 检查表达式是否为逻辑组合节点
     *
     * @param expr 表达式
     * @return 是否为逻辑组合节点
     */
    public static boolean isLogicalExpression(Expression expr) {
        return expr instanceof BinaryExpression || expr instanceof UnaryExpression;
    }

    /**
     * 检查表达式是否为括号包裹
     *
     * @param expr 表达式
     * @return 是否为括号表达式
     */
    public static boolean isParenthesisExpression(Expression expr) {
        return expr instanceof ParenthesisExpression;
    }

    /** 查询字段；有界校验整棵树，不因前面的匹配绕过后面的非法节点。 */
    public static boolean containsField(Expression expr, String fieldHint) {
        return findFieldCondition(expr, fieldHint) != null;
    }
    /** 返回首次出现的条件，未找到返回 null；空字段提示不匹配。 */
    public static Expression findFieldCondition(Expression expr, String fieldHint) {
        if (fieldHint == null) return null;
        Expression[] first = new Expression[1];
        io.github.surezzzzzz.sdk.expression.condition.parser.support.ExpressionTreeHelper.walk(expr, (node, depth) -> {
            if (first[0] == null && fieldHint.equals(
                    io.github.surezzzzzz.sdk.expression.condition.parser.support.ExpressionTreeHelper.fieldOf(node))) first[0] = node;
        });
        return first[0];
    }
    /** 只检查二元连接拓扑，不消除 NOT，也不证明语义为合取。 */
    public static boolean isAllAnd(Expression expr) { return hasOnlyOperator(expr, LogicalOperator.AND); }
    /** 只检查二元连接拓扑，不消除 NOT，也不证明语义为析取。 */
    public static boolean isAllOr(Expression expr) { return hasOnlyOperator(expr, LogicalOperator.OR); }
    private static boolean hasOnlyOperator(Expression expr, LogicalOperator expected) {
        boolean[] result = {true};
        io.github.surezzzzzz.sdk.expression.condition.parser.support.ExpressionTreeHelper.walk(expr, (node, depth) -> {
            if (node instanceof BinaryExpression && ((BinaryExpression) node).getOperator() != expected) result[0] = false;
        });
        return result[0];
    }
    /** 校验完成后按树顺序匹配叶子；不得在谓词里修改树。 */
    public static List<Expression> findConditions(Expression expr, Predicate<Expression> predicate) {
        java.util.Objects.requireNonNull(predicate,
                io.github.surezzzzzz.sdk.expression.condition.parser.constant.ErrorMessage.INVALID_CONFIGURATION);
        List<Expression> results = new ArrayList<>();
        io.github.surezzzzzz.sdk.expression.condition.parser.support.ExpressionTreeHelper.walk(expr, (node, depth) -> {
            if (isLeafExpression(node) && predicate.test(node)) results.add(node);
        });
        return results;
    }
}
