package io.github.surezzzzzz.sdk.expression.condition.parser.parser;

import io.github.surezzzzzz.sdk.expression.condition.parser.annotation.ConditionExpressionParserComponent;
import io.github.surezzzzzz.sdk.expression.condition.parser.constant.ValueType;
import io.github.surezzzzzz.sdk.expression.condition.parser.model.ValueNode;
import org.springframework.core.annotation.Order;

import java.util.regex.Pattern;

/**
 * 数字解析策略
 * 优先级：3
 *
 * @author surezzzzzz
 */
@Order(3)
@ConditionExpressionParserComponent
public class NumberValueParseStrategy implements ValueParseStrategy {

    private static final Pattern NUMBER_PATTERN = Pattern.compile("-?\\d+(\\.\\d+)?");

    @Override
    public boolean canParse(String rawValue) {
        return rawValue != null && NUMBER_PATTERN.matcher(rawValue).matches();
    }

    @Override
    public ValueNode parse(String rawValue) {
        if (rawValue.contains(".")) {
            // 浮点数
            return ValueNode.builder()
                    .type(ValueType.DECIMAL)
                    .rawValue(rawValue)
                    .parsedValue(Double.parseDouble(rawValue))
                    .build();
        } else {
            // 整数
            return ValueNode.builder()
                    .type(ValueType.INTEGER)
                    .rawValue(rawValue)
                    .parsedValue(Long.parseLong(rawValue))
                    .build();
        }
    }

    @Override
    public int getPriority() {
        return 3;
    }
}
