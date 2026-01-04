package io.github.surezzzzzz.sdk.expression.condition.parser.model;

import io.github.surezzzzzz.sdk.expression.condition.parser.constant.ComparisonOperator;
import io.github.surezzzzzz.sdk.expression.condition.parser.visitor.ExpressionVisitor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 比较表达式
 * 用于字段与值的比较操作
 * <p>
 * 示例：类型='活跃', 年龄>=18, 创建时间<'近1个月'
 *
 * @author surezzzzzz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class ComparisonExpression extends Expression {

    /**
     * 字段名
     */
    private String field;

    /**
     * 比较运算符
     */
    private ComparisonOperator operator;

    /**
     * 值节点
     */
    private ValueNode value;

    @Override
    public <R> R accept(ExpressionVisitor<R> visitor) {
        return visitor.visitComparison(this);
    }
}
