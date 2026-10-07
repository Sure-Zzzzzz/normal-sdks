package io.github.surezzzzzz.sdk.limiter.redis.smart.management.client.resttemplate.annotation;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.*;

/**
 * 限流管理客户端 RestTemplate 组件扫描注解
 *
 * @author surezzzzzz
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ComponentScan
public @interface SmartRedisLimiterManagementClientRestTemplateComponent {

    /**
     * 扫描基础包
     *
     * @return 基础包
     */
    @AliasFor(annotation = ComponentScan.class, attribute = "basePackageClasses")
    Class<?>[] value() default {};
}
