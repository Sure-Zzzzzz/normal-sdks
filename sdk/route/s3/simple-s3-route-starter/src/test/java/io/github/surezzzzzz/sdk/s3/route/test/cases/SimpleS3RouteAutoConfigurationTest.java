package io.github.surezzzzzz.sdk.s3.route.test.cases;

import com.amazonaws.services.s3.AmazonS3;
import io.github.surezzzzzz.sdk.s3.route.client.S3RouteClientFactory;
import io.github.surezzzzzz.sdk.s3.route.configuration.SimpleS3RouteAutoConfiguration;
import io.github.surezzzzzz.sdk.s3.route.configuration.SimpleS3RouteProperties;
import io.github.surezzzzzz.sdk.s3.route.registry.SimpleS3RouteRegistry;
import io.github.surezzzzzz.sdk.s3.route.resolver.S3RouteResolver;
import io.github.surezzzzzz.sdk.s3.route.template.S3RouteTemplate;
import io.github.surezzzzzz.sdk.s3.route.validator.S3RoutePropertiesValidator;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * S3 Route 自动配置测试。
 *
 * @author surezzzzzz
 */
@Slf4j
class SimpleS3RouteAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SimpleS3RouteAutoConfiguration.class));

    @Test
    void disabledRouteDoesNotCreateBeans() {
        contextRunner.withPropertyValues(
                        "io.github.surezzzzzz.sdk.s3.route.enable=false")
                .run(context -> {
                    log.info("禁用 Route 时 Template Bean 是否存在: {}",
                            context.containsBean("s3RouteTemplate"));
                    assertThat(context).doesNotHaveBean(S3RouteTemplate.class);
                });
    }

    @Test
    void customClientFactoryReplacesDefaultBean() {
        contextRunner.withUserConfiguration(CustomClientFactoryConfiguration.class)
                .withPropertyValues(
                        "io.github.surezzzzzz.sdk.s3.route.enable=true",
                        "io.github.surezzzzzz.sdk.s3.route.targets.test-main.endpoint=http://127.0.0.1:19000")
                .run(context -> {
                    S3RouteClientFactory factory = context.getBean(S3RouteClientFactory.class);
                    log.info("自定义 client factory 类型: {}", factory.getClass().getSimpleName());
                    assertThat(context).hasSingleBean(S3RouteClientFactory.class);
                    assertThat(factory).isInstanceOf(CustomClientFactory.class);
                });
    }

    @Test
    void enabledRouteCreatesRegistryAndTemplate() {
        contextRunner.withPropertyValues(
                        "io.github.surezzzzzz.sdk.s3.route.enable=true",
                        "io.github.surezzzzzz.sdk.s3.route.targets.test-main.endpoint=http://127.0.0.1:19000")
                .run(context -> {
                    log.info("启用 Route 后 Registry Bean 是否存在: {}",
                            context.containsBean("simpleS3RouteRegistry"));
                    assertThat(context).hasSingleBean(SimpleS3RouteProperties.class);
                    assertThat(context).hasSingleBean(S3RoutePropertiesValidator.class);
                    assertThat(context).hasSingleBean(S3RouteClientFactory.class);
                    assertThat(context).hasSingleBean(SimpleS3RouteRegistry.class);
                    assertThat(context).hasSingleBean(S3RouteResolver.class);
                    assertThat(context).hasSingleBean(S3RouteTemplate.class);
                });
    }

    @Configuration
    static class CustomClientFactoryConfiguration {

        @Bean
        S3RouteClientFactory customClientFactory() {
            return new CustomClientFactory();
        }
    }

    static class CustomClientFactory implements S3RouteClientFactory {

        @Override
        public AmazonS3 create(String targetKey, SimpleS3RouteProperties.TargetConfig config) {
            return mock(AmazonS3.class);
        }
    }
}
