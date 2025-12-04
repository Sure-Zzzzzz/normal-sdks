package io.github.surezzzzzz.sdk.limiter.redis.configuration;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author: Sure.
 * @description
 * @Date: 2024/12/11 11:25
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface RedisLimiterComponent {
}