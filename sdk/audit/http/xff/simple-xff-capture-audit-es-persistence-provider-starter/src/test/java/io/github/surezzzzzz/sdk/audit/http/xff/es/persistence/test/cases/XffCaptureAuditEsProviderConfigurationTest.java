package io.github.surezzzzzz.sdk.audit.http.xff.es.persistence.test.cases;

import io.github.surezzzzzz.sdk.audit.http.xff.es.persistence.configuration.XffCaptureAuditEsProviderConfiguration;
import io.github.surezzzzzz.sdk.audit.http.xff.es.persistence.constant.SimpleXffCaptureAuditEsPersistenceProviderConstant;
import io.github.surezzzzzz.sdk.audit.http.xff.es.persistence.provider.ElasticsearchXffCaptureAuditPersistenceProvider;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.engine.PersistenceEngine;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.SpringFactoriesLoader;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Elasticsearch XFF Capture Audit Provider 自动配置测试。
 *
 * @author surezzzzzz
 */
@Slf4j
class XffCaptureAuditEsProviderConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    XffCaptureAuditEsProviderConfiguration.class));

    @Test
    void shouldDeclareSpringFactoriesAutoConfiguration() {
        List<String> configurations = SpringFactoriesLoader.loadFactoryNames(
                EnableAutoConfiguration.class, getClass().getClassLoader());

        log.info("Spring factories 自动配置声明数量：{}", configurations.size());
        assertTrue(configurations.contains(
                        XffCaptureAuditEsProviderConfiguration.class.getName()),
                "spring.factories 必须声明 ES Provider 自动配置");
    }

    @Test
    void shouldNotRegisterWhenDisabled() {
        contextRunner.withUserConfiguration(PersistenceConfiguration.class)
                .run(context -> {
                    log.info("关闭时 ES Provider 数量：{}",
                            context.getBeansOfType(ElasticsearchXffCaptureAuditPersistenceProvider.class).size());
                    assertTrue(context.getBeansOfType(ElasticsearchXffCaptureAuditPersistenceProvider.class)
                                    .isEmpty(),
                            "默认关闭时不应注册 ES Provider");
                });
    }

    @Test
    void shouldFailWhenEnabledWithoutPersistenceEngine() {
        contextRunner.withPropertyValues(enabledProperty())
                .run(context -> {
                    log.info("无 PersistenceEngine 时启动异常：{}", context.getStartupFailure());
                    assertTrue(context.getStartupFailure() != null,
                            "启用 ES Provider 但缺少 PersistenceEngine 时必须启动失败");
                });
    }

    @Test
    void shouldRegisterWhenEnabledWithPersistenceEngine() {
        contextRunner.withUserConfiguration(PersistenceConfiguration.class)
                .withPropertyValues(enabledProperty())
                .run(context -> {
                    int providerCount = context.getBeansOfType(
                            ElasticsearchXffCaptureAuditPersistenceProvider.class).size();
                    log.info("启用且存在 PersistenceEngine 时 ES Provider 数量：{}", providerCount);
                    assertEquals(1, providerCount, "应注册一个 ES Provider");
                });
    }

    private String enabledProperty() {
        return SimpleXffCaptureAuditEsPersistenceProviderConstant.CONFIG_PREFIX + ".enable=true";
    }

    @Configuration
    static class PersistenceConfiguration {

        @Bean
        PersistenceEngine persistenceEngine() {
            return mock(PersistenceEngine.class);
        }
    }
}
