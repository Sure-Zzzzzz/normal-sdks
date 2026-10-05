package io.github.surezzzzzz.sdk.audit.http.xff.es.persistence.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * XFF Capture 审计 Elasticsearch Provider 内部组件标记。
 *
 * @author surezzzzzz
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface SimpleXffCaptureAuditEsPersistenceProviderComponent {
}
