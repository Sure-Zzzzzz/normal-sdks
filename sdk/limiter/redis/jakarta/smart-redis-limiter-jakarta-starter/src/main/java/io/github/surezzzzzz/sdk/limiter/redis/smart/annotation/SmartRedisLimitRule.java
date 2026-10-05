package io.github.surezzzzzz.sdk.limiter.redis.smart.annotation;

import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterTimeUnit;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * @author: Sure.
 * @description 限流规则注解
 * @Date: 2026-05-08
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface SmartRedisLimitRule {

    /**
     * 获取限流次数
     *
     * @return 限流次数
     */
    long count();

    /**
     * 获取时间窗口
     *
     * @return 时间窗口
     */
    long window();

    /**
     * 获取时间单位
     *
     * @return 时间单位
     */
    SmartRedisLimiterTimeUnit unit() default SmartRedisLimiterTimeUnit.SECONDS;
}
