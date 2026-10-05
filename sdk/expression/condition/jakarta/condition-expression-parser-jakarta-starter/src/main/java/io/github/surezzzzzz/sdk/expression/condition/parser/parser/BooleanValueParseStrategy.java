package io.github.surezzzzzz.sdk.expression.condition.parser.parser;

import io.github.surezzzzzz.sdk.expression.condition.parser.constant.*;
import io.github.surezzzzzz.sdk.expression.condition.parser.exception.ConditionExpressionParseException;
import io.github.surezzzzzz.sdk.expression.condition.parser.model.ValueNode;
import java.util.Locale;

/** 大小写不敏感的布尔策略，与词法器规则一致。 */
public class BooleanValueParseStrategy implements ValueParseStrategy {
    /** 识别英文及中文布尔别名。 */
    @Override
    public boolean canParse(String rawValue) {
        if (rawValue == null) return false;
        String text = rawValue.toLowerCase(Locale.ROOT);
        return ConditionExpressionParserConstant.TRUE.equals(text)
                || ConditionExpressionParserConstant.FALSE.equals(text)
                || ConditionExpressionParserConstant.TRUE_ALIAS.equals(text)
                || ConditionExpressionParserConstant.FALSE_ALIAS.equals(text)
                || ConditionExpressionParserConstant.FALSE_ALIAS_NO.equals(text);
    }
    /** 非布尔文本拒绝，保留原文本。 */
    @Override
    public ValueNode parse(String rawValue) {
        if (!canParse(rawValue)) throw ConditionExpressionParseException.invalidBooleanValue(null,
                ConditionExpressionParserConstant.UNKNOWN_POSITION, ConditionExpressionParserConstant.UNKNOWN_POSITION, rawValue);
        boolean value = ConditionExpressionParserConstant.TRUE.equals(rawValue.toLowerCase(Locale.ROOT))
                || ConditionExpressionParserConstant.TRUE_ALIAS.equals(rawValue);
        return ValueNode.builder().type(ValueType.BOOLEAN).rawValue(rawValue).parsedValue(value).build();
    }
    /** 数字越小越先执行。 */
    @Override
    public int getPriority() { return ConditionExpressionParserConstant.PRIORITY_BOOLEAN; }
}
