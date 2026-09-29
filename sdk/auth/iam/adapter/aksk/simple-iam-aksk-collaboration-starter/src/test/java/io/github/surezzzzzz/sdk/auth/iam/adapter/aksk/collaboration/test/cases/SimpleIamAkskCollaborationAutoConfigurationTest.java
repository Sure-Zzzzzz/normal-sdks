package io.github.surezzzzzz.sdk.auth.iam.adapter.aksk.collaboration.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.server.configuration.SimpleAkskServerProperties;
import io.github.surezzzzzz.sdk.auth.authorization.owner.collaboration.core.spi.OwnerAuthorizationProvider;
import io.github.surezzzzzz.sdk.auth.iam.adapter.aksk.collaboration.configuration.SimpleIamAkskCollaborationAutoConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * IAM AKSK Collaboration 自动配置测试。
 *
 * <p>只验证适配器自身扫描和属性绑定，不启动 AKSK Server、数据库或 IAM HTTP 服务。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
class SimpleIamAkskCollaborationAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SimpleIamAkskCollaborationAutoConfiguration.class))
            .withUserConfiguration(TestConfiguration.class);

    @Test
    void enabledAdapterMustRegisterExactlyOneAvailableProvider() {
        contextRunner.withPropertyValues(
                "io.github.surezzzzzz.sdk.auth.aksk.server.owner-authorization.enabled=true",
                "io.github.surezzzzzz.sdk.auth.aksk.server.owner-authorization.owner-source-id=local-iam",
                "io.github.surezzzzzz.sdk.auth.iam.adapter.aksk.collaboration.enabled=true",
                "io.github.surezzzzzz.sdk.auth.iam.adapter.aksk.collaboration.token-uri=http://iam.example/oauth2/token",
                "io.github.surezzzzzz.sdk.auth.iam.adapter.aksk.collaboration.base-uri=http://iam.example",
                "io.github.surezzzzzz.sdk.auth.iam.adapter.aksk.collaboration.client-id=reader",
                "io.github.surezzzzzz.sdk.auth.iam.adapter.aksk.collaboration.client-secret=reader-secret"
        ).run(context -> {
            assertEquals(1, context.getBeansOfType(OwnerAuthorizationProvider.class).size(),
                    "适配器必须只注册一个 owner authorization provider");
            assertTrue(context.getBean(OwnerAuthorizationProvider.class).isAvailable(),
                    "完整启用配置下 provider 必须可用");
            log.info("IAM AKSK Collaboration 自动配置通过：providerCount={}",
                    context.getBeansOfType(OwnerAuthorizationProvider.class).size());
        });
    }

    /**
     * 为适配器提供 AKSK 公共投影配置 Bean，模拟业务应用同时引入两个 starter 的组合方式。
     */
    @Configuration
    @EnableConfigurationProperties(SimpleAkskServerProperties.class)
    static class TestConfiguration {
    }
}
