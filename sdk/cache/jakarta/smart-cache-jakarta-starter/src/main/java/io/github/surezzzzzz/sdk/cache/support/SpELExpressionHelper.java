package io.github.surezzzzzz.sdk.cache.support;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.surezzzzzz.sdk.cache.constant.SmartCacheConstant;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

/**
 * SpEL Expression Helper
 * <p>
 * SpEL 表达式解析工具
 * </p>
 *
 * @author Sure
 */
public class SpELExpressionHelper {

    private static final ExpressionParser PARSER = new SpelExpressionParser();
    private static final ParameterNameDiscoverer NAME_DISCOVERER = new DefaultParameterNameDiscoverer();

    /**
     * 表达式缓存，使用 Caffeine 限制大小，避免内存泄漏
     * 最多缓存 1000 个表达式，1 小时后过期
     */
    private static final Cache<String, Expression> EXPRESSION_CACHE = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterAccess(1, TimeUnit.HOURS)
            .build();

    /**
     * 解析 SpEL 表达式
     */
    public static String parseExpression(String expressionString, Method method, Object[] args, Object result) {
        if (expressionString == null || expressionString.isEmpty()) {
            return "";
        }

        Expression expression = EXPRESSION_CACHE.get(expressionString, PARSER::parseExpression);
        EvaluationContext context = createEvaluationContext(method, args, result);
        Object value = expression.getValue(context);
        return value != null ? value.toString() : "";
    }

    /**
     * 解析条件表达式
     */
    public static boolean parseCondition(String conditionString, Method method, Object[] args, Object result) {
        if (conditionString == null || conditionString.isEmpty()) {
            return true;
        }

        Expression expression = EXPRESSION_CACHE.get(conditionString, PARSER::parseExpression);
        EvaluationContext context = createEvaluationContext(method, args, result);
        Boolean value = expression.getValue(context, Boolean.class);
        return value != null && value;
    }

    /**
     * 创建 EvaluationContext
     * 使用 SimpleEvaluationContext 替代 StandardEvaluationContext，防止 SpEL 注入攻击
     */
    private static EvaluationContext createEvaluationContext(Method method, Object[] args, Object result) {
        SimpleEvaluationContext context = SimpleEvaluationContext.forReadOnlyDataBinding().build();

        // 将方法参数绑定为 SpEL 变量（#paramName）
        String[] parameterNames = NAME_DISCOVERER.getParameterNames(method);
        if (parameterNames != null && args != null && parameterNames.length == args.length) {
            for (int i = 0; i < parameterNames.length; i++) {
                context.setVariable(parameterNames[i], args[i]);
            }
        }

        // 将方法返回值绑定为 #result
        if (result != null) {
            context.setVariable(SmartCacheConstant.SPEL_RESULT_VARIABLE, result);
        }

        return context;
    }
}
