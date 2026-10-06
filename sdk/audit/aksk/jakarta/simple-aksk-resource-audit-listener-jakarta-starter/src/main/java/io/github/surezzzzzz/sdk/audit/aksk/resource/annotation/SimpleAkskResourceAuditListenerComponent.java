package io.github.surezzzzzz.sdk.audit.aksk.resource.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记本模块通过精确扫描注册的组件；有 Bean 条件的监听器由自动配置单独注册。
 *
 * @author surezzzzzz
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface SimpleAkskResourceAuditListenerComponent {
}
