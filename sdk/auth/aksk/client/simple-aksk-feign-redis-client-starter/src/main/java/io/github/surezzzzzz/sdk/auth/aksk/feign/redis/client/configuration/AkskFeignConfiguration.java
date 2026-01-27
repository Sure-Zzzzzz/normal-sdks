package io.github.surezzzzzz.sdk.auth.aksk.feign.redis.client.configuration;

import feign.RequestInterceptor;
import io.github.surezzzzzz.sdk.auth.aksk.feign.redis.client.interceptor.AkskFeignRequestInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AKSK Feign Configuration
 *
 * <p>预配置的 Feign 配置类，自动注入 AKSK 认证拦截器。
 *
 * <p>使用方式1：通过 @AkskClientFeignClient 注解（推荐）
 * <pre>{@code
 * @AkskClientFeignClient(name = "my-service", url = "http://localhost:8080")
 * public interface MyServiceClient {
 *     @GetMapping("/api/resource")
 *     String getResource();
 * }
 * }</pre>
 *
 * <p>使用方式2：显式配置（备选）
 * <pre>{@code
 * @FeignClient(
 *     name = "my-service",
 *     url = "http://localhost:8080",
 *     configuration = AkskFeignConfiguration.class
 * )
 * public interface MyServiceClient {
 *     @GetMapping("/api/resource")
 *     String getResource();
 * }
 * }</pre>
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@Configuration
public class AkskFeignConfiguration {

    @Autowired
    private AkskFeignRequestInterceptor akskFeignRequestInterceptor;

    /**
     * 注册 AKSK 认证拦截器
     *
     * @return RequestInterceptor
     */
    @Bean
    public RequestInterceptor akskRequestInterceptor() {
        return akskFeignRequestInterceptor;
    }
}
