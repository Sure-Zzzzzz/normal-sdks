package io.github.surezzzzzz.sdk.expression.condition.parser.model;

import io.github.surezzzzzz.sdk.expression.condition.parser.visitor.ExpressionVisitor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * IN 表达式
 * 用于判断字段值是否在指定的值列表中
 * <p>
 * 示例：类型 IN ('高','中','低'), 分类 NOT IN ('废弃','禁用')
 *
 * @author surezzzzzz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class InExpression extends Expression {

    /**
     * 字段名
     */
    private String field;

    /**
     * 是否为 NOT IN
     * true = NOT IN, false = IN
     */
    private boolean notIn;

    /**
     * 值列表
     */
    @Builder.Default
    private List<ValueNode> values = new ArrayList<>();

    @Override
    public <R> R accept(ExpressionVisitor<R> visitor) {
        return visitor.visitIn(this);
    }
}
