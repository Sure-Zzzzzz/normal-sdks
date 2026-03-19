package io.github.surezzzzzz.sdk.cache.annotation;

import io.github.surezzzzzz.sdk.cache.constant.SmartCacheConstant;

import java.lang.annotation.*;

/**
 * Smart Cacheable Annotation
 * <p>
 * 查询缓存注解
 * </p>
 *
 * @author Sure
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SmartCacheable {

    /**
     * 缓存名称
     */
    String cacheName() default SmartCacheConstant.DEFAULT_CACHE_NAME;

    /**
     * 缓存 key（支持 SpEL 表达式）
     */
    String key();

    /**
     * 条件（支持 SpEL 表达式）
     */
    String condition() default "";
}
