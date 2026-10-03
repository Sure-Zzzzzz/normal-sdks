package io.github.surezzzzzz.sdk.s3.client.test.cases;

import io.github.surezzzzzz.sdk.retry.task.executor.TaskRetryExecutor;
import io.github.surezzzzzz.sdk.s3.client.configuration.SimpleS3ClientAutoConfiguration;
import io.github.surezzzzzz.sdk.s3.client.configuration.SimpleS3ClientProperties;
import io.github.surezzzzzz.sdk.s3.client.controller.S3EventCallbackController;
import io.github.surezzzzzz.sdk.s3.client.template.S3ClientTemplate;
import io.github.surezzzzzz.sdk.s3.route.configuration.SimpleS3RouteProperties;
import io.github.surezzzzzz.sdk.s3.route.template.S3RouteTemplate;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * S3 Client 自动配置测试：enable 开关、门面构造依赖齐备性、
 * 事件回调端点的条件装配（enable + web 环境 + 默认关闭）。
 *
 * @author surezzzzzz
 */
@Slf4j
class SimpleS3ClientAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SimpleS3ClientAutoConfiguration.class));

    private final WebApplicationContextRunner webContextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SimpleS3ClientAutoConfiguration.class));

    @Test
    void disabledClientDoesNotCreateBeans() {
        contextRunner.withPropertyValues(
                        "io.github.surezzzzzz.sdk.s3.client.enable=false")
                .run(context -> {
                    log.info("禁用 Client 时 Template Bean 是否存在: {}",
                            context.containsBean("s3ClientTemplate"));
                    assertThat(context).doesNotHaveBean(S3ClientTemplate.class);
                });
    }

    @Test
    void enabledClientCreatesTemplateWithDependencies() {
        contextRunner.withUserConfiguration(ClientDependenciesStubConfiguration.class)
                .withPropertyValues(
                        "io.github.surezzzzzz.sdk.s3.client.enable=true")
                .run(context -> {
                    log.info("启用 Client 后 Template Bean 是否存在: {}",
                            context.containsBean("s3ClientTemplate"));
                    assertThat(context).hasSingleBean(SimpleS3ClientProperties.class);
                    assertThat(context).hasSingleBean(S3ClientTemplate.class);
                    assertThat(context.getBean(S3ClientTemplate.class)).isNotNull();
                    assertThat(context).doesNotHaveBean(S3EventCallbackController.class);
                });
    }

    @Test
    void enabledClientWithoutDependenciesFailsFast() {
        contextRunner.withPropertyValues(
                        "io.github.surezzzzzz.sdk.s3.client.enable=true")
                .run(context -> {
                    log.info("门面构造依赖缺失时启动失败: {}", context.getStartupFailure() != null);
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).isNotNull();
                });
    }

    @Test
    void eventCallbackControllerAssemblesOnlyWhenEnabled() {
        webContextRunner.withUserConfiguration(ClientDependenciesStubConfiguration.class)
                .withPropertyValues(
                        "io.github.surezzzzzz.sdk.s3.client.enable=true",
                        "io.github.surezzzzzz.sdk.s3.client.event-callback.enable=true")
                .run(context -> {
                    log.info("web 环境开启 event-callback 后端点是否存在: {}",
                            context.containsBean("s3EventCallbackController"));
                    assertThat(context).hasSingleBean(S3EventCallbackController.class);
                });

        webContextRunner.withUserConfiguration(ClientDependenciesStubConfiguration.class)
                .withPropertyValues(
                        "io.github.surezzzzzz.sdk.s3.client.enable=true")
                .run(context -> {
                    log.info("未开启 event-callback 时端点是否存在: {}",
                            context.containsBean("s3EventCallbackController"));
                    assertThat(context).doesNotHaveBean(S3EventCallbackController.class);
                });
    }

    @Configuration
    static class ClientDependenciesStubConfiguration {

        @Bean
        S3RouteTemplate s3RouteTemplate() {
            return mock(S3RouteTemplate.class);
        }

        @Bean
        SimpleS3RouteProperties simpleS3RouteProperties() {
            return new SimpleS3RouteProperties();
        }

        @Bean
        TaskRetryExecutor taskRetryExecutor() {
            return mock(TaskRetryExecutor.class);
        }
    }
}
