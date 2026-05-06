package io.github.surezzzzzz.sdk.auth.aksk.feign.redis.client.configuration;

import feign.Feign;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.constant.SimpleAkskClientCoreConstant;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.manager.TokenManager;
import io.github.surezzzzzz.sdk.auth.aksk.feign.redis.client.interceptor.AkskFeignRequestInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;

/**
 * Simple AKSK Feign Redis Client Auto Configuration
 *
 * <p>Feign Client 的自动配置类
 *
 * <p>启用条件：
 * <ul>
 *   <li>io.github.surezzzzzz.sdk.auth.aksk.client.enable=true</li>
 *   <li>存在 Feign 类</li>
 *   <li>存在 TokenManager Bean（来自 simple-aksk-redis-token-manager）</li>
 * </ul>
 *
 * <p>提供的 Bean：
 * <ul>
 *   <li>AkskFeignRequestInterceptor - 拦截器</li>
 * </ul>
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
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = SimpleAkskClientCoreConstant.CONFIG_PREFIX, name = "enable", havingValue = "true")
@ConditionalOnClass(Feign.class)
@ConditionalOnBean(TokenManager.class)
public class SimpleAkskFeignRedisClientAutoConfiguration {

    @PostConstruct
    public void init() {
        log.info("===== Simple AKSK Feign Redis Client 自动配置加载成功 =====");
    }

    /**
     * 创建 AKSK Feign Request Interceptor Bean
     *
     * <p>注册到父上下文，{@link AkskFeignConfiguration} 通过 {@code @Autowired} 从父上下文继承此 Bean，
     * 再注册为 Feign 子上下文的 {@code RequestInterceptor}。
     * 此处使用 {@code @Bean} 方法而非自定义注解 + ComponentScan，
     * 是为了避免依赖 Feign 子上下文继承行为的不确定性。
     *
     * @param tokenManager Token Manager
     * @return AkskFeignRequestInterceptor
     */
    @Bean
    public AkskFeignRequestInterceptor akskFeignRequestInterceptor(TokenManager tokenManager) {
        log.info("Creating AkskFeignRequestInterceptor Bean");
        return new AkskFeignRequestInterceptor(tokenManager);
    }
}
