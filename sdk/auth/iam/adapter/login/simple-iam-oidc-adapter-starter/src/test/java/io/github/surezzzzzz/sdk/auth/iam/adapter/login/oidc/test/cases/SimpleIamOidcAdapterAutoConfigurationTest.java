package io.github.surezzzzzz.sdk.auth.iam.adapter.login.oidc.test.cases;

import io.github.surezzzzzz.sdk.auth.iam.adapter.login.oidc.configuration.SimpleIamOidcAdapterAutoConfiguration;
import io.github.surezzzzzz.sdk.auth.iam.adapter.login.oidc.service.MemoryPendingStateStore;
import io.github.surezzzzzz.sdk.auth.iam.adapter.login.oidc.service.OidcBrowserLoginProvider;
import io.github.surezzzzzz.sdk.auth.iam.adapter.login.oidc.service.RedisPendingStateStore;
import io.github.surezzzzzz.sdk.redis.route.template.RedisRouteTemplate;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OIDC 适配器自动装配测试（不依赖 Keycloak 基础设施）
 *
 * @author surezzzzzz
 */
@Slf4j
class SimpleIamOidcAdapterAutoConfigurationTest {

    private static final String COMPLETE_PROPERTIES = ""
            + "io.github.surezzzzzz.sdk.auth.iam.adapter.login.oidc.issuer=https://idp.example.test,"
            + "io.github.surezzzzzz.sdk.auth.iam.adapter.login.oidc.authorization-uri=https://idp.example.test/authorize,"
            + "io.github.surezzzzzz.sdk.auth.iam.adapter.login.oidc.token-uri=https://idp.example.test/token,"
            + "io.github.surezzzzzz.sdk.auth.iam.adapter.login.oidc.jwk-set-uri=https://idp.example.test/certs,"
            + "io.github.surezzzzzz.sdk.auth.iam.adapter.login.oidc.client-id=iam-test-client,"
            + "io.github.surezzzzzz.sdk.auth.iam.adapter.login.oidc.client-secret=test-secret";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SimpleIamOidcAdapterAutoConfiguration.class));

    @Test
    @DisplayName("配置完整时应装配登录提供方（无 enable 开关，引入即装配）")
    void testCompleteConfigurationRegistersProvider() {
        runner.withPropertyValues(COMPLETE_PROPERTIES.split(","))
                .run(context -> {
                    assertThat(context).hasSingleBean(OidcBrowserLoginProvider.class);
                    String providerCode = context.getBean(OidcBrowserLoginProvider.class)
                            .providerCode();
                    log.info("配置完整：providerCode={}", providerCode);
                    assertTrue(providerCode.equals("oidc"));
                });
    }

    @Test
    @DisplayName("宿主无 RedisRouteTemplate bean 时授权暂存退回内存实现")
    void testMemoryStoreFallbackWhenRouteBeanMissing() {
        runner.withPropertyValues(COMPLETE_PROPERTIES.split(","))
                .run(context -> {
                    assertThat(context).hasSingleBean(OidcBrowserLoginProvider.class);
                    Object store = ReflectionTestUtils.getField(
                            context.getBean(OidcBrowserLoginProvider.class), "pendingStateStore");
                    log.info("宿主无 route bean：store 实现类型={}", store.getClass().getSimpleName());
                    assertTrue(store instanceof MemoryPendingStateStore,
                            "route bean 缺失时必须退回内存实现，实际：" + store);
                });
    }

    @Test
    @DisplayName("宿主存在 RedisRouteTemplate bean 时授权暂存走 Redis 实现")
    void testRedisStoreWhenRouteBeanPresent() {
        runner.withPropertyValues(COMPLETE_PROPERTIES.split(","))
                .withBean("redisRouteTemplate", RedisRouteTemplate.class,
                        () -> org.mockito.Mockito.mock(RedisRouteTemplate.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(OidcBrowserLoginProvider.class);
                    Object store = ReflectionTestUtils.getField(
                            context.getBean(OidcBrowserLoginProvider.class), "pendingStateStore");
                    log.info("宿主有 route bean：store 实现类型={}", store.getClass().getSimpleName());
                    assertTrue(store instanceof RedisPendingStateStore,
                            "route bean 存在时必须走 Redis 实现，实际：" + store);
                });
    }

    @Test
    @DisplayName("配置不完整应启动失败")
    void testIncompleteConfigurationFailsFast() {
        runner.withPropertyValues(
                        "io.github.surezzzzzz.sdk.auth.iam.adapter.login.oidc.issuer=https://idp.example.test")
                .run(context -> {
                    assertTrue(context.getStartupFailure() != null);
                    log.info("配置不完整启动失败：{}",
                            context.getStartupFailure().getMessage());
                    assertTrue(context.getStartupFailure().getMessage()
                            .contains("adapter.login.oidc 已引入但配置不完整"));
                });
    }
}

