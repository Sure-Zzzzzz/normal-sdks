package io.github.surezzzzzz.sdk.expression.condition.parser.parser;

import io.github.surezzzzzz.sdk.expression.condition.parser.annotation.ConditionExpressionParserComponent;
import io.github.surezzzzzz.sdk.expression.condition.parser.constant.TimeRange;
import io.github.surezzzzzz.sdk.expression.condition.parser.constant.ValueType;
import io.github.surezzzzzz.sdk.expression.condition.parser.model.ValueNode;
import org.springframework.core.annotation.Order;

/**
 * 时间范围解析策略
 * 优先级：2
 *
 * @author surezzzzzz
 */
@Order(2)
@ConditionExpressionParserComponent
public class TimeRangeValueParseStrategy implements ValueParseStrategy {

    @Override
    public boolean canParse(String rawValue) {
        return TimeRange.isKeyword(rawValue);
    }

    @Override
    public ValueNode parse(String rawValue) {
        TimeRange timeRange = TimeRange.fromKeyword(rawValue);
        return ValueNode.builder()
                .type(ValueType.TIME_RANGE)
                .rawValue(rawValue)
                .parsedValue(timeRange)
                .build();
    }

    @Override
    public int getPriority() {
        return 2;
    }
}
