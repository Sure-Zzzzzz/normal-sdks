package io.github.surezzzzzz.sdk.auth.aksk.feign.redis.client.configuration;

import feign.RequestInterceptor;
import io.github.surezzzzzz.sdk.auth.aksk.feign.redis.client.interceptor.AkskFeignRequestInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;

/**
 * AKSK Feign Configuration
 *
 * <p>Feign 客户端级别的配置类，将 AKSK 拦截器注册为 RequestInterceptor，
 * 使 Feign 在发送请求时自动添加 Authorization 请求头。
 *
 * <p>注意：此类不加 @Configuration，避免被 Spring 全局扫描注册，
 * 仅在 @AkskClientFeignClient 或显式指定 configuration 时生效于对应客户端的子上下文。
 *
 * @author surezzzzzz
 */
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
