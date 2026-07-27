package io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.test.cases;

import io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.configuration.SimpleKafkaOutboxManagementAutoConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.thymeleaf.ThymeleafAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Management 密码编码器自动配置测试。
 *
 * @author surezzzzzz
 */
@Slf4j
class SimpleKafkaOutboxManagementPasswordEncoderTest {
    private static final DataSource TEST_DATA_SOURCE = Mockito.mock(DataSource.class);

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DispatcherServletAutoConfiguration.class, WebMvcAutoConfiguration.class,
                    ThymeleafAutoConfiguration.class, SecurityAutoConfiguration.class,
                    SimpleKafkaOutboxManagementAutoConfiguration.class))
            .withBean(DataSource.class, () -> TEST_DATA_SOURCE)
            .withBean(DataSourceTransactionManager.class,
                    () -> new DataSourceTransactionManager(TEST_DATA_SOURCE))
            .withPropertyValues("io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.enable=true",
                    "io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.admin.username=test",
                    "io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.admin.password=test");

    @Test
    void shouldProvideDefaultPasswordEncoderWhenApplicationDoesNotHaveOne() {
        contextRunner.run(context -> {
            assertNotNull(context.getBean(PasswordEncoder.class));
            assertNotNull(context.getBean("simpleKafkaOutboxManagementUserDetailsService"));
            assertEquals(1, context.getBeanNamesForType(org.springframework.security.core.userdetails.UserDetailsService.class).length,
                    "Management 启用时不得额外注册默认用户服务");
            log.info("没有应用 PasswordEncoder 时，Management 提供默认 delegating encoder");
        });
    }

    @Test
    void shouldNotRegisterManagementBeansWhenUiIsDisabled() {
        contextRunner.withPropertyValues("io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.ui.enable=false")
                .run(context -> assertTrue(context.getBeanNamesForType(
                                io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.service.KafkaOutboxManagementService.class).length == 0,
                        "ui.enable=false 时不得注册任何 Management 服务"));
    }

    @Test
    void shouldNotRegisterRootRedirectControllerWhenDisabled() {
        contextRunner.withPropertyValues("io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.ui.redirect-root=false")
                .run(context -> assertEquals(0, context.getBeanNamesForType(
                                io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.controller.KafkaOutboxManagementRootController.class).length,
                        "关闭根路径重定向时不得注册根入口控制器"));
    }

    @Test
    void shouldReuseApplicationPasswordEncoder() {
        contextRunner.withUserConfiguration(HostPasswordEncoderConfiguration.class).run(context -> {
            PasswordEncoder hostEncoder = context.getBean("hostPasswordEncoder", PasswordEncoder.class);
            assertSame(hostEncoder, context.getBean(PasswordEncoder.class));
            org.springframework.security.core.userdetails.UserDetailsService users = context.getBean(
                    "simpleKafkaOutboxManagementUserDetailsService", org.springframework.security.core.userdetails.UserDetailsService.class);
            assertTrue(hostEncoder.matches("test", users.loadUserByUsername("test").getPassword()),
                    "Management 管理员密码必须使用应用 PasswordEncoder 编码");
            log.info("存在应用 PasswordEncoder 时，Management 直接复用该 Bean");
        });
    }

    @Configuration
    static class HostPasswordEncoderConfiguration {
        @Bean
        PasswordEncoder hostPasswordEncoder() {
            return PasswordEncoderFactories.createDelegatingPasswordEncoder();
        }
    }

}
