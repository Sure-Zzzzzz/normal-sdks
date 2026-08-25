package io.github.surezzzzzz.sdk.prometheus.client.configuration;

import io.github.surezzzzzz.sdk.prometheus.client.SimplePrometheusClientPackage;
import io.github.surezzzzzz.sdk.prometheus.client.annotation.SimplePrometheusClientComponent;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Simple Prometheus Client 自动配置。
 *
 * <p>与仓库既有形态对齐：通过受限扫描链装配 {@code SimplePrometheusClientComponent}
 * 组件，配置类本身不携带类级条件。</p>
 *
 * @author surezzzzzz
 */
@Configuration
@ComponentScan(basePackageClasses = SimplePrometheusClientPackage.class, useDefaultFilters = false,
        includeFilters = @ComponentScan.Filter(SimplePrometheusClientComponent.class))
public class SimplePrometheusClientAutoConfiguration {
}
