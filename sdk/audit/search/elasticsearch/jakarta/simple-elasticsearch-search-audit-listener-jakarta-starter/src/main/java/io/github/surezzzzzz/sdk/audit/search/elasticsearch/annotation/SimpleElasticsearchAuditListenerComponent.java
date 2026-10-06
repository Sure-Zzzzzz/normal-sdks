package io.github.surezzzzzz.sdk.audit.search.elasticsearch.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Elasticsearch Search 审计组件注解
 *
 * @author surezzzzzz
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface SimpleElasticsearchAuditListenerComponent {
}
