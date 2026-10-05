package io.github.surezzzzzz.sdk.audit.http.xff.es.persistence.test.cases;

import io.github.surezzzzzz.sdk.audit.http.xff.es.persistence.configuration.XffCaptureAuditEsProviderConfiguration;
import io.github.surezzzzzz.sdk.audit.http.xff.es.persistence.constant.SimpleXffCaptureAuditEsPersistenceProviderConstant;
import io.github.surezzzzzz.sdk.audit.http.xff.es.persistence.provider.ElasticsearchXffCaptureAuditPersistenceProvider;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.engine.PersistenceEngine;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Jakarta 自动配置入口和启用边界测试。
 *
 * @author surezzzzzz
 */
@Slf4j
class XffCaptureAuditEsProviderConfigurationTest {

    private static final String AUTO_CONFIGURATION_IMPORTS =
            "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(XffCaptureAuditEsProviderConfiguration.class));

    @Test
    void shouldDeclareJakartaAutoConfiguration() throws Exception {
        InputStream resource = getClass().getClassLoader().getResourceAsStream(AUTO_CONFIGURATION_IMPORTS);
        assertNotNull(resource, "必须提供 Spring Boot 3 自动配置入口");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource,
                StandardCharsets.UTF_8))) {
            List<String> configurations = reader.lines().filter(line -> !line.trim().isEmpty())
                    .collect(Collectors.toList());
            log.info("Jakarta 自动配置数量：{}", configurations.size());
            assertEquals(1, configurations.size(), "模块只应声明一个自动配置");
            assertEquals(XffCaptureAuditEsProviderConfiguration.class.getName(), configurations.get(0),
                    "自动配置入口必须指向 Provider");
        }
    }

    @Test
    void shouldNotRegisterWhenDisabled() {
        contextRunner.withUserConfiguration(PersistenceConfiguration.class)
                .run(context -> {
                    int count = context.getBeansOfType(
                            ElasticsearchXffCaptureAuditPersistenceProvider.class).size();
                    log.info("关闭时 ES Provider 数量：{}", count);
                    assertEquals(0, count, "默认关闭时不能注册 Provider");
                });
    }

    @Test
    void shouldFailWhenEnabledWithoutPersistenceEngine() {
        contextRunner.withPropertyValues(enabledProperty())
                .run(context -> {
                    log.info("缺少 PersistenceEngine 时启动异常类型：{}",
                            context.getStartupFailure() == null ? "none"
                                    : context.getStartupFailure().getClass().getName());
                    assertNotNull(context.getStartupFailure(), "启用但缺少写入入口必须启动失败");
                });
    }

    @Test
    void shouldRegisterWhenEnabledWithPersistenceEngine() {
        contextRunner.withUserConfiguration(PersistenceConfiguration.class)
                .withPropertyValues(enabledProperty())
                .run(context -> {
                    int count = context.getBeansOfType(
                            ElasticsearchXffCaptureAuditPersistenceProvider.class).size();
                    log.info("启用时 ES Provider 数量：{}", count);
                    assertEquals(1, count, "应注册一个 Provider");
                    assertTrue(context.getStartupFailure() == null, "有效配置不应启动失败");
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
