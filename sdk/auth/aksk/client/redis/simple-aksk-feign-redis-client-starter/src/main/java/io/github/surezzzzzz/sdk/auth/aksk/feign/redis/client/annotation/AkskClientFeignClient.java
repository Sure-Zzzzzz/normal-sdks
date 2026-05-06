package io.github.surezzzzzz.sdk.auth.aksk.feign.redis.client.annotation;

import io.github.surezzzzzz.sdk.auth.aksk.feign.redis.client.configuration.AkskFeignConfiguration;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.*;

/**
 * AKSK Client Feign Client
 *
 * <p>自定义 FeignClient 注解，自动配置 AKSK 认证拦截器。
 *
 * <p>使用此注解的 FeignClient 会自动添加 Authorization 请求头。
 *
 * <p>使用示例：
 * <pre>{@code
 * @AkskClientFeignClient(name = "my-service", url = "http://localhost:8080")
 * public interface MyServiceClient {
 *     @GetMapping("/api/resource")
 *     String getResource();
 * }
 * }</pre>
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@FeignClient
public @interface AkskClientFeignClient {

    /**
     * The name of the service with optional protocol prefix.
     */
    @AliasFor(annotation = FeignClient.class, attribute = "name")
    String name() default "";

    /**
     * The service id with optional protocol prefix. Synonym for {@link #name() name}.
     */
    @AliasFor(annotation = FeignClient.class, attribute = "value")
    String value() default "";

    /**
     * An absolute URL or resolvable hostname (the protocol is optional).
     */
    @AliasFor(annotation = FeignClient.class, attribute = "url")
    String url() default "";

    /**
     * Whether to mark the feign proxy as a primary bean.
     */
    @AliasFor(annotation = FeignClient.class, attribute = "primary")
    boolean primary() default true;

    /**
     * A custom configuration class for the feign client.
     * Automatically includes AkskFeignConfiguration.
     */
    @AliasFor(annotation = FeignClient.class, attribute = "configuration")
    Class<?>[] configuration() default {AkskFeignConfiguration.class};

    /**
     * Path prefix to be used by all method-level mappings.
     */
    @AliasFor(annotation = FeignClient.class, attribute = "path")
    String path() default "";

    /**
     * Whether to decode 404 responses.
     */
    @AliasFor(annotation = FeignClient.class, attribute = "decode404")
    boolean decode404() default false;

    /**
     * Fallback class for the specified Feign client interface.
     */
    @AliasFor(annotation = FeignClient.class, attribute = "fallback")
    Class<?> fallback() default void.class;

    /**
     * Define a fallback factory for the specified Feign client interface.
     */
    @AliasFor(annotation = FeignClient.class, attribute = "fallbackFactory")
    Class<?> fallbackFactory() default void.class;

    /**
     * Qualifier value for the feign client.
     */
    @AliasFor(annotation = FeignClient.class, attribute = "qualifier")
    String qualifier() default "";
}
