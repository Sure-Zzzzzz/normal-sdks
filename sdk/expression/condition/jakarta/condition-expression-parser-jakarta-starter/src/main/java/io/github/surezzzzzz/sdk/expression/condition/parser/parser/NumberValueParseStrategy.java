package io.github.surezzzzzz.sdk.expression.condition.parser.parser;

import io.github.surezzzzzz.sdk.expression.condition.parser.constant.*;
import io.github.surezzzzzz.sdk.expression.condition.parser.exception.ConditionExpressionParseException;
import io.github.surezzzzzz.sdk.expression.condition.parser.model.ValueNode;
import java.util.regex.Pattern;

/** Long 和有限 Double 策略；溢出不降为字符串。 */
public class NumberValueParseStrategy implements ValueParseStrategy {
    private static final Pattern NUMBER_PATTERN = Pattern.compile(ConditionExpressionParserConstant.NUMBER_PATTERN);
    /** 与语法的 ASCII 数字规则一致。 */
    @Override
    public boolean canParse(String rawValue) { return rawValue != null && NUMBER_PATTERN.matcher(rawValue).matches(); }
    /** 转换溢出或非有限值时返回安全错误。 */
    @Override
    public ValueNode parse(String rawValue) {
        try {
            if (!canParse(rawValue)) throw new NumberFormatException();
            if (rawValue.contains(ConditionExpressionParserConstant.DECIMAL_SEPARATOR)) {
                double value = Double.parseDouble(rawValue);
                if (!Double.isFinite(value)) throw new NumberFormatException();
                return ValueNode.builder().type(ValueType.DECIMAL).rawValue(rawValue).parsedValue(value).build();
            }
            return ValueNode.builder().type(ValueType.INTEGER).rawValue(rawValue)
                    .parsedValue(Long.parseLong(rawValue)).build();
        } catch (NumberFormatException ex) {
            throw ConditionExpressionParseException.builder(ConditionExpressionParseException.ErrorType.INVALID_VALUE).build();
        }
    }
    /** 数字优先级。 */
    @Override
    public int getPriority() { return ConditionExpressionParserConstant.PRIORITY_NUMBER; }
}
