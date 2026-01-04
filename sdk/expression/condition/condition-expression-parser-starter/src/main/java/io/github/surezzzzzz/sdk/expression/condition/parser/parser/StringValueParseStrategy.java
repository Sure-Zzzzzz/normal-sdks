package io.github.surezzzzzz.sdk.expression.condition.parser.parser;

import io.github.surezzzzzz.sdk.expression.condition.parser.annotation.ConditionExpressionParserComponent;
import io.github.surezzzzzz.sdk.expression.condition.parser.constant.ValueType;
import io.github.surezzzzzz.sdk.expression.condition.parser.model.ValueNode;
import org.springframework.core.annotation.Order;

/**
 * 字符串解析策略（兜底策略）
 * 优先级：99（最低，所有其他策略都无法解析时使用）
 *
 * @author surezzzzzz
 */
@Order(99)
@ConditionExpressionParserComponent
public class StringValueParseStrategy implements ValueParseStrategy {

    @Override
    public boolean canParse(String rawValue) {
        // 字符串策略总是可以解析（兜底）
        return true;
    }

    @Override
    public ValueNode parse(String rawValue) {
        return ValueNode.builder()
                .type(ValueType.STRING)
                .rawValue(rawValue)
                .parsedValue(rawValue)
                .build();
    }

    @Override
    public int getPriority() {
        return 99;
    }
}
