package io.github.surezzzzzz.sdk.expression.condition.parser.model;

import io.github.surezzzzzz.sdk.expression.condition.parser.constant.MatchOperator;
import io.github.surezzzzzz.sdk.expression.condition.parser.visitor.ExpressionVisitor;
import lombok.*;

/**
 * LIKE 表达式
 * 用于模糊匹配、前缀匹配、后缀匹配
 *
 * @author surezzzzzz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class LikeExpression extends Expression {

    /**
     * 字段名
     */
    private String field;

    /**
     * 匹配运算符类型
     * LIKE, PREFIX, SUFFIX, NOT_LIKE
     */
    private MatchOperator operator;

    /**
     * 匹配值（业务层根据 operator 决定如何拼接通配符）
     */
    private ValueNode value;

    @Override
    public <R> R accept(ExpressionVisitor<R> visitor) {
        return visitor.visitLike(this);
    }
}
