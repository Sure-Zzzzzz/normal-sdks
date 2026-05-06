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
 * <h3>为什么不加 {@code @Configuration}？</h3>
 * <p>Spring Cloud Feign 为每个 {@code @FeignClient} 创建独立的子 ApplicationContext。
 * 如果此类加了 {@code @Configuration}，会被 Spring 全局扫描注册到父上下文，
 * 导致拦截器对所有 Feign 客户端生效，而不仅仅是使用 {@code @AkskClientFeignClient} 的客户端。
 * 不加 {@code @Configuration} 可以确保此配置只在显式指定时才生效于对应客户端的子上下文。
 *
 * <h3>为什么用 {@code @Autowired} 而不是自定义注解 + ComponentScan？</h3>
 * <p>{@code AkskFeignRequestInterceptor} 由父上下文的
 * {@link SimpleAkskFeignRedisClientAutoConfiguration} 通过 {@code @Bean} 方法注册。
 * Feign 子上下文可以从父上下文继承该 Bean，因此 {@code @Autowired} 能正常注入。
 * 若改为自定义注解 + ComponentScan 方式，Bean 注册在父上下文，
 * 理论上子上下文也能继承，但这依赖 Spring Cloud Feign 的子上下文继承行为，
 * 存在隐患，不如现有方式明确可靠。
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
