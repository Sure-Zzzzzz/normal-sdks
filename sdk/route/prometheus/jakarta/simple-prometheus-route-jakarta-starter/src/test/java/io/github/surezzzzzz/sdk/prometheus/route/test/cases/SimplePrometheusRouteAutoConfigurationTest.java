package io.github.surezzzzzz.sdk.prometheus.route.test.cases;

import io.github.surezzzzzz.sdk.prometheus.route.configuration.SimplePrometheusRouteAutoConfiguration;
import io.github.surezzzzzz.sdk.prometheus.route.configuration.SimplePrometheusRouteProperties;
import io.github.surezzzzzz.sdk.prometheus.route.registry.SimplePrometheusRouteRegistry;
import io.github.surezzzzzz.sdk.prometheus.route.template.PrometheusRouteTemplate;
import io.github.surezzzzzz.sdk.prometheus.route.transport.PrometheusRouteTransportFactory;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
class SimplePrometheusRouteAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SimplePrometheusRouteAutoConfiguration.class));

    @Test
    void disabledRouteDoesNotCreateBeans() {
        contextRunner.withPropertyValues(
                        "io.github.surezzzzzz.sdk.prometheus.route.enable=false")
                .run(context -> {
                    log.info("禁用 Route 时 Template Bean 是否存在: {}",
                            context.containsBean("prometheusRouteTemplate"));
                    assertThat(context).doesNotHaveBean(PrometheusRouteTemplate.class);
                });
    }

    @Test
    void customTransportFactoryReplacesDefaultBean() {
        contextRunner.withUserConfiguration(CustomTransportFactoryConfiguration.class)
                .withPropertyValues(
                        "io.github.surezzzzzz.sdk.prometheus.route.enable=true",
                        "io.github.surezzzzzz.sdk.prometheus.route.targets.test-main.url=http://127.0.0.1:19090")
                .run(context -> {
                    PrometheusRouteTransportFactory factory = context.getBean(PrometheusRouteTransportFactory.class);
                    log.info("自定义 transport factory 类型: {}", factory.getClass().getSimpleName());
                    assertThat(context).hasSingleBean(PrometheusRouteTransportFactory.class);
                    assertThat(factory).isInstanceOf(CustomTransportFactory.class);
                });
    }

    @Test
    void enabledRouteCreatesRegistryAndTemplate() {
        contextRunner.withPropertyValues(
                        "io.github.surezzzzzz.sdk.prometheus.route.enable=true",
                        "io.github.surezzzzzz.sdk.prometheus.route.targets.test-main.url=http://127.0.0.1:19090")
                .run(context -> {
                    log.info("启用 Route 后 Registry Bean 是否存在: {}",
                            context.containsBean("simplePrometheusRouteRegistry"));
                    assertThat(context).hasSingleBean(SimplePrometheusRouteProperties.class);
                    assertThat(context).hasSingleBean(SimplePrometheusRouteRegistry.class);
                    assertThat(context).hasSingleBean(PrometheusRouteTemplate.class);
                });
    }

    @Configuration
    static class CustomTransportFactoryConfiguration {

        @Bean
        PrometheusRouteTransportFactory customTransportFactory() {
            return new CustomTransportFactory();
        }
    }

    static class CustomTransportFactory implements PrometheusRouteTransportFactory {

        @Override
        public io.github.surezzzzzz.sdk.prometheus.route.transport.PrometheusRouteTransport create(
                String targetKey, SimplePrometheusRouteProperties.TargetConfig config) {
            return new io.github.surezzzzzz.sdk.prometheus.route.transport.PrometheusRouteTransport() {
                @Override
                public io.github.surezzzzzz.sdk.prometheus.route.model.PrometheusRouteResponse exchange(
                        io.github.surezzzzzz.sdk.prometheus.route.model.PrometheusRouteRequest request) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public void close() {
                }
            };
        }
    }
}
