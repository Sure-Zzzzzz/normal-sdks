package io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.client.core.executor.TokenRefreshExecutor;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.provider.SecurityContextProvider;
import io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.configuration.SimpleAkskRedisTokenManagerAutoConfiguration;
import io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.manager.RedisTokenManager;
import io.github.surezzzzzz.sdk.cache.configuration.SmartCacheProperties;
import io.github.surezzzzzz.sdk.cache.manager.SmartCacheManager;
import io.github.surezzzzzz.sdk.lock.redis.SimpleRedisLock;
import io.github.surezzzzzz.sdk.retry.task.executor.TaskRetryExecutor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Redis Token Manager 自动配置测试
 *
 * @author surezzzzzz
 */
class RedisTokenManagerAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SimpleAkskRedisTokenManagerAutoConfiguration.class));

    @Test
    @DisplayName("客户端未启用时不注册 TokenManager")
    void shouldNotRegisterTokenManagerWhenClientDisabled() {
        contextRunner
                .withBean(SmartCacheManager.class, () -> mock(SmartCacheManager.class))
                .run(context -> assertFalse(context.containsBean("redisTokenManager"),
                        "客户端未启用时不应注册 RedisTokenManager"));
    }

    @Test
    @DisplayName("缺少 SmartCacheManager 时不注册 TokenManager")
    void shouldNotRegisterTokenManagerWithoutSmartCacheManager() {
        contextRunner
                .withPropertyValues("io.github.surezzzzzz.sdk.auth.aksk.client.enable=true")
                .run(context -> assertFalse(context.containsBean("redisTokenManager"),
                        "缺少 SmartCacheManager 时不应注册 RedisTokenManager"));
    }

    @Test
    @DisplayName("调用方自定义 Provider 和刷新执行器优先")
    void shouldPreferCustomProviderAndRefreshExecutor() {
        SecurityContextProvider customProvider = () -> "test-context";
        TokenRefreshExecutor customExecutor = mock(TokenRefreshExecutor.class);

        contextRunner
                .withPropertyValues("io.github.surezzzzzz.sdk.auth.aksk.client.enable=true")
                .withBean(SmartCacheManager.class, () -> mock(SmartCacheManager.class))
                .withBean(SmartCacheProperties.class, SmartCacheProperties::new)
                .withBean(RedisConnectionFactory.class, () -> mock(RedisConnectionFactory.class))
                .withBean(SimpleRedisLock.class, () -> mock(SimpleRedisLock.class))
                .withBean(TaskRetryExecutor.class, () -> mock(TaskRetryExecutor.class))
                .withBean(SecurityContextProvider.class, () -> customProvider)
                .withBean(TokenRefreshExecutor.class, () -> customExecutor)
                .run(context -> {
                    assertTrue(context.containsBean("redisTokenManager"), "满足条件时应注册 RedisTokenManager");
                    assertSame(customProvider, context.getBean(SecurityContextProvider.class),
                            "调用方 SecurityContextProvider 必须优先");
                    assertSame(customExecutor, context.getBean(TokenRefreshExecutor.class),
                            "调用方 TokenRefreshExecutor 必须优先");
                    assertNotNull(context.getBean(RedisTokenManager.class), "应可获取 RedisTokenManager");
                });
    }

    @Test
    @DisplayName("Jakarta 自动配置只通过 imports 注册")
    void shouldRegisterAutoConfigurationOnlyThroughImports() throws IOException {
        ClassLoader classLoader = SimpleAkskRedisTokenManagerAutoConfiguration.class.getClassLoader();
        InputStream importsStream = classLoader.getResourceAsStream(
                "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports");

        assertNotNull(importsStream, "AutoConfiguration imports 文件必须存在");
        try (InputStream inputStream = importsStream) {
            String imports = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(imports.contains(SimpleAkskRedisTokenManagerAutoConfiguration.class.getName()),
                    "imports 必须注册 Redis Token Manager 自动配置");
        }

        Enumeration<URL> legacyFactories = classLoader.getResources("META-INF/spring.factories");
        while (legacyFactories.hasMoreElements()) {
            URL legacyFactory = legacyFactories.nextElement();
            assertFalse(legacyFactory.toString().contains("simple-aksk-redis-token-manager-jakarta-starter"),
                    "Jakarta 模块不得携带 legacy spring.factories");
        }
    }
}
