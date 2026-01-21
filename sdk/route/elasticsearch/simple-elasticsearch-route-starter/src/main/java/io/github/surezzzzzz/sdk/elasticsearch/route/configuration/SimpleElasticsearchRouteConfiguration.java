package io.github.surezzzzzz.sdk.elasticsearch.route.configuration;

import io.github.surezzzzzz.sdk.elasticsearch.route.SimpleElasticsearchRoutePackage;
import io.github.surezzzzzz.sdk.elasticsearch.route.annotation.SimpleElasticsearchRouteComponent;
import io.github.surezzzzzz.sdk.elasticsearch.route.extractor.IndexNameExtractor;
import io.github.surezzzzzz.sdk.elasticsearch.route.proxy.RouteTemplateProxy;
import io.github.surezzzzzz.sdk.elasticsearch.route.registry.SimpleElasticsearchRouteRegistry;
import io.github.surezzzzzz.sdk.elasticsearch.route.resolver.RouteResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Simple Elasticsearch Route Auto Configuration
 *
 * @author surezzzzzz
 */
@Slf4j
@Configuration
@ComponentScan(
        basePackageClasses = SimpleElasticsearchRoutePackage.class,
        includeFilters = @ComponentScan.Filter(SimpleElasticsearchRouteComponent.class)
)
@ConditionalOnProperty(prefix = "io.github.surezzzzzz.sdk.elasticsearch.route", name = "enable", havingValue = "true")
@RequiredArgsConstructor
public class SimpleElasticsearchRouteConfiguration {

    private final SimpleElasticsearchRouteProperties properties;
    private final SimpleElasticsearchRouteRegistry routeRegistry;

    /**
     * 创建路由代理 ElasticsearchRestTemplate
     */
    @Bean
    @Primary
    @ConditionalOnMissingBean(name = "elasticsearchRestTemplate")
    public ElasticsearchRestTemplate elasticsearchRestTemplate(
            RouteResolver routeResolver,
            List<IndexNameExtractor> indexNameExtractors) {

        Map<String, ElasticsearchRestTemplate> templatesMap = routeRegistry.getTemplates();

        log.info("Creating routing ElasticsearchRestTemplate proxy with {} datasource(s)", templatesMap.size());
        log.info("Configured route rules: {} rule(s)", properties.getRules().size());
        log.info("Loaded {} IndexNameExtractor(s): {}",
                indexNameExtractors.size(),
                indexNameExtractors.stream()
                        .map(e -> e.getClass().getSimpleName())
                        .toArray());

        String defaultKey = properties.getDefaultSource();
        ElasticsearchRestTemplate defaultTemplate = routeRegistry.getTemplate(defaultKey);

        log.info("Default Elasticsearch datasource set to: [{}]", defaultKey);

        ElasticsearchRestTemplate proxy = RouteTemplateProxy.createProxy(
                templatesMap,
                defaultTemplate,
                routeResolver,
                indexNameExtractors,
                routeRegistry.getHighLevelClient(defaultKey)
        );

        log.info("✅ Routing ElasticsearchRestTemplate initialized successfully");
        return proxy;
    }
}

