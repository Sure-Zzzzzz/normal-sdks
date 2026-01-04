package io.github.surezzzzzz.sdk.expression.condition.parser.parser;

import io.github.surezzzzzz.sdk.expression.condition.parser.annotation.ConditionExpressionParserComponent;
import io.github.surezzzzzz.sdk.expression.condition.parser.constant.ValueType;
import io.github.surezzzzzz.sdk.expression.condition.parser.model.ValueNode;
import org.springframework.core.annotation.Order;

import java.util.HashSet;
import java.util.Set;

/**
 * 布尔值解析策略
 * 优先级：1（最高）
 *
 * @author surezzzzzz
 */
@Order(1)
@ConditionExpressionParserComponent
public class BooleanValueParseStrategy implements ValueParseStrategy {

    private static final Set<String> TRUE_VALUES = new HashSet<>();
    private static final Set<String> FALSE_VALUES = new HashSet<>();

    static {
        TRUE_VALUES.add("true");
        TRUE_VALUES.add("TRUE");
        TRUE_VALUES.add("真");

        FALSE_VALUES.add("false");
        FALSE_VALUES.add("FALSE");
        FALSE_VALUES.add("假");
        FALSE_VALUES.add("否");
    }

    @Override
    public boolean canParse(String rawValue) {
        return TRUE_VALUES.contains(rawValue) || FALSE_VALUES.contains(rawValue);
    }

    @Override
    public ValueNode parse(String rawValue) {
        boolean value = TRUE_VALUES.contains(rawValue);
        return ValueNode.builder()
                .type(ValueType.BOOLEAN)
                .rawValue(rawValue)
                .parsedValue(value)
                .build();
    }

    @Override
    public int getPriority() {
        return 1;
    }
}
