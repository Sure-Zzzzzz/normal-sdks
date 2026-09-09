package io.github.surezzzzzz.sdk.auth.iam.adapter.login.oidc.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Simple IAM OIDC Adapter Component Annotation
 *
 * <p>标记 OIDC 适配器组件，由
 * {@code SimpleIamOidcAdapterAutoConfiguration} 的精准扫描装配。
 *
 * @author surezzzzzz
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface SimpleIamOidcAdapterComponent {
}
