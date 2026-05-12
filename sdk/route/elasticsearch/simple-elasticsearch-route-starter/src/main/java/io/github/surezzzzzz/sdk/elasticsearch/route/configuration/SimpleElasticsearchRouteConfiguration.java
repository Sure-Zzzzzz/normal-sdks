package io.github.surezzzzzz.sdk.elasticsearch.route.configuration;

import io.github.surezzzzzz.sdk.elasticsearch.route.SimpleElasticsearchRoutePackage;
import io.github.surezzzzzz.sdk.elasticsearch.route.annotation.SimpleElasticsearchRouteComponent;
import io.github.surezzzzzz.sdk.elasticsearch.route.constant.SimpleElasticsearchRouteConstant;
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
import org.springframework.boot.context.properties.EnableConfigurationProperties;
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
@EnableConfigurationProperties(SimpleElasticsearchRouteProperties.class)
@ComponentScan(
        basePackageClasses = SimpleElasticsearchRoutePackage.class,
        includeFilters = @ComponentScan.Filter(SimpleElasticsearchRouteComponent.class)
)
@ConditionalOnProperty(prefix = SimpleElasticsearchRouteConstant.CONFIG_PREFIX, name = "enable", havingValue = "true")
@RequiredArgsConstructor
public class SimpleElasticsearchRouteConfiguration {

    /**
     * 检测 ES Java Client 是否为 7.9+ 版本
     * <p>{@code org.elasticsearch.client.core.MainResponse} 是 ES Client 7.9+ 才引入的类，
     * 6.8.x（对应 Spring Boot 2.3.x）中不存在。CGLIB 代理依赖 7.x 的类链路，
     * 在 6.8.x 下会触发 {@code BootstrapMethodError}。</p>
     */
    private static final boolean ES_CLIENT_7X_AVAILABLE;

    static {
        boolean available;
        try {
            Class.forName(SimpleElasticsearchRouteConstant.ES_CLIENT_7X_MARKER_CLASS);
            available = true;
        } catch (ClassNotFoundException e) {
            available = false;
        }
        ES_CLIENT_7X_AVAILABLE = available;
    }

    private final SimpleElasticsearchRouteProperties properties;
    private final SimpleElasticsearchRouteRegistry routeRegistry;

    /**
     * 创建路由代理 ElasticsearchRestTemplate
     *
     * <p>优先创建 {@link RouteTemplateProxy} 支持多数据源路由。
     * 在低版本 Spring Boot 下按以下策略降级：
     * <ul>
     *   <li>ES Client 6.8.x（Spring Boot 2.3.x）：CGLIB 代理依赖 7.x 类链路，跳过代理创建</li>
     *   <li>Spring Boot 2.4.x：CGLIB 无法访问 protected 成员，代理创建失败</li>
     *   <li>单数据源：降级到简单 {@link ElasticsearchRestTemplate}，功能正常</li>
     *   <li>多数据源：启动失败并报错，路由失效会导致数据写入错误数据源</li>
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

        // ES Client 6.8.x（Spring Boot 2.3.x）前置检测，避免触发 BootstrapMethodError
        if (!ES_CLIENT_7X_AVAILABLE) {
            int datasourceCount = properties.getSources().size();
            if (datasourceCount > 1) {
                log.error(SimpleElasticsearchRouteConstant.MSG_ES_CLIENT_6X_MULTI);
                throw new BeanCreationException("elasticsearchRestTemplate",
                        SimpleElasticsearchRouteConstant.MSG_ES_CLIENT_6X_MULTI);
            } else {
                log.warn(SimpleElasticsearchRouteConstant.MSG_ES_CLIENT_6X_SINGLE);
                return defaultTemplate;
            }
        }

        try {
            ElasticsearchRestTemplate proxy = RouteTemplateProxy.createProxy(
                    templatesMap, defaultTemplate, routeResolver, indexNameExtractors, defaultClient);
            log.info("Routing ElasticsearchRestTemplate initialized successfully");
            return proxy;

        } catch (Exception e) {
            // Spring Boot 2.4.x 下 CGLIB 无法访问 protected 成员，代理创建失败
            int datasourceCount = properties.getSources().size();
            if (datasourceCount > 1) {
                log.error("Failed to create RouteTemplateProxy for {} datasource(s). " +
                        "This is likely caused by CGLIB limitation in Spring Boot 2.4.x " +
                        "(cannot access protected members of AbstractElasticsearchTemplate). " +
                        "Please upgrade to Spring Boot 2.7.x+.", datasourceCount, e);
                throw new BeanCreationException("elasticsearchRestTemplate",
                        "Cannot create routing ElasticsearchRestTemplate proxy for multiple datasources. " +
                                "Please upgrade to Spring Boot 2.7.x+.", e);
            } else {
                log.warn("Failed to create RouteTemplateProxy, falling back to simple ElasticsearchRestTemplate " +
                        "(routing disabled, single datasource mode). " +
                        "This is likely caused by CGLIB limitation in Spring Boot 2.4.x.", e);
                return defaultTemplate;
            }
        }
    }
}
