package io.github.surezzzzzz.sdk.elasticsearch.route.support;

import lombok.extern.slf4j.Slf4j;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

/**
 * Spring EL 表达式解析器
 * 用于解析 @Document 注解中的 SpEL 表达式
 *
 * @author surezzzzzz
 */
@Slf4j
public class SpELResolver {

    private static final ExpressionParser PARSER = new SpelExpressionParser();

    /**
     * 判断字符串是否是 SpEL 表达式
     *
     * @param value 待判断的字符串
     * @return 如果是 SpEL 表达式返回 true
     */
    public static boolean isSpEL(String value) {
        return value != null && value.startsWith("#{") && value.endsWith("}");
    }

    /**
     * 解析 SpEL 表达式
     *
     * @param expression SpEL 表达式字符串（如：#{T(xxx).method()}）
     * @return 解析后的值，如果解析失败返回原始字符串
     */
    public static String resolve(String expression) {
        if (!isSpEL(expression)) {
            return expression;
        }

        try {
            // 去掉 #{ 和 }
            String expr = expression.substring(2, expression.length() - 1);

            // 解析表达式
            Object value = PARSER.parseExpression(expr).getValue(new StandardEvaluationContext());

            String result = value != null ? value.toString() : null;

            log.debug("Resolved SpEL expression [{}] -> [{}]", expression, result);

            return result;

        } catch (Exception e) {
            log.warn("Failed to resolve SpEL expression [{}], using original value. Error: {}",
                    expression, e.getMessage());
            return expression;
        }
    }
}
