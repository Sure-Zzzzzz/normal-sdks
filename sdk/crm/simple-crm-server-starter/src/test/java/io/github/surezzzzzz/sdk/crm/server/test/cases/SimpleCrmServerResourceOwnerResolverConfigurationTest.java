package io.github.surezzzzzz.sdk.crm.server.test.cases;

import io.github.surezzzzzz.sdk.auth.resource.core.model.VerifiedResourceContext;
import io.github.surezzzzzz.sdk.auth.resource.server.filter.ResourceAuthenticationFilter;
import io.github.surezzzzzz.sdk.crm.server.configuration.SimpleCrmServerAutoConfiguration;
import io.github.surezzzzzz.sdk.crm.server.configuration.SimpleCrmServerResourceOwnerResolverConfiguration;
import io.github.surezzzzzz.sdk.crm.server.service.SimpleCrmServerOwnerResolverBridge;
import io.github.surezzzzzz.sdk.crm.server.service.SimpleCrmServerResourceOwnerResolverBridge;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Resource owner 桥自动配置契约测试。
 *
 * @author surezzzzzz
 */
@Slf4j
class SimpleCrmServerResourceOwnerResolverConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    SimpleCrmServerResourceOwnerResolverConfiguration.class,
                    SimpleCrmServerAutoConfiguration.class))
            .withPropertyValues("io.github.surezzzzzz.sdk.crm.server.enable=true")
            .withUserConfiguration(JdbcTemplateConfiguration.class);

    @Test
    void resourceContextUsesResourceOwnerBridgeBeforeDefaultBridge() {
        runner.run(context -> {
            SimpleCrmServerOwnerResolverBridge bridge = context.getBean(SimpleCrmServerOwnerResolverBridge.class);
            log.info("Resource 协作形态 owner 桥={}", bridge.getClass().getName());
            assertEquals(SimpleCrmServerResourceOwnerResolverBridge.class, bridge.getClass());
        });
    }

    @Test
    void missingResourceContextKeepsDefaultBridge() {
        runner.withClassLoader(new FilteredClassLoader(VerifiedResourceContext.class,
                        ResourceAuthenticationFilter.class))
                .run(context -> {
                    SimpleCrmServerOwnerResolverBridge bridge =
                            context.getBean(SimpleCrmServerOwnerResolverBridge.class);
                    log.info("散形态 owner 桥={}", bridge.getClass().getName());
                    assertEquals(SimpleCrmServerOwnerResolverBridge.class, bridge.getClass());
                });
    }

    @Test
    void resourceCoreWithoutAuthenticationFilterKeepsDefaultBridge() {
        runner.withClassLoader(new FilteredClassLoader(ResourceAuthenticationFilter.class))
                .run(context -> {
                    SimpleCrmServerOwnerResolverBridge bridge =
                            context.getBean(SimpleCrmServerOwnerResolverBridge.class);
                    log.info("仅 Resource core 在场时 owner 桥={}", bridge.getClass().getName());
                    assertEquals(SimpleCrmServerOwnerResolverBridge.class, bridge.getClass());
                });
    }

    /**
     * 装配测试 JDBC 模板桩，不访问数据库。
     *
     * @author surezzzzzz
     */
    @Configuration
    static class JdbcTemplateConfiguration {

        @Bean
        NamedParameterJdbcTemplate namedParameterJdbcTemplate() {
            return org.mockito.Mockito.mock(NamedParameterJdbcTemplate.class);
        }
    }
}
