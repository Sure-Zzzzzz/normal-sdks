package io.github.surezzzzzz.sdk.limiter.redis.smart.configuration;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author: Sure.
 * @description 组件扫描标记注解
 * @Date: 2024/12/XX XX:XX
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface SmartRedisLimiterComponent {
}
