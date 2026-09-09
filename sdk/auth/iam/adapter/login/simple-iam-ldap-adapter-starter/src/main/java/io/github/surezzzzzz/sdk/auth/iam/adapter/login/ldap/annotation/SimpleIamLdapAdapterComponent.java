package io.github.surezzzzzz.sdk.auth.iam.adapter.login.ldap.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Simple IAM LDAP Adapter Component Annotation
 *
 * <p>标记 LDAP 适配器组件，由
 * {@code SimpleIamLdapAdapterAutoConfiguration} 的精准扫描装配。
 *
 * @author surezzzzzz
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface SimpleIamLdapAdapterComponent {
}
