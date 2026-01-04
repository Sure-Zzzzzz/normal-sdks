package io.github.surezzzzzz.sdk.expression.condition.parser.model;

import io.github.surezzzzzz.sdk.expression.condition.parser.constant.UnaryOperator;
import io.github.surezzzzzz.sdk.expression.condition.parser.visitor.ExpressionVisitor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 一元表达式
 * 用于逻辑取反（NOT）
 * <p>
 * 示例：NOT (类型='活跃' AND 分类='高')
 *
 * @author surezzzzzz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class UnaryExpression extends Expression {

    /**
     * 一元运算符（目前只有 NOT）
     */
    private UnaryOperator operator;

    /**
     * 子表达式
     */
    private Expression operand;

    @Override
    public <R> R accept(ExpressionVisitor<R> visitor) {
        return visitor.visitUnary(this);
    }
}
