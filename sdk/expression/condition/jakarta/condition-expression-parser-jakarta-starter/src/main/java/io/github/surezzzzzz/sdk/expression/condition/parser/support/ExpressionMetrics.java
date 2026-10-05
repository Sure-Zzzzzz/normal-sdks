package io.github.surezzzzzz.sdk.expression.condition.parser.support;

import io.github.surezzzzzz.sdk.expression.condition.parser.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.expression.condition.parser.exception.ExpressionValidationException;
import io.github.surezzzzzz.sdk.expression.condition.parser.model.Expression;

/** 逻辑树度量：括号透明；统计前进行有界遍历和结构校验。 */
public final class ExpressionMetrics {
    private ExpressionMetrics() { }
    /** 空根为零；逻辑深度不包括括号，与解析器实际树深度不同。 */
    public static int calculateDepth(Expression expr) { return ExpressionTreeHelper.walk(expr, null).getLogicalDepth(); }
    /** 共享子树每次出现都计入条件数量。 */
    public static int countConditions(Expression expr) { return ExpressionTreeHelper.walk(expr, null).getConditions(); }
    /** 指定正数逻辑深度上限；默认遍历容量仍适用。 */
    public static void validateDepth(Expression expr, int maxDepth) {
        if (maxDepth <= 0) throw new IllegalArgumentException(ErrorMessage.INVALID_CONFIGURATION);
        int depth = calculateDepth(expr);
        if (depth > maxDepth) throw new ExpressionValidationException(ExpressionValidationException.MetricType.DEPTH, depth, maxDepth);
    }
    /** 指定正数条件上限；默认遍历容量仍适用。 */
    public static void validateConditionCount(Expression expr, int maxConditions) {
        if (maxConditions <= 0) throw new IllegalArgumentException(ErrorMessage.INVALID_CONFIGURATION);
        int count = countConditions(expr);
        if (count > maxConditions) throw new ExpressionValidationException(ExpressionValidationException.MetricType.CONDITION_COUNT, count, maxConditions);
    }
}
