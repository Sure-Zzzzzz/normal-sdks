package io.github.surezzzzzz.sdk.prometheus.configuration;

import io.github.surezzzzzz.sdk.prometheus.PrometheusPackage;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan(
        basePackageClasses = PrometheusPackage.class,
        includeFilters = @ComponentScan.Filter(PrometheusComponent.class)
)
public class PrometheusConfiguration {

}
