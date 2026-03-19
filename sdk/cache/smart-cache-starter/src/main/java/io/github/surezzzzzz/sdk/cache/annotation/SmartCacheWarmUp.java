package io.github.surezzzzzz.sdk.cache.annotation;

import java.lang.annotation.*;

/**
 * Smart Cache WarmUp Annotation
 * <p>
 * 预热注解
 * </p>
 *
 * @author Sure
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SmartCacheWarmUp {

    /**
     * 缓存名称
     */
    String cacheName();

    /**
     * 预热顺序（值越小，优先级越高）
     */
    int order() default 0;
}
