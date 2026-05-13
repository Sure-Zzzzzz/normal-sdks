package io.github.surezzzzzz.sdk.metrics.limiter.configuration;

import io.github.surezzzzzz.sdk.metrics.limiter.SmartRedisLimiterMetricsPackage;
import io.github.surezzzzzz.sdk.metrics.limiter.annotation.SmartRedisLimiterMetricsComponent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;

/**
 * SmartRedisLimiter 限流指标自动配置
 *
 * @author surezzzzzz
 */
@Slf4j
@Configuration
@ConditionalOnClass(name = "io.micrometer.core.instrument.MeterRegistry")
@EnableConfigurationProperties(SmartRedisLimiterMetricsProperties.class)
@ComponentScan(
        basePackageClasses = SmartRedisLimiterMetricsPackage.class,
        includeFilters = @ComponentScan.Filter(SmartRedisLimiterMetricsComponent.class),
        useDefaultFilters = false
)
public class SmartRedisLimiterMetricsAutoConfiguration {

    @PostConstruct
    public void init() {
        log.info("===== SmartRedisLimiter Metrics 自动配置加载成功 =====");
    }
}
