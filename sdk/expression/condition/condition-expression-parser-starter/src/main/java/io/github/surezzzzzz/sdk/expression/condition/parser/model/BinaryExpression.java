package io.github.surezzzzzz.sdk.expression.condition.parser.model;

import io.github.surezzzzzz.sdk.expression.condition.parser.constant.LogicalOperator;
import io.github.surezzzzzz.sdk.expression.condition.parser.visitor.ExpressionVisitor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 二元逻辑表达式
 * 用于连接两个表达式（AND/OR）
 * <p>
 * 示例：类型='活跃' AND 分类='高', (A OR B) AND C
 *
 * @author surezzzzzz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class BinaryExpression extends Expression {

    /**
     * 左表达式
     */
    private Expression left;

    /**
     * 逻辑运算符（AND/OR）
     */
    private LogicalOperator operator;

    /**
     * 右表达式
     */
    private Expression right;

    @Override
    public <R> R accept(ExpressionVisitor<R> visitor) {
        return visitor.visitBinary(this);
    }
}
