package io.github.surezzzzzz.sdk.elasticsearch.search.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 仅标记本模块组件，不扩大宿主扫描范围。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface SimpleElasticsearchSearchComponent {
}
