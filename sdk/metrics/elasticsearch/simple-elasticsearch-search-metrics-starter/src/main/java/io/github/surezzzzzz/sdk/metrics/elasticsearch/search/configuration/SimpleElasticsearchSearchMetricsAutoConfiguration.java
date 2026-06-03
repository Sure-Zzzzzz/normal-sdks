package io.github.surezzzzzz.sdk.metrics.elasticsearch.search.configuration;

import io.github.surezzzzzz.sdk.metrics.elasticsearch.search.SimpleElasticsearchSearchMetricsPackage;
import io.github.surezzzzzz.sdk.metrics.elasticsearch.search.annotation.SimpleElasticsearchSearchMetricsComponent;
import io.github.surezzzzzz.sdk.metrics.elasticsearch.search.constant.SimpleElasticsearchSearchMetricsConstant;
import io.github.surezzzzzz.sdk.metrics.elasticsearch.search.listener.ElasticsearchSearchMetricsListener;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;

/**
 * simple-elasticsearch-search 指标自动配置
 *
 * @author surezzzzzz
 */
@Slf4j
@Configuration
@ConditionalOnClass(name = "io.micrometer.core.instrument.MeterRegistry")
@EnableConfigurationProperties(SimpleElasticsearchSearchMetricsProperties.class)
@ComponentScan(
        basePackageClasses = SimpleElasticsearchSearchMetricsPackage.class,
        includeFilters = @ComponentScan.Filter(SimpleElasticsearchSearchMetricsComponent.class),
        useDefaultFilters = false
)
public class SimpleElasticsearchSearchMetricsAutoConfiguration {

    @Value("${spring.application.name:}")
    private String applicationName;

    @PostConstruct
    public void init() {
        log.info("===== SimpleElasticsearchSearch Metrics 自动配置加载成功 =====");
    }

    /**
     * 解析业务模块标识 me：
     * 优先取 spring.application.name，其次取配置项 me，均无则为 "unknown"
     *
     * @param properties 配置项
     * @return me 标签值
     */
    private String resolveMe(SimpleElasticsearchSearchMetricsProperties properties) {
        if (applicationName != null && !applicationName.trim().isEmpty()) {
            return applicationName.trim();
        }
        String configMe = properties.getMe();
        if (configMe != null && !configMe.trim().isEmpty()) {
            return configMe.trim();
        }
        return SimpleElasticsearchSearchMetricsConstant.DEFAULT_ME;
    }

    @Bean
    @ConditionalOnProperty(
            prefix = SimpleElasticsearchSearchMetricsConstant.CONFIG_PREFIX,
            name = "enable",
            havingValue = "true",
            matchIfMissing = true
    )
    public ElasticsearchSearchMetricsListener elasticsearchSearchMetricsListener(
            MeterRegistry registry,
            SimpleElasticsearchSearchMetricsProperties properties) {
        return new ElasticsearchSearchMetricsListener(registry, resolveMe(properties));
    }
}
