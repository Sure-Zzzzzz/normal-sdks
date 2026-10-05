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
@lombok.ToString(onlyExplicitlyIncluded = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValueNode {

    /**
     * 值类型
     */
    @lombok.ToString.Include
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

    /** 校验类型与实际 Java 值一致；用于自定义策略与手工树的统一检查。 */
    public boolean isValid() {
        if (type == null) return false;
        switch (type) {
            case NULL: return parsedValue == null;
            case STRING: return parsedValue instanceof String;
            case INTEGER: return parsedValue instanceof Long;
            case DECIMAL: return parsedValue instanceof Double && Double.isFinite((Double) parsedValue);
            case BOOLEAN: return parsedValue instanceof Boolean;
            case TIME_RANGE: return parsedValue instanceof TimeRange;
            default: return false;
        }
    }

    /** 类型检查或显式类型读取；调用方须先核对值类型。 */
    public boolean isString() {
        return type == ValueType.STRING;
    }

    /** 类型检查或显式类型读取；调用方须先核对值类型。 */
    public boolean isInteger() {
        return type == ValueType.INTEGER;
    }

    /** 类型检查或显式类型读取；调用方须先核对值类型。 */
    public boolean isDecimal() {
        return type == ValueType.DECIMAL;
    }

    /** 类型检查或显式类型读取；调用方须先核对值类型。 */
    public boolean isNumber() {
        return isInteger() || isDecimal();
    }

    /** 类型检查或显式类型读取；调用方须先核对值类型。 */
    public boolean isBoolean() {
        return type == ValueType.BOOLEAN;
    }

    /** 类型检查或显式类型读取；调用方须先核对值类型。 */
    public boolean isTimeRange() {
        return type == ValueType.TIME_RANGE;
    }

    /** 类型检查或显式类型读取；调用方须先核对值类型。 */
    public boolean isNull() {
        return type == ValueType.NULL;
    }

    // ========== 便捷获取方法 ==========

    /** 类型检查或显式类型读取；调用方须先核对值类型。 */
    public String asString() {
        return (String) parsedValue;
    }

    /** 类型检查或显式类型读取；调用方须先核对值类型。 */
    public Long asInteger() {
        return (Long) parsedValue;
    }

    /** 类型检查或显式类型读取；调用方须先核对值类型。 */
    public Double asDecimal() {
        return (Double) parsedValue;
    }

    /** 类型检查或显式类型读取；调用方须先核对值类型。 */
    public Boolean asBoolean() {
        return (Boolean) parsedValue;
    }

    /** 类型检查或显式类型读取；调用方须先核对值类型。 */
    public TimeRange asTimeRange() {
        return (TimeRange) parsedValue;
    }
}
