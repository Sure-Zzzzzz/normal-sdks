package io.github.surezzzzzz.sdk.metrics.aksk.resource.configuration;

import io.github.surezzzzzz.sdk.metrics.aksk.resource.SimpleAkskResourceMetricsPackage;
import io.github.surezzzzzz.sdk.metrics.aksk.resource.annotation.SimpleAkskResourceMetricsComponent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;

/**
 * AKSK Resource Metrics Auto Configuration
 *
 * @author surezzzzzz
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(SimpleAkskResourceMetricsProperties.class)
@ComponentScan(
        basePackageClasses = SimpleAkskResourceMetricsPackage.class,
        includeFilters = @ComponentScan.Filter(SimpleAkskResourceMetricsComponent.class),
        useDefaultFilters = false
)
@ConditionalOnClass(name = "io.micrometer.core.instrument.MeterRegistry")
public class SimpleAkskResourceMetricsAutoConfiguration {

    @PostConstruct
    public void init() {
        log.info("===== AKSK Resource Metrics 自动配置加载成功 =====");
    }
}