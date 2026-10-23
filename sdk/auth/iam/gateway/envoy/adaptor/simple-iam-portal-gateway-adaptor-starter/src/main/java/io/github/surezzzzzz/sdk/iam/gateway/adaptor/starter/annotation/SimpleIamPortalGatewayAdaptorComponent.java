package io.github.surezzzzzz.sdk.iam.gateway.adaptor.starter.annotation;

import org.springframework.stereotype.Component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Portal Gateway adaptor 的受控组件标记。
 */
@Component
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface SimpleIamPortalGatewayAdaptorComponent {
}
