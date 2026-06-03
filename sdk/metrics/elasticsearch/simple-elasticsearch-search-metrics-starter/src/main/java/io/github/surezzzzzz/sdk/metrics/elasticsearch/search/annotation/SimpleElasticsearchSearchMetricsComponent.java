package io.github.surezzzzzz.sdk.metrics.elasticsearch.search.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * simple-elasticsearch-search 指标组件扫描注解
 *
 * @author surezzzzzz
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface SimpleElasticsearchSearchMetricsComponent {
}