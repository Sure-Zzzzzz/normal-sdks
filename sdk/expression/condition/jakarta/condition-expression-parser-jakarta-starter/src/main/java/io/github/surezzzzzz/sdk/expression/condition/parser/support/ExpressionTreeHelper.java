package io.github.surezzzzzz.sdk.expression.condition.parser.support;

import io.github.surezzzzzz.sdk.expression.condition.parser.constant.*;
import io.github.surezzzzzz.sdk.expression.condition.parser.exception.ExpressionValidationException;
import io.github.surezzzzzz.sdk.expression.condition.parser.model.*;
import lombok.Value;
import java.util.*;
import java.util.function.BiConsumer;
import static io.github.surezzzzzz.sdk.expression.condition.parser.exception.ExpressionValidationException.MetricType.*;

/** 树工具的统一有界迭代遍历；路径级循环检测允许共享子树按出现次数统计。 */
public final class ExpressionTreeHelper {
    private ExpressionTreeHelper() { }
    /** 默认容量下先校验完整树，再调用消费函数；空根返回零统计。 */
    public static Statistics walk(Expression root, BiConsumer<Expression, Integer> consumer) {
        return walk(root, new ParseOptions(), consumer);
    }
    /** 显式完整容量；禁止在遍历中修改树，回调若有副作用由调用方承担。 */
    public static Statistics walk(Expression root, ParseOptions options, BiConsumer<Expression, Integer> consumer) {
        ParseOptions limits = ParseOptions.snapshot(options);
        List<Frame> ordered = new ArrayList<>();
        Deque<Frame> pending = new ArrayDeque<>();
        Set<Expression> path = Collections.newSetFromMap(new IdentityHashMap<>());
        if (root != null) pending.push(new Frame(root, 1, 1, false));
        int nodes = 0, conditions = 0, astDepth = 0, logicalDepth = 0;
        while (!pending.isEmpty()) {
            Frame frame = pending.pop();
            Expression node = frame.node;
            if (frame.exit) { path.remove(node); continue; }
            if (node == null || !path.add(node)) throw invalidTree();
            if (++nodes > limits.getMaxAstNodes()) throw exceeded(NODE_COUNT, nodes, limits.getMaxAstNodes());
            if (frame.astDepth > limits.getMaxAstDepth()) throw exceeded(AST_DEPTH, frame.astDepth, limits.getMaxAstDepth());
            astDepth = Math.max(astDepth, frame.astDepth);
            ordered.add(frame);
            pending.push(new Frame(node, frame.astDepth, frame.logicalDepth, true));
            if (node instanceof BinaryExpression) {
                BinaryExpression binary = (BinaryExpression) node;
                if (binary.getOperator() == null || binary.getLeft() == null || binary.getRight() == null) throw invalidTree();
                pending.push(new Frame(binary.getRight(), frame.astDepth + 1, frame.logicalDepth + 1, false));
                pending.push(new Frame(binary.getLeft(), frame.astDepth + 1, frame.logicalDepth + 1, false));
            } else if (node instanceof UnaryExpression) {
                UnaryExpression unary = (UnaryExpression) node;
                if (unary.getOperator() == null || unary.getOperand() == null) throw invalidTree();
                pending.push(new Frame(unary.getOperand(), frame.astDepth + 1, frame.logicalDepth + 1, false));
            } else if (node instanceof ParenthesisExpression) {
                Expression child = ((ParenthesisExpression) node).getExpression();
                if (child == null) throw invalidTree();
                pending.push(new Frame(child, frame.astDepth + 1, frame.logicalDepth, false));
            } else {
                validateLeaf(node, limits);
                if (++conditions > limits.getMaxConditions()) throw exceeded(CONDITION_COUNT, conditions, limits.getMaxConditions());
                logicalDepth = Math.max(logicalDepth, frame.logicalDepth);
            }
        }
        if (consumer != null) for (Frame frame : ordered) consumer.accept(frame.node, frame.astDepth);
        return new Statistics(nodes, conditions, astDepth, logicalDepth);
    }
    /** 条件字段；容器返回 null，未知节点拒绝。 */
    public static String fieldOf(Expression node) {
        if (node instanceof ComparisonExpression) return ((ComparisonExpression) node).getField();
        if (node instanceof InExpression) return ((InExpression) node).getField();
        if (node instanceof LikeExpression) return ((LikeExpression) node).getField();
        if (node instanceof NullExpression) return ((NullExpression) node).getField();
        if (node instanceof BinaryExpression || node instanceof UnaryExpression || node instanceof ParenthesisExpression) return null;
        throw invalidTree();
    }
    private static void validateLeaf(Expression node, ParseOptions limits) {
        String field = fieldOf(node);
        if (field == null || field.trim().isEmpty()) throw invalidTree();
        if (node instanceof ComparisonExpression) {
            ComparisonExpression comparison = (ComparisonExpression) node;
            if (comparison.getOperator() == null) throw invalidTree();
            validateValue(comparison.getValue());
        } else if (node instanceof InExpression) {
            List<ValueNode> values = ((InExpression) node).getValues();
            if (values == null || values.isEmpty()) throw invalidTree();
            if (values.size() > limits.getMaxInValues()) throw exceeded(IN_VALUE_COUNT, values.size(), limits.getMaxInValues());
            for (ValueNode value : values) validateValue(value);
        } else if (node instanceof LikeExpression) {
            LikeExpression like = (LikeExpression) node;
            if (like.getOperator() == null) throw invalidTree();
            if (like.getOperator() == MatchOperator.EXISTS || like.getOperator() == MatchOperator.NOT_EXISTS) {
                if (like.getValue() != null) throw invalidTree();
            } else validateValue(like.getValue());
        }
    }
    private static void validateValue(ValueNode value) {
        if (value == null || !value.isValid()) throw invalidTree();
    }
    private static ExpressionValidationException invalidTree() { return exceeded(INVALID_TREE, 0, 0); }
    private static ExpressionValidationException exceeded(ExpressionValidationException.MetricType type, int actual, int limit) {
        return new ExpressionValidationException(type, actual, limit);
    }
    @Value
    private static class Frame { Expression node; int astDepth; int logicalDepth; boolean exit; }
    /** 实际树深度计括号；逻辑深度保留旧工具的括号透明约定。 */
    @Value
    public static class Statistics { int nodes; int conditions; int astDepth; int logicalDepth; }
}
