package io.github.surezzzzzz.sdk.expression.condition.parser.parser;

import io.github.surezzzzzz.sdk.expression.condition.parser.annotation.ConditionExpressionParserComponent;
import io.github.surezzzzzz.sdk.expression.condition.parser.constant.ValueType;
import io.github.surezzzzzz.sdk.expression.condition.parser.exception.ConditionExpressionParseException;
import io.github.surezzzzzz.sdk.expression.condition.parser.model.ValueNode;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;

import java.util.List;

/**
 * 值解析器（策略模式管理器）
 *
 * @author surezzzzzz
 */
@ConditionExpressionParserComponent
public class ValueParser {

    private final List<ValueParseStrategy> strategies;

    public ValueParser(List<ValueParseStrategy> strategies) {
        AnnotationAwareOrderComparator.sort(strategies);
        this.strategies = strategies;
    }

    /**
     * 解析值
     *
     * @param rawValue 原始值字符串
     * @return 解析后的值节点
     */
    public ValueNode parse(String rawValue) {
        if (rawValue == null) {
            return ValueNode.builder()
                    .type(ValueType.NULL)
                    .rawValue(null)
                    .parsedValue(null)
                    .build();
        }

        for (ValueParseStrategy strategy : strategies) {
            if (strategy.canParse(rawValue)) {
                return strategy.parse(rawValue);
            }
        }

        // 理论上不会到这里，因为 StringValueParseStrategy 是兜底策略
        throw ConditionExpressionParseException.builder(ConditionExpressionParseException.ErrorType.INVALID_VALUE)
                .message("无法解析的值: " + rawValue)
                .build();
    }
}
