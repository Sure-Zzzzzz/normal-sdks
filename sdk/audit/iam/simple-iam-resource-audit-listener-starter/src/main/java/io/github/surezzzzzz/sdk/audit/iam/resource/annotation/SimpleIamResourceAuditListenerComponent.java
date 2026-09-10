package io.github.surezzzzzz.sdk.audit.iam.resource.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Simple IAM Resource Audit Listener 组件标记注解
 *
 * <p>用于标记 IAM 资源访问审计组件。
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface SimpleIamResourceAuditListenerComponent {
}
