package io.github.surezzzzzz.sdk.limiter.redis.smart.management.client.mutation.resttemplate.annotation;

import org.springframework.beans.factory.annotation.Qualifier;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 管理面策略变更客户端组件标记（组件扫描 includeFilters 专用，不做通用用途）
 *
 * @author surezzzzzz
 */
@Qualifier
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface SmartRedisLimiterPolicyMutationClientRestTemplateComponent {

    /**
     * Bean 名称前缀（缺省空）
     */
    String value() default "";
}
