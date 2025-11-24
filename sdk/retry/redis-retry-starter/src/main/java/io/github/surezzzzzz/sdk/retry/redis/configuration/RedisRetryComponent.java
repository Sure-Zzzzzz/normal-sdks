package io.github.surezzzzzz.sdk.retry.redis.configuration;

import org.springframework.stereotype.Component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Redis 重试组件注解
 *
 * @author: Sure.
 * @Date: 2025/3/11
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Component
public @interface RedisRetryComponent {
}
