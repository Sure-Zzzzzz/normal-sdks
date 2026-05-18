package io.github.surezzzzzz.sdk.metrics.aksk.server.configuration;

import io.github.surezzzzzz.sdk.metrics.aksk.server.SimpleAkskServerMetricsPackage;
import io.github.surezzzzzz.sdk.metrics.aksk.server.annotation.SimpleAkskServerMetricsComponent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;

/**
 * AKSK Server Metrics Auto Configuration
 *
 * @author surezzzzzz
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(SimpleAkskServerMetricsProperties.class)
@ComponentScan(
        basePackageClasses = SimpleAkskServerMetricsPackage.class,
        includeFilters = @ComponentScan.Filter(SimpleAkskServerMetricsComponent.class),
        useDefaultFilters = false
)
@ConditionalOnClass(name = "io.micrometer.core.instrument.MeterRegistry")
public class SimpleAkskServerMetricsAutoConfiguration {

    @PostConstruct
    public void init() {
        log.info("===== AKSK Server Metrics 自动配置加载成功 =====");
    }
}
