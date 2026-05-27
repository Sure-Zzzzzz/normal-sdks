package io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.test;

import io.github.surezzzzzz.sdk.auth.aksk.client.core.configuration.SimpleAkskClientCoreProperties;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.provider.SecurityContextProvider;
import io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.configuration.SimpleAkskRedisTokenManagerProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Test Application
 *
 * @author surezzzzzz
 */
@SpringBootApplication
@EnableConfigurationProperties({
        SimpleAkskClientCoreProperties.class,
        SimpleAkskRedisTokenManagerProperties.class
})
public class SimpleAkskRedisTokenManagerTestApplication {

    public static final String TEST_SECURITY_CONTEXT = "{\"user_id\":\"test-user-123\"}";

    public static void main(String[] args) {
        SpringApplication.run(SimpleAkskRedisTokenManagerTestApplication.class, args);
    }

    /**
     * 测试用 SecurityContextProvider，返回固定非空值。
     * 仅在 "nonNullSecurityContext" profile 下加载。
     *
     * <p>用于验证 TokenWithExpiry 中的 securityContext 能正确存取。
     * 默认 null 场景由 {@link RedisTokenManagerDefaultCacheKeyTest} 单独覆盖。
     */
    @Bean
    @org.springframework.context.annotation.Profile("nonNullSecurityContext")
    public SecurityContextProvider securityContextProvider() {
        return () -> TEST_SECURITY_CONTEXT;
    }
}
