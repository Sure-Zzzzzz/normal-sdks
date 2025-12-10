package io.github.surezzzzzz.sdk.limiter.redis.smart.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.concurrent.TimeUnit;

/**
 * @author: Sure.
 * @description 限流规则注解
 * @Date: 2024/12/XX XX:XX
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface SmartRedisLimitRule {

    /**
     * 限流次数
     */
    int count();

    /**
     * 时间窗口
     */
    int window();

    /**
     * 时间单位
     */
    TimeUnit unit() default TimeUnit.SECONDS;
}
