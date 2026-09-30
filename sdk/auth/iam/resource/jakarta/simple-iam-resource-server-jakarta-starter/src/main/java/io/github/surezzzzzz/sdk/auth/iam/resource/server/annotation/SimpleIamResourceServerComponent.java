package io.github.surezzzzzz.sdk.auth.iam.resource.server.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Simple IAM Resource Server 组件标记注解
 *
 * <p>用于标记资源服务组件。
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface SimpleIamResourceServerComponent {
}
