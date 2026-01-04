package io.github.surezzzzzz.sdk.expression.condition.parser.model;

import io.github.surezzzzzz.sdk.expression.condition.parser.visitor.ExpressionVisitor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 括号分组表达式
 * 用于控制运算优先级
 * <p>
 * 示例：(类型='活跃' OR 类型='待审') AND 分类='高'
 *
 * @author surezzzzzz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class ParenthesisExpression extends Expression {

    /**
     * 括号内的表达式
     */
    private Expression expression;

    @Override
    public <R> R accept(ExpressionVisitor<R> visitor) {
        return visitor.visitParenthesis(this);
    }
}
