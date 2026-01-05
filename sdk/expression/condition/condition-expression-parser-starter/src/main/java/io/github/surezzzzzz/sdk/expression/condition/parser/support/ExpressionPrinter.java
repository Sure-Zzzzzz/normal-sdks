package io.github.surezzzzzz.sdk.expression.condition.parser.support;

import io.github.surezzzzzz.sdk.expression.condition.parser.constant.LogicalOperator;
import io.github.surezzzzzz.sdk.expression.condition.parser.constant.UnaryOperator;
import io.github.surezzzzzz.sdk.expression.condition.parser.model.*;
import io.github.surezzzzzz.sdk.expression.condition.parser.visitor.BaseExpressionVisitor;

import java.util.stream.Collectors;

/**
 * 表达式打印工具
 * <p>
 * 提供将 AST 格式化为人类可读字符串的静态方法，用于调试和日志记录，线程安全。
 * <p>
 * 内部使用 Visitor 模式实现，避免硬编码的 instanceof 判断。
 *
 * @author surezzzzzz
 * @since 1.0.1
 */
public final class ExpressionPrinter {

    private ExpressionPrinter() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * 格式化为紧凑的单行字符串
     * <p>
     * 示例输出：年龄 > 18 AND 城市 IN [北京, 上海]
     *
     * @param expr 表达式
     * @return 紧凑格式字符串
     */
    public static String toCompactString(Expression expr) {
        if (expr == null) {
            return "";
        }
        return expr.accept(new CompactStringVisitor());
    }

    /**
     * 格式化为多行树形结构字符串（用于调试）
     * <p>
     * 示例输出：
     * <pre>
     * 逻辑运算: AND
     * ├─ 左侧:
     *   比较: 年龄 GT 18 (类型:INTEGER)
     * └─ 右侧:
     *   集合: 城市 IN [北京, 上海]
     * </pre>
     *
     * @param expr 表达式
     * @return 树形格式字符串
     */
    public static String toTreeString(Expression expr) {
        if (expr == null) {
            return "";
        }
        return expr.accept(new TreeStringVisitor(0));
    }

    // ========== 内部 Visitor 实现：紧凑格式 ==========

    private static class CompactStringVisitor extends BaseExpressionVisitor<String> {

        @Override
        public String visitComparison(ComparisonExpression expr) {
            return String.format("%s %s %s",
                    expr.getField(),
                    expr.getOperator(),
                    expr.getValue().getRawValue());
        }

        @Override
        public String visitIn(InExpression expr) {
            String op = expr.isNotIn() ? "NOT IN" : "IN";
            String values = expr.getValues().stream()
                    .map(ValueNode::getRawValue)
                    .collect(Collectors.joining(", ", "[", "]"));
            return String.format("%s %s %s", expr.getField(), op, values);
        }

        @Override
        public String visitLike(LikeExpression expr) {
            return String.format("%s %s %s",
                    expr.getField(),
                    expr.getOperator(),
                    expr.getValue().getRawValue());
        }

        @Override
        public String visitNull(NullExpression expr) {
            String op = expr.isNull() ? "IS NULL" : "IS NOT NULL";
            return String.format("%s %s", expr.getField(), op);
        }

        @Override
        protected String combineBinaryResults(String left, String right, LogicalOperator operator) {
            return String.format("(%s %s %s)", left, operator, right);
        }

        @Override
        protected String combineUnaryResult(String operand, UnaryOperator operator) {
            return String.format("%s (%s)", operator, operand);
        }

        @Override
        protected String getDefaultResult() {
            return "";
        }
    }

    // ========== 内部 Visitor 实现：树形格式 ==========

    private static class TreeStringVisitor extends BaseExpressionVisitor<String> {

        private final int depth;

        TreeStringVisitor(int depth) {
            this.depth = depth;
        }

        @Override
        public String visitComparison(ComparisonExpression expr) {
            return String.format("%s比较: %s %s %s (类型:%s)\n",
                    indent(),
                    expr.getField(),
                    expr.getOperator(),
                    expr.getValue().getRawValue(),
                    expr.getValue().getType());
        }

        @Override
        public String visitIn(InExpression expr) {
            String op = expr.isNotIn() ? "NOT IN" : "IN";
            String values = expr.getValues().stream()
                    .map(ValueNode::getRawValue)
                    .collect(Collectors.joining(", ", "[", "]"));
            return String.format("%s集合: %s %s %s\n",
                    indent(),
                    expr.getField(),
                    op,
                    values);
        }

        @Override
        public String visitLike(LikeExpression expr) {
            return String.format("%s模糊匹配: %s %s %s (类型:%s)\n",
                    indent(),
                    expr.getField(),
                    expr.getOperator(),
                    expr.getValue().getRawValue(),
                    expr.getValue().getType());
        }

        @Override
        public String visitNull(NullExpression expr) {
            String op = expr.isNull() ? "IS NULL" : "IS NOT NULL";
            return String.format("%s空值检查: %s %s\n",
                    indent(),
                    expr.getField(),
                    op);
        }

        @Override
        public String visitBinary(BinaryExpression expr) {
            StringBuilder sb = new StringBuilder();
            sb.append(indent()).append("逻辑运算: ").append(expr.getOperator()).append("\n");
            sb.append(indent()).append("├─ 左侧:\n");
            sb.append(expr.getLeft().accept(new TreeStringVisitor(depth + 1)));
            sb.append(indent()).append("└─ 右侧:\n");
            sb.append(expr.getRight().accept(new TreeStringVisitor(depth + 1)));
            return sb.toString();
        }

        @Override
        public String visitUnary(UnaryExpression expr) {
            StringBuilder sb = new StringBuilder();
            sb.append(indent()).append("一元运算: ").append(expr.getOperator()).append("\n");
            sb.append(expr.getOperand().accept(new TreeStringVisitor(depth + 1)));
            return sb.toString();
        }

        @Override
        public String visitParenthesis(ParenthesisExpression expr) {
            StringBuilder sb = new StringBuilder();
            sb.append(indent()).append("括号表达式:\n");
            sb.append(expr.getExpression().accept(new TreeStringVisitor(depth + 1)));
            return sb.toString();
        }

        @Override
        protected String getDefaultResult() {
            return "";
        }

        private String indent() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < depth; i++) {
                sb.append("  ");
            }
            return sb.toString();
        }
    }
}
