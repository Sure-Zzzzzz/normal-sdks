package io.github.surezzzzzz.sdk.audit.license.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Simple License Audit Listener 组件注解
 *
 * <p>仅本模块内部组件使用，宿主自定义 {@code LicenseAuditHandler} 无需标注。
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface SimpleLicenseAuditListenerComponent {
}
