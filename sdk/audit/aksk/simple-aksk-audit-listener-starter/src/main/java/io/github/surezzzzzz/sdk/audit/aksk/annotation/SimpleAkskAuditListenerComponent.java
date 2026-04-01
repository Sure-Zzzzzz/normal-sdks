package io.github.surezzzzzz.sdk.audit.aksk.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Simple AKSK Audit Listener Component Annotation
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface SimpleAkskAuditListenerComponent {
}
