package io.github.surezzzzzz.sdk.auth.aksk.feign.httpsession.client.annotation;

import io.github.surezzzzzz.sdk.auth.aksk.feign.httpsession.client.configuration.AkskHttpSessionFeignConfiguration;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.*;

/**
 * AKSK HttpSession Feign Client
 *
 * <p>使用 HttpSession Token Manager 的 FeignClient 注解封装
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@FeignClient
public @interface AkskHttpSessionFeignClient {

    @AliasFor(annotation = FeignClient.class, attribute = "name")
    String name() default "";

    @AliasFor(annotation = FeignClient.class, attribute = "value")
    String value() default "";

    @AliasFor(annotation = FeignClient.class, attribute = "url")
    String url() default "";

    @AliasFor(annotation = FeignClient.class, attribute = "primary")
    boolean primary() default true;

    @AliasFor(annotation = FeignClient.class, attribute = "configuration")
    Class<?>[] configuration() default {AkskHttpSessionFeignConfiguration.class};

    @AliasFor(annotation = FeignClient.class, attribute = "path")
    String path() default "";

    @AliasFor(annotation = FeignClient.class, attribute = "decode404")
    boolean decode404() default false;

    @AliasFor(annotation = FeignClient.class, attribute = "fallback")
    Class<?> fallback() default void.class;

    @AliasFor(annotation = FeignClient.class, attribute = "fallbackFactory")
    Class<?> fallbackFactory() default void.class;

    @AliasFor(annotation = FeignClient.class, attribute = "qualifier")
    String qualifier() default "";
}
