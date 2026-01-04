package io.github.surezzzzzz.sdk.expression.condition.parser.parser;

import io.github.surezzzzzz.sdk.expression.condition.parser.annotation.ConditionExpressionParserComponent;
import io.github.surezzzzzz.sdk.expression.condition.parser.model.ValueNode;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;

import java.util.List;

/**
 * 值解析器（策略模式管理器）
 * <p>
 * 根据优先级顺序尝试各个策略，直到找到能够解析的策略
 *
 * @author surezzzzzz
 */
@ConditionExpressionParserComponent
public class ValueParser {

    private final List<ValueParseStrategy> strategies;

    public ValueParser(List<ValueParseStrategy> strategies) {
        // 按优先级排序（使用Spring的Order注解）
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
                    .type(io.github.surezzzzzz.sdk.expression.condition.parser.constant.ValueType.NULL)
                    .rawValue(null)
                    .parsedValue(null)
                    .build();
        }

        // 按优先级尝试各个策略
        for (ValueParseStrategy strategy : strategies) {
            if (strategy.canParse(rawValue)) {
                return strategy.parse(rawValue);
            }
        }

        // 理论上不会到这里，因为 StringValueParseStrategy 是兜底策略
        throw new IllegalStateException("No strategy found for value: " + rawValue);
    }
}
