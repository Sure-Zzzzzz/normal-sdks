package io.github.surezzzzzz.sdk.limiter.redis.smart.annotation;

import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterStarterConstant;

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
     * 稳定资源编码，用于匹配远程动态策略
     *
     * @return 资源编码；空字符串表示仅使用本地策略
     */
    String resourceCode() default SmartRedisLimiterStarterConstant.DEFAULT_RESOURCE_CODE;

    /**
     * 限流规则
     *
     * @return 限流规则数组
     */
    SmartRedisLimitRule[] rules();

    /**
     * 获取 Key 生成策略
     *
     * @return Key 生成策略；空字符串表示使用配置默认值
     */
    String keyStrategy() default "";

    /**
     * 获取限流算法
     *
     * @return 限流算法
     */
    String algorithm() default SmartRedisLimiterConstant.ALGORITHM_FIXED;

    /**
     * 获取注解级降级策略
     *
     * @return 降级策略；空字符串表示使用配置默认值
     */
    String fallback() default "";
}
