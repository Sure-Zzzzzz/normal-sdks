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

    // ========== 字段查询方法 ==========

    /**
     * 判断表达式中是否包含指定字段
     * <p>
     * 使用场景：判断规则是否涉及某个字段，如 containsField(expr, "存活状态")
     *
     * @param expr      表达式
     * @param fieldHint 字段提示
     * @return 是否包含该字段
     */
    public static boolean containsField(Expression expr, String fieldHint) {
        if (expr == null || fieldHint == null) {
            return false;
        }

        if (expr instanceof ComparisonExpression) {
            return fieldHint.equals(((ComparisonExpression) expr).getField());
        } else if (expr instanceof InExpression) {
            return fieldHint.equals(((InExpression) expr).getField());
        } else if (expr instanceof LikeExpression) {
            return fieldHint.equals(((LikeExpression) expr).getField());
        } else if (expr instanceof NullExpression) {
            return fieldHint.equals(((NullExpression) expr).getField());
        } else if (expr instanceof BinaryExpression) {
            BinaryExpression binary = (BinaryExpression) expr;
            return containsField(binary.getLeft(), fieldHint) || containsField(binary.getRight(), fieldHint);
        } else if (expr instanceof UnaryExpression) {
            return containsField(((UnaryExpression) expr).getOperand(), fieldHint);
        } else if (expr instanceof ParenthesisExpression) {
            return containsField(((ParenthesisExpression) expr).getExpression(), fieldHint);
        }

        return false;
    }

    /**
     * 查找指定字段的第一个条件表达式
     * <p>
     * 使用场景：提取某个字段的筛选条件，如 findFieldCondition(expr, "存活状态")
     *
     * @param expr      表达式
     * @param fieldHint 字段提示
     * @return 条件表达式，未找到返回 null
     */
    public static Expression findFieldCondition(Expression expr, String fieldHint) {
        if (expr == null || fieldHint == null) {
            return null;
        }

        if (expr instanceof ComparisonExpression && fieldHint.equals(((ComparisonExpression) expr).getField())) {
            return expr;
        } else if (expr instanceof InExpression && fieldHint.equals(((InExpression) expr).getField())) {
            return expr;
        } else if (expr instanceof LikeExpression && fieldHint.equals(((LikeExpression) expr).getField())) {
            return expr;
        } else if (expr instanceof NullExpression && fieldHint.equals(((NullExpression) expr).getField())) {
            return expr;
        } else if (expr instanceof BinaryExpression) {
            BinaryExpression binary = (BinaryExpression) expr;
            Expression left = findFieldCondition(binary.getLeft(), fieldHint);
            return left != null ? left : findFieldCondition(binary.getRight(), fieldHint);
        } else if (expr instanceof UnaryExpression) {
            return findFieldCondition(((UnaryExpression) expr).getOperand(), fieldHint);
        } else if (expr instanceof ParenthesisExpression) {
            return findFieldCondition(((ParenthesisExpression) expr).getExpression(), fieldHint);
        }

        return null;
    }

    // ========== 逻辑分析方法 ==========

    /**
     * 判断表达式是否全部为 AND 连接
     * <p>
     * 使用场景：判断是否为"必须同时满足"的筛选规则
     *
     * @param expr 表达式
     * @return 是否全部AND
     */
    public static boolean isAllAnd(Expression expr) {
        if (expr == null || isLeafExpression(expr)) {
            return true;
        }

        if (expr instanceof BinaryExpression) {
            BinaryExpression binary = (BinaryExpression) expr;
            if (binary.getOperator() != LogicalOperator.AND) {
                return false;
            }
            return isAllAnd(binary.getLeft()) && isAllAnd(binary.getRight());
        } else if (expr instanceof ParenthesisExpression) {
            return isAllAnd(((ParenthesisExpression) expr).getExpression());
        } else if (expr instanceof UnaryExpression) {
            return isAllAnd(((UnaryExpression) expr).getOperand());
        }

        return true;
    }

    /**
     * 判断表达式是否全部为 OR 连接
     * <p>
     * 使用场景：判断是否为"满足任一即可"的排除规则
     *
     * @param expr 表达式
     * @return 是否全部OR
     */
    public static boolean isAllOr(Expression expr) {
        if (expr == null || isLeafExpression(expr)) {
            return true;
        }

        if (expr instanceof BinaryExpression) {
            BinaryExpression binary = (BinaryExpression) expr;
            if (binary.getOperator() != LogicalOperator.OR) {
                return false;
            }
            return isAllOr(binary.getLeft()) && isAllOr(binary.getRight());
        } else if (expr instanceof ParenthesisExpression) {
            return isAllOr(((ParenthesisExpression) expr).getExpression());
        } else if (expr instanceof UnaryExpression) {
            return isAllOr(((UnaryExpression) expr).getOperand());
        }

        return true;
    }

    // ========== 条件查找方法 ==========

    /**
     * 查找所有满足条件的叶子表达式
     * <p>
     * 使用场景：提取所有符合某种模式的条件，如所有等值比较
     *
     * @param expr      表达式
     * @param predicate 判断条件
     * @return 满足条件的表达式列表
     */
    public static List<Expression> findConditions(Expression expr, Predicate<Expression> predicate) {
        List<Expression> results = new ArrayList<>();
        if (expr == null) {
            return results;
        }

        if (isLeafExpression(expr)) {
            if (predicate.test(expr)) {
                results.add(expr);
            }
        } else if (expr instanceof BinaryExpression) {
            BinaryExpression binary = (BinaryExpression) expr;
            results.addAll(findConditions(binary.getLeft(), predicate));
            results.addAll(findConditions(binary.getRight(), predicate));
        } else if (expr instanceof UnaryExpression) {
            results.addAll(findConditions(((UnaryExpression) expr).getOperand(), predicate));
        } else if (expr instanceof ParenthesisExpression) {
            results.addAll(findConditions(((ParenthesisExpression) expr).getExpression(), predicate));
        }

        return results;
    }
}
