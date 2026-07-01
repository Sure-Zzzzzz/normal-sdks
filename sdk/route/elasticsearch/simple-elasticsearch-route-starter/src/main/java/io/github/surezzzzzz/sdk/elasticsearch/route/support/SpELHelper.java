package io.github.surezzzzzz.sdk.elasticsearch.route.support;

import lombok.extern.slf4j.Slf4j;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spring EL 表达式解析工具
 * 用于解析 @Document 注解中的 SpEL 表达式
 *
 * @author surezzzzzz
 */
@Slf4j
public class SpELHelper {

    private static final ExpressionParser PARSER = new SpelExpressionParser();
    /**
     * 缓存已解析的 Expression 对象（而非结果值）
     * 这样可以提升解析性能，同时支持依赖运行时上下文的表达式
     */
    private static final Map<String, Expression> SPEL_CACHE = new ConcurrentHashMap<>();

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
     * 解析 SpEL 表达式（带缓存）
     * 缓存的是编译后的 Expression 对象，而不是结果值
     * 这样可以支持依赖运行时上下文（如 RequestAttributes）的表达式
     *
     * @param expression SpEL 表达式字符串（如：#{T(xxx).method()}）
     * @return 解析后的值，如果解析失败返回原始字符串
     */
    public static String resolve(String expression) {
        if (!isSpEL(expression)) {
            return expression;
        }

        try {
            Expression compiledExpr = SPEL_CACHE.computeIfAbsent(expression, SpELHelper::compileExpression);
            Object value = compiledExpr.getValue(new StandardEvaluationContext());

            String result = value != null ? value.toString() : null;

            log.debug("SpEL 表达式解析完成，expression=[{}]，result=[{}]", expression, result);

            return result;

        } catch (Exception e) {
            log.warn("SpEL 表达式解析失败，使用原始值，expression=[{}]，error=[{}]",
                    expression, e.getMessage());
            return expression;
        }
    }

    /**
     * 编译 SpEL 表达式（仅在首次使用时调用，结果会被缓存）
     */
    private static Expression compileExpression(String expression) {
        String expr = expression.substring(2, expression.length() - 1);
        return PARSER.parseExpression(expr);
    }

    /**
     * 清除缓存
     */
    public static void clearCache() {
        SPEL_CACHE.clear();
        log.info("SpEL 表达式缓存已清空");
    }

    /**
     * 获取缓存大小
     */
    public static int getCacheSize() {
        return SPEL_CACHE.size();
    }
}
