package io.github.surezzzzzz.sdk.expression.condition.parser.model;

import io.github.surezzzzzz.sdk.expression.condition.parser.constant.TimeRange;
import io.github.surezzzzzz.sdk.expression.condition.parser.constant.ValueType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 值节点
 * 表示条件表达式中的值（右操作数）
 * <p>
 * SDK 负责将原始字符串解析为对应的类型，具体的业务计算由业务层处理
 *
 * @author surezzzzzz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValueNode {

    /**
     * 值类型
     */
    private ValueType type;

    /**
     * 原始字符串值（用于调试和错误提示）
     */
    private String rawValue;

    /**
     * 解析后的值（根据类型不同，实际类型不同）
     * <ul>
     *   <li>STRING: String</li>
     *   <li>INTEGER: Long</li>
     *   <li>DECIMAL: Double</li>
     *   <li>BOOLEAN: Boolean</li>
     *   <li>TIME_RANGE: TimeRange</li>
     *   <li>NULL: null</li>
     * </ul>
     */
    private Object parsedValue;

    // ========== 便捷判断方法 ==========

    public boolean isString() {
        return type == ValueType.STRING;
    }

    public boolean isInteger() {
        return type == ValueType.INTEGER;
    }

    public boolean isDecimal() {
        return type == ValueType.DECIMAL;
    }

    public boolean isNumber() {
        return isInteger() || isDecimal();
    }

    public boolean isBoolean() {
        return type == ValueType.BOOLEAN;
    }

    public boolean isTimeRange() {
        return type == ValueType.TIME_RANGE;
    }

    public boolean isNull() {
        return type == ValueType.NULL;
    }

    // ========== 便捷获取方法 ==========

    public String asString() {
        return (String) parsedValue;
    }

    public Long asInteger() {
        return (Long) parsedValue;
    }

    public Double asDecimal() {
        return (Double) parsedValue;
    }

    public Boolean asBoolean() {
        return (Boolean) parsedValue;
    }

    public TimeRange asTimeRange() {
        return (TimeRange) parsedValue;
    }
}
