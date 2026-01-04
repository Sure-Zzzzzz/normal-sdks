package io.github.surezzzzzz.sdk.expression.condition.parser.annotation;

import org.springframework.stereotype.Component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Condition Expression Parser Component Annotation
 * 标记条件表达式解析器的组件，用于自动扫描和注册
 *
 * @author surezzzzzz
 */
@Component
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ConditionExpressionParserComponent {
}
