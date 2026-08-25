package io.github.surezzzzzz.sdk.prometheus.client.test.cases;

import io.github.surezzzzzz.sdk.prometheus.client.configuration.SimplePrometheusClientAutoConfiguration;
import io.github.surezzzzzz.sdk.prometheus.client.template.PrometheusClientTemplate;
import io.github.surezzzzzz.sdk.prometheus.route.template.PrometheusRouteTemplate;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Simple Prometheus Client 自动配置测试。
 *
 * @author surezzzzzz
 */
@Slf4j
class SimplePrometheusClientAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SimplePrometheusClientAutoConfiguration.class));

    @Test
    void noRouteFailsStartupFast() {
        contextRunner.run(context -> {
            log.info("验证缺少 Route Bean 时应用启动快速失败");
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasRootCauseInstanceOf(NoSuchBeanDefinitionException.class);
        });
    }

    @Test
    void routeCreatesOneClientAndUsesSameRouteBean() {
        contextRunner.withUserConfiguration(RouteConfiguration.class)
                .run(context -> {
                    PrometheusRouteTemplate route = context.getBean(PrometheusRouteTemplate.class);
                    PrometheusClientTemplate client = context.getBean(PrometheusClientTemplate.class);
                    log.info("验证 Route 就绪后创建唯一 Client Bean");
                    assertThat(context).hasSingleBean(PrometheusRouteTemplate.class);
                    assertThat(context).hasSingleBean(PrometheusClientTemplate.class);
                    assertThat(ReflectionTestUtils.getField(client, "routeTemplate")).isSameAs(route);
                });
    }

    @Test
    void clientEnablePropertyDoesNotControlAutoConfiguration() {
        contextRunner.withUserConfiguration(RouteConfiguration.class)
                .withPropertyValues("io.github.surezzzzzz.sdk.prometheus.client.enable=false")
                .run(context -> {
                    log.info("验证 Client 没有独立 enable 配置项");
                    assertThat(context).hasSingleBean(PrometheusClientTemplate.class);
                });
    }

    @Configuration
    static class RouteConfiguration {

        @Bean
        PrometheusRouteTemplate prometheusRouteTemplate() {
            return new PrometheusRouteTemplate(null, null);
        }
    }
}
