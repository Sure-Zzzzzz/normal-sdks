package io.github.surezzzzzz.sdk.auth.aksk.feign.redis.client.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.client.core.manager.TokenManager;
import io.github.surezzzzzz.sdk.auth.aksk.feign.redis.client.configuration.AkskFeignConfiguration;
import io.github.surezzzzzz.sdk.auth.aksk.feign.redis.client.configuration.SimpleAkskFeignRedisClientAutoConfiguration;
import io.github.surezzzzzz.sdk.auth.aksk.feign.redis.client.interceptor.AkskFeignRequestInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Feign Jakarta 自动配置测试
 *
 * @author surezzzzzz
 */
class FeignAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SimpleAkskFeignRedisClientAutoConfiguration.class));

    @Test
    void shouldNotRegisterInterceptorWhenClientDisabled() {
        contextRunner
                .withBean(TokenManager.class, EmptyTokenManager::new)
                .run(context -> assertFalse(context.containsBean("akskFeignRequestInterceptor"),
                        "客户端未启用时不应注册 Feign 拦截器"));
    }

    @Test
    void shouldRegisterInterceptorWhenTokenManagerAvailable() {
        contextRunner
                .withPropertyValues("io.github.surezzzzzz.sdk.auth.aksk.client.enable=true")
                .withBean(TokenManager.class, EmptyTokenManager::new)
                .run(context -> assertNotNull(context.getBean(AkskFeignRequestInterceptor.class),
                        "满足条件时应注册 Feign 拦截器"));
    }

    @Test
    void shouldKeepFeignConfigurationOutOfGlobalScan() {
        assertFalse(AkskFeignConfiguration.class.isAnnotationPresent(Configuration.class),
                "Feign 客户端级配置不能成为全局 Configuration");
    }

    @Test
    void shouldRegisterAutoConfigurationOnlyThroughImports() throws IOException {
        ClassLoader classLoader = SimpleAkskFeignRedisClientAutoConfiguration.class.getClassLoader();
        InputStream importsStream = classLoader.getResourceAsStream(
                "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports");

        assertNotNull(importsStream, "AutoConfiguration imports 文件必须存在");
        try (InputStream inputStream = importsStream) {
            String imports = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(imports.contains(SimpleAkskFeignRedisClientAutoConfiguration.class.getName()),
                    "imports 必须注册 Feign 自动配置");
        }

        Enumeration<URL> legacyFactories = classLoader.getResources("META-INF/spring.factories");
        while (legacyFactories.hasMoreElements()) {
            URL legacyFactory = legacyFactories.nextElement();
            assertFalse(legacyFactory.toString().contains("simple-aksk-feign-redis-client-jakarta-starter"),
                    "Jakarta 模块不得携带 legacy spring.factories");
        }
    }

    private static final class EmptyTokenManager implements TokenManager {

        @Override
        public String getToken() {
            return "test-token";
        }

        @Override
        public void clearToken() {
            // 测试桩不持有 Token 状态。
        }
    }
}
