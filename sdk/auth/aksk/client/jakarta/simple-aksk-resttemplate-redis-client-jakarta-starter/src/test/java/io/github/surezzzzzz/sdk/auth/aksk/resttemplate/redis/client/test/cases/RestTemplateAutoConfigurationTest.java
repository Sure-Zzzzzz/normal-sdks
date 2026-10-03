package io.github.surezzzzzz.sdk.auth.aksk.resttemplate.redis.client.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.client.core.manager.TokenManager;
import io.github.surezzzzzz.sdk.auth.aksk.resttemplate.redis.client.configuration.SimpleAkskRestTemplateRedisClientAutoConfiguration;
import io.github.surezzzzzz.sdk.auth.aksk.resttemplate.redis.client.interceptor.AkskRestTemplateInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RestTemplate Jakarta 自动配置测试
 *
 * @author surezzzzzz
 */
class RestTemplateAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SimpleAkskRestTemplateRedisClientAutoConfiguration.class));

    @Test
    void shouldNotRegisterClientBeansWhenClientDisabled() {
        contextRunner
                .withBean(TokenManager.class, EmptyTokenManager::new)
                .run(context -> {
                    assertFalse(context.containsBean("akskRestTemplateInterceptor"), "客户端未启用时不应注册拦截器");
                    assertFalse(context.containsBean("akskClientRestTemplate"), "客户端未启用时不应注册 RestTemplate");
                });
    }

    @Test
    void shouldRegisterRestTemplateWhenTokenManagerAvailable() {
        contextRunner
                .withPropertyValues(
                        "io.github.surezzzzzz.sdk.auth.aksk.client.enable=true",
                        "io.github.surezzzzzz.sdk.auth.aksk.client.resttemplate.enable=true")
                .withBean(TokenManager.class, EmptyTokenManager::new)
                .run(context -> {
                    assertNotNull(context.getBean(AkskRestTemplateInterceptor.class), "应注册 AKSK RestTemplate 拦截器");
                    assertNotNull(context.getBean("akskClientRestTemplate", RestTemplate.class),
                            "启用时应注册命名 RestTemplate");
                });
    }

    @Test
    void shouldKeepCallerProvidedInterceptor() {
        AkskRestTemplateInterceptor customInterceptor = new AkskRestTemplateInterceptor(new EmptyTokenManager());

        contextRunner
                .withPropertyValues("io.github.surezzzzzz.sdk.auth.aksk.client.enable=true")
                .withBean(TokenManager.class, EmptyTokenManager::new)
                .withBean(AkskRestTemplateInterceptor.class, () -> customInterceptor)
                .run(context -> assertSame(customInterceptor, context.getBean(AkskRestTemplateInterceptor.class),
                        "调用方自定义拦截器必须优先"));
    }

    @Test
    void shouldRegisterAutoConfigurationOnlyThroughImports() throws IOException {
        ClassLoader classLoader = SimpleAkskRestTemplateRedisClientAutoConfiguration.class.getClassLoader();
        InputStream importsStream = classLoader.getResourceAsStream(
                "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports");

        assertNotNull(importsStream, "AutoConfiguration imports 文件必须存在");
        try (InputStream inputStream = importsStream) {
            String imports = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(imports.contains(SimpleAkskRestTemplateRedisClientAutoConfiguration.class.getName()),
                    "imports 必须注册 RestTemplate 自动配置");
        }

        Enumeration<URL> legacyFactories = classLoader.getResources("META-INF/spring.factories");
        while (legacyFactories.hasMoreElements()) {
            URL legacyFactory = legacyFactories.nextElement();
            assertFalse(legacyFactory.toString().contains("simple-aksk-resttemplate-redis-client-jakarta-starter"),
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
