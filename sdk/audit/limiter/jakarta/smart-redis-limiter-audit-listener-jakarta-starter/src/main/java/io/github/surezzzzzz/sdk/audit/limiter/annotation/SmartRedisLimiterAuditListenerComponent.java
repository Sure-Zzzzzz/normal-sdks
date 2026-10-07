package io.github.surezzzzzz.sdk.audit.limiter.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * SmartRedisLimiter 限流审计监听器组件扫描注解
 *
 * @author surezzzzzz
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface SmartRedisLimiterAuditListenerComponent {
}
