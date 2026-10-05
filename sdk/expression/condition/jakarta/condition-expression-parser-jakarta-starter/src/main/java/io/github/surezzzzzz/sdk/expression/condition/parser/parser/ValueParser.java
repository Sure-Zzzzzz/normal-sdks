package io.github.surezzzzzz.sdk.expression.condition.parser.parser;

import io.github.surezzzzzz.sdk.expression.condition.parser.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.expression.condition.parser.constant.ValueType;
import io.github.surezzzzzz.sdk.expression.condition.parser.exception.ConditionExpressionParseException;
import io.github.surezzzzzz.sdk.expression.condition.parser.model.ValueNode;
import lombok.Value;
import org.springframework.aop.framework.AopProxyUtils;
import java.util.*;
import static io.github.surezzzzzz.sdk.expression.condition.parser.exception.ConditionExpressionParseException.ErrorType.INVALID_VALUE;

/** 值策略注册表；复制并冻结排序信息，自定义策略须自行保证线程安全。 */
public class ValueParser {
    private final List<Registration> strategies;

    /** 不修改传入列表；同目标类重复注册拒绝，并列按类名确定性排序。 */
    public ValueParser(List<ValueParseStrategy> strategies) {
        if (strategies == null) throw new IllegalArgumentException(ErrorMessage.INVALID_CONFIGURATION);
        List<Registration> copy = new ArrayList<>();
        Set<Class<?>> classes = new HashSet<>();
        for (ValueParseStrategy strategy : strategies) {
            if (strategy == null) throw new IllegalArgumentException(ErrorMessage.INVALID_CONFIGURATION);
            Class<?> type = AopProxyUtils.ultimateTargetClass(strategy);
            if (!classes.add(type)) throw new IllegalArgumentException(ErrorMessage.DUPLICATE_STRATEGY);
            copy.add(new Registration(strategy, strategy.getPriority(), type.getName()));
        }
        copy.sort(Comparator.comparingInt(Registration::getPriority).thenComparing(Registration::getClassName));
        this.strategies = Collections.unmodifiableList(copy);
    }

    /** null 返回 NULL 节点；命中策略失败不回退字符串，也不暴露第三方原因。 */
    public ValueNode parse(String rawValue) {
        if (rawValue == null) return ValueNode.builder().type(ValueType.NULL).build();
        try {
            for (Registration entry : strategies) {
                if (entry.strategy.canParse(rawValue)) {
                    ValueNode result = entry.strategy.parse(rawValue);
                    if (result == null || !result.isValid())
                        throw ConditionExpressionParseException.builder(INVALID_VALUE).build();
                    return result;
                }
            }
        } catch (RuntimeException ex) {
            throw ConditionExpressionParseException.builder(INVALID_VALUE).build();
        }
        throw ConditionExpressionParseException.builder(INVALID_VALUE).build();
    }

    @Value
    private static class Registration {
        ValueParseStrategy strategy;
        int priority;
        String className;
    }
}
