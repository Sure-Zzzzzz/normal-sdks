package io.github.surezzzzzz.sdk.cache.annotation;

import io.github.surezzzzzz.sdk.cache.constant.SmartCacheConstant;

import java.lang.annotation.*;

/**
 * Smart Cache Evict Annotation
 * <p>
 * 删除缓存注解
 * </p>
 *
 * @author Sure
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SmartCacheEvict {

    /**
     * 缓存名称
     */
    String cacheName() default SmartCacheConstant.DEFAULT_CACHE_NAME;

    /**
     * 缓存 key（支持 SpEL 表达式）
     */
    String key() default "";

    /**
     * 是否清空所有缓存
     */
    boolean allEntries() default false;

    /**
     * 是否在方法执行前删除缓存
     * true: 方法执行前删除缓存（即使方法执行失败，缓存也会被删除）
     * false: 方法执行后删除缓存（默认，只有方法成功执行后才删除缓存）
     */
    boolean beforeInvocation() default false;

    /**
     * 条件（支持 SpEL 表达式）
     */
    String condition() default "";
}
