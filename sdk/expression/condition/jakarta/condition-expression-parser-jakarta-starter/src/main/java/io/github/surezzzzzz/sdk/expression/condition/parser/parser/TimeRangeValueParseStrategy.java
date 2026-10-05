package io.github.surezzzzzz.sdk.expression.condition.parser.parser;

import io.github.surezzzzzz.sdk.expression.condition.parser.constant.ConditionExpressionParserConstant;
import io.github.surezzzzzz.sdk.expression.condition.parser.exception.ConditionExpressionParseException;
import io.github.surezzzzzz.sdk.expression.condition.parser.constant.TimeRange;
import io.github.surezzzzzz.sdk.expression.condition.parser.constant.ValueType;
import io.github.surezzzzzz.sdk.expression.condition.parser.model.ValueNode;

/**
 * 时间范围解析策略
 * 优先级：2
 *
 * @author surezzzzzz
 */
public class TimeRangeValueParseStrategy implements ValueParseStrategy {

    @Override
    public boolean canParse(String rawValue) {
        return TimeRange.isKeyword(rawValue);
    }

    @Override
    public ValueNode parse(String rawValue) {
        TimeRange timeRange = TimeRange.fromKeyword(rawValue);
        if (timeRange == null) throw ConditionExpressionParseException.invalidTimeRange(null,
                ConditionExpressionParserConstant.UNKNOWN_POSITION, ConditionExpressionParserConstant.UNKNOWN_POSITION, rawValue);
        return ValueNode.builder()
                .type(ValueType.TIME_RANGE)
                .rawValue(rawValue)
                .parsedValue(timeRange)
                .build();
    }

    @Override
    public int getPriority() {
        return ConditionExpressionParserConstant.PRIORITY_TIME;
    }
}
