package io.github.surezzzzzz.sdk.limiter.redis.smart.annotation;

import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterConstant;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author: Sure.
 * @description 智能限流注解
 * @Date: 2026-05-08
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SmartRedisLimiter {

    /**
     * 限流规则
     */
    SmartRedisLimitRule[] rules();

    /**
     * Key生成策略
     */
    String keyStrategy() default "";

    /**
     * 限流算法
     */
    String algorithm() default SmartRedisLimiterConstant.ALGORITHM_FIXED;

    /**
     * 注解级别降级策略
     */
    String fallback() default "";  // 空字符串表示使用默认值
}
