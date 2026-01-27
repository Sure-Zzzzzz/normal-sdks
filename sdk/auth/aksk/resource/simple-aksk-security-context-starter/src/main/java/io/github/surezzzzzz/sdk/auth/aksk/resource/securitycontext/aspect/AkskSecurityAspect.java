package io.github.surezzzzzz.sdk.auth.aksk.resource.securitycontext.aspect;

import io.github.surezzzzzz.sdk.auth.aksk.resource.securitycontext.annotation.RequireContext;
import io.github.surezzzzzz.sdk.auth.aksk.resource.securitycontext.annotation.RequireExpression;
import io.github.surezzzzzz.sdk.auth.aksk.resource.securitycontext.annotation.RequireField;
import io.github.surezzzzzz.sdk.auth.aksk.resource.securitycontext.annotation.RequireFieldValue;
import io.github.surezzzzzz.sdk.auth.aksk.resource.securitycontext.annotation.SimpleAkskSecurityContextComponent;
import io.github.surezzzzzz.sdk.auth.aksk.resource.securitycontext.context.AkskUserContext;
import io.github.surezzzzzz.sdk.auth.aksk.resource.securitycontext.exception.AkskSecurityException;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * AKSK 安全切面
 *
 * <p>实现权限注解的 AOP 校验逻辑。
 *
 * <p>支持的注解：
 * <ul>
 *   <li>@RequireContext：要求存在安全上下文</li>
 *   <li>@RequireField：要求存在指定字段</li>
 *   <li>@RequireFieldValue：要求字段值匹配</li>
 *   <li>@RequireExpression：要求 SpEL 表达式为 true</li>
 * </ul>
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@Slf4j
@Aspect
@SimpleAkskSecurityContextComponent
public class AkskSecurityAspect {

    private final ExpressionParser parser = new SpelExpressionParser();

    /**
     * 校验 @RequireContext 注解
     */
    @Before("@annotation(io.github.surezzzzzz.sdk.auth.aksk.resource.securitycontext.annotation.RequireContext) || " +
            "@within(io.github.surezzzzzz.sdk.auth.aksk.resource.securitycontext.annotation.RequireContext)")
    public void checkRequireContext(JoinPoint joinPoint) {
        RequireContext annotation = getAnnotation(joinPoint, RequireContext.class);
        if (annotation == null) {
            return;
        }

        Map<String, String> context = AkskUserContext.getAll();
        if (context == null || context.isEmpty()) {
            String message = annotation.message();
            log.warn("Security context check failed: {}", message);
            throw new AkskSecurityException(message);
        }

        log.debug("Security context check passed");
    }

    /**
     * 校验 @RequireField 注解
     */
    @Before("@annotation(io.github.surezzzzzz.sdk.auth.aksk.resource.securitycontext.annotation.RequireField) || " +
            "@within(io.github.surezzzzzz.sdk.auth.aksk.resource.securitycontext.annotation.RequireField)")
    public void checkRequireField(JoinPoint joinPoint) {
        RequireField annotation = getAnnotation(joinPoint, RequireField.class);
        if (annotation == null) {
            return;
        }

        String fieldName = annotation.value();
        String fieldValue = AkskUserContext.get(fieldName);

        if (fieldValue == null || fieldValue.isEmpty()) {
            String message = annotation.message();
            if (message.isEmpty()) {
                message = "Required field '" + fieldName + "' is missing";
            }
            log.warn("Field check failed: {}", message);
            throw new AkskSecurityException(message);
        }

        log.debug("Field check passed: {} = {}", fieldName, fieldValue);
    }

    /**
     * 校验 @RequireFieldValue 注解
     */
    @Before("@annotation(io.github.surezzzzzz.sdk.auth.aksk.resource.securitycontext.annotation.RequireFieldValue) || " +
            "@within(io.github.surezzzzzz.sdk.auth.aksk.resource.securitycontext.annotation.RequireFieldValue)")
    public void checkRequireFieldValue(JoinPoint joinPoint) {
        RequireFieldValue annotation = getAnnotation(joinPoint, RequireFieldValue.class);
        if (annotation == null) {
            return;
        }

        String fieldName = annotation.field();
        String expectedValue = annotation.value();
        String actualValue = AkskUserContext.get(fieldName);

        if (!expectedValue.equals(actualValue)) {
            String message = annotation.message();
            if (message.isEmpty()) {
                message = "Field '" + fieldName + "' value mismatch: expected '" + expectedValue + "', actual '" + actualValue + "'";
            }
            log.warn("Field value check failed: {}", message);
            throw new AkskSecurityException(message);
        }

        log.debug("Field value check passed: {} = {}", fieldName, actualValue);
    }

    /**
     * 校验 @RequireExpression 注解
     */
    @Before("@annotation(io.github.surezzzzzz.sdk.auth.aksk.resource.securitycontext.annotation.RequireExpression) || " +
            "@within(io.github.surezzzzzz.sdk.auth.aksk.resource.securitycontext.annotation.RequireExpression)")
    public void checkRequireExpression(JoinPoint joinPoint) {
        RequireExpression annotation = getAnnotation(joinPoint, RequireExpression.class);
        if (annotation == null) {
            return;
        }

        String expression = annotation.value();
        Map<String, String> context = AkskUserContext.getAll();

        // 创建 SpEL 上下文
        StandardEvaluationContext evalContext = new StandardEvaluationContext();
        evalContext.setVariable("context", context);

        // 计算表达式
        Boolean result;
        try {
            result = parser.parseExpression(expression).getValue(evalContext, Boolean.class);
        } catch (Exception e) {
            log.error("Expression evaluation failed: {}", expression, e);
            throw new AkskSecurityException("Expression evaluation failed: " + e.getMessage());
        }

        if (result == null || !result) {
            String message = annotation.message();
            if (message.isEmpty()) {
                message = "Expression check failed: " + expression;
            }
            log.warn("Expression check failed: {}", message);
            throw new AkskSecurityException(message);
        }

        log.debug("Expression check passed: {}", expression);
    }

    /**
     * 获取注解（优先从方法获取，其次从类获取）
     */
    private <T extends java.lang.annotation.Annotation> T getAnnotation(JoinPoint joinPoint, Class<T> annotationClass) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        // 优先从方法获取
        T annotation = method.getAnnotation(annotationClass);
        if (annotation != null) {
            return annotation;
        }

        // 其次从类获取
        return method.getDeclaringClass().getAnnotation(annotationClass);
    }
}
