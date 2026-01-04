package io.github.surezzzzzz.sdk.expression.condition.parser.model;

import io.github.surezzzzzz.sdk.expression.condition.parser.visitor.ExpressionVisitor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * NULL 表达式
 * 用于判断字段值是否为 NULL
 * <p>
 * 示例：名称 IS NULL, 描述 IS NOT NULL
 *
 * @author surezzzzzz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class NullExpression extends Expression {

    /**
     * 字段名
     */
    private String field;

    /**
     * 是否为 IS NULL
     * true = IS NULL, false = IS NOT NULL
     */
    private boolean isNull;

    @Override
    public <R> R accept(ExpressionVisitor<R> visitor) {
        return visitor.visitNull(this);
    }
}
