package io.github.surezzzzzz.sdk.prometheus.route.configuration;

import io.github.surezzzzzz.sdk.prometheus.route.SimplePrometheusRoutePackage;
import io.github.surezzzzzz.sdk.prometheus.route.annotation.SimplePrometheusRouteComponent;
import io.github.surezzzzzz.sdk.prometheus.route.constant.SimplePrometheusRouteConstant;
import io.github.surezzzzzz.sdk.prometheus.route.registry.SimplePrometheusRouteRegistry;
import io.github.surezzzzzz.sdk.prometheus.route.resolver.DefaultPrometheusRouteResolver;
import io.github.surezzzzzz.sdk.prometheus.route.resolver.PrometheusRouteResolver;
import io.github.surezzzzzz.sdk.prometheus.route.template.PrometheusRouteTemplate;
import io.github.surezzzzzz.sdk.prometheus.route.transport.DefaultPrometheusRouteTransportFactory;
import io.github.surezzzzzz.sdk.prometheus.route.transport.PrometheusRouteTransportFactory;
import io.github.surezzzzzz.sdk.prometheus.route.validator.DefaultPrometheusRoutePropertiesValidator;
import io.github.surezzzzzz.sdk.prometheus.route.validator.PrometheusRoutePropertiesValidator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Prometheus Route 自动配置。
 *
 * @author surezzzzzz
 */
@Configuration
@EnableConfigurationProperties(SimplePrometheusRouteProperties.class)
@ComponentScan(basePackageClasses = SimplePrometheusRoutePackage.class, useDefaultFilters = false,
        includeFilters = @ComponentScan.Filter(SimplePrometheusRouteComponent.class))
@ConditionalOnClass(name = "org.apache.http.impl.client.CloseableHttpClient")
@ConditionalOnProperty(prefix = SimplePrometheusRouteConstant.CONFIG_PREFIX,
        name = SimplePrometheusRouteConstant.CONFIG_PROPERTY_ENABLE,
        havingValue = SimplePrometheusRouteConstant.BOOLEAN_TRUE)
public class SimplePrometheusRouteAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(PrometheusRoutePropertiesValidator.class)
    public PrometheusRoutePropertiesValidator prometheusRoutePropertiesValidator() {
        return new DefaultPrometheusRoutePropertiesValidator();
    }

    @Bean
    @ConditionalOnMissingBean(PrometheusRouteTransportFactory.class)
    public PrometheusRouteTransportFactory prometheusRouteTransportFactory() {
        return new DefaultPrometheusRouteTransportFactory();
    }

    @Bean
    @ConditionalOnMissingBean(SimplePrometheusRouteRegistry.class)
    public SimplePrometheusRouteRegistry simplePrometheusRouteRegistry(
            SimplePrometheusRouteProperties properties,
            PrometheusRoutePropertiesValidator validator,
            PrometheusRouteTransportFactory factory) {
        return new SimplePrometheusRouteRegistry(properties, validator, factory);
    }

    @Bean
    @ConditionalOnMissingBean(PrometheusRouteResolver.class)
    public PrometheusRouteResolver prometheusRouteResolver(SimplePrometheusRouteRegistry registry) {
        return new DefaultPrometheusRouteResolver(registry);
    }

    @Bean
    @ConditionalOnMissingBean(PrometheusRouteTemplate.class)
    public PrometheusRouteTemplate prometheusRouteTemplate(SimplePrometheusRouteRegistry registry,
                                                           PrometheusRouteResolver resolver) {
        return new PrometheusRouteTemplate(registry, resolver);
    }
}
