package io.github.surezzzzzz.sdk.elasticsearch.route.configuration;

import io.github.surezzzzzz.sdk.elasticsearch.route.SimpleElasticsearchRoutePackage;
import io.github.surezzzzzz.sdk.elasticsearch.route.annotation.SimpleElasticsearchRouteComponent;
import io.github.surezzzzzz.sdk.elasticsearch.route.extractor.IndexNameExtractor;
import io.github.surezzzzzz.sdk.elasticsearch.route.proxy.RouteTemplateProxy;
import io.github.surezzzzzz.sdk.elasticsearch.route.registry.SimpleElasticsearchRouteRegistry;
import io.github.surezzzzzz.sdk.elasticsearch.route.resolver.RouteResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.factory.BeanCreationException;
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
     *
     * <p>优先创建 {@link RouteTemplateProxy} 支持多数据源路由。
     * 在 Spring Boot 2.4.x 下，CGLIB 无法访问 {@code AbstractElasticsearchTemplate} 的 protected 成员，
     * 代理创建会失败，此时按以下策略降级：
     * <ul>
     *   <li>单数据源：降级到简单 {@link ElasticsearchRestTemplate}，路由无意义，功能正常</li>
     *   <li>多数据源：启动失败并报错，路由失效会导致数据写入错误数据源，必须升级 Spring Boot 到 2.7.x+</li>
     * </ul>
     * </p>
     */
    @Bean
    @Primary
    @ConditionalOnMissingBean(name = "elasticsearchRestTemplate")
    public ElasticsearchRestTemplate elasticsearchRestTemplate(
            RouteResolver routeResolver,
            List<IndexNameExtractor> indexNameExtractors) {

        Map<String, ElasticsearchRestTemplate> templatesMap = routeRegistry.getTemplates();
        String defaultKey = properties.getDefaultSource();
        ElasticsearchRestTemplate defaultTemplate = routeRegistry.getTemplate(defaultKey);
        RestHighLevelClient defaultClient = routeRegistry.getHighLevelClient(defaultKey);

        log.info("Creating routing ElasticsearchRestTemplate proxy with {} datasource(s)", templatesMap.size());
        log.info("Configured route rules: {} rule(s)", properties.getRules().size());
        log.info("Loaded {} IndexNameExtractor(s): {}",
                indexNameExtractors.size(),
                indexNameExtractors.stream()
                        .map(e -> e.getClass().getSimpleName())
                        .toArray());
        log.info("Default Elasticsearch datasource set to: [{}]", defaultKey);

        try {
            ElasticsearchRestTemplate proxy = RouteTemplateProxy.createProxy(
                    templatesMap, defaultTemplate, routeResolver, indexNameExtractors, defaultClient);
            log.info("✅ Routing ElasticsearchRestTemplate initialized successfully");
            return proxy;

        } catch (Exception e) {
            // Spring Boot 2.4.x 下 CGLIB 无法访问 protected 成员，代理创建失败
            int datasourceCount = properties.getSources().size();
            if (datasourceCount > 1) {
                // 多数据源：路由失效会导致数据写入错误数据源，必须报错
                log.error("Failed to create RouteTemplateProxy for {} datasource(s). " +
                        "This is likely caused by CGLIB limitation in Spring Boot 2.4.x " +
                        "(cannot access protected members of AbstractElasticsearchTemplate). " +
                        "Please upgrade to Spring Boot 2.7.x+.", datasourceCount, e);
                throw new BeanCreationException("elasticsearchRestTemplate",
                        "Cannot create routing ElasticsearchRestTemplate proxy for multiple datasources. " +
                                "Please upgrade to Spring Boot 2.7.x+.", e);
            } else {
                // 单数据源：路由无意义，降级到简单 template，功能正常
                log.warn("Failed to create RouteTemplateProxy, falling back to simple ElasticsearchRestTemplate " +
                        "(routing disabled, single datasource mode). " +
                        "This is likely caused by CGLIB limitation in Spring Boot 2.4.x.", e);
                return defaultTemplate;
            }
        }
    }
}

