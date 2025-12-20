package io.github.surezzzzzz.sdk.elasticsearch.search.configuration;

import io.github.surezzzzzz.sdk.elasticsearch.route.configuration.SimpleElasticsearchRouteConfiguration;
import io.github.surezzzzzz.sdk.elasticsearch.route.configuration.SimpleElasticsearchRouteProperties;
import io.github.surezzzzzz.sdk.elasticsearch.route.registry.SimpleElasticsearchRouteRegistry;
import io.github.surezzzzzz.sdk.elasticsearch.route.support.RouteResolver;
import io.github.surezzzzzz.sdk.elasticsearch.route.support.RouteTemplateProxy;
import io.github.surezzzzzz.sdk.elasticsearch.search.SimpleElasticsearchSearchPackage;
import io.github.surezzzzzz.sdk.elasticsearch.search.annotation.SimpleElasticsearchSearchComponent;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;

import javax.annotation.PostConstruct;
import java.util.Map;

/**
 * Simple Elasticsearch Search Auto Configuration
 *
 * <p>依赖 simple-elasticsearch-route-starter 提供的 SimpleElasticsearchRouteRegistry，
 * 通过路由解析获取版本自适应的 RestHighLevelClient</p>
 *
 * <p>为了避免 Spring Boot 版本兼容性问题（CGLIB 代理失败），在此配置类中优先创建
 * ElasticsearchRestTemplate bean，阻止 route-starter 创建代理 bean。支持智能降级：
 * 单数据源时降级到简单 template，多数据源时报错提示。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "io.github.surezzzzzz.sdk.elasticsearch.search", name = "enable", havingValue = "true")
@AutoConfigureBefore(SimpleElasticsearchRouteConfiguration.class)
@ComponentScan(
        basePackageClasses = SimpleElasticsearchSearchPackage.class,
        includeFilters = @ComponentScan.Filter(SimpleElasticsearchSearchComponent.class)
)
public class SimpleElasticsearchSearchAutoConfiguration {

    /**
     * 创建 ElasticsearchRestTemplate bean
     *
     * <p>优先于 route-starter 创建，避免 Spring Boot 版本兼容性问题（CGLIB 代理 protected 成员失败）</p>
     *
     * <p>智能降级策略：
     * <ul>
     *     <li>尝试创建 RouteTemplateProxy（支持多数据源路由）</li>
     *     <li>失败时判断数据源数量：
     *         <ul>
     *             <li>单数据源：降级到简单 template（路由无意义）</li>
     *             <li>多数据源：抛出异常，提示升级 Spring Boot 版本</li>
     *         </ul>
     *     </li>
     * </ul>
     *
     * @param registry        路由注册表
     * @param routeResolver   路由解析器
     * @param routeProperties 路由配置
     * @return ElasticsearchOperations 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public ElasticsearchOperations elasticsearchRestTemplate(
            SimpleElasticsearchRouteRegistry registry,
            RouteResolver routeResolver,
            SimpleElasticsearchRouteProperties routeProperties) {

        try {
            // 尝试创建路由代理（支持多数据源）
            Map<String, ElasticsearchRestTemplate> templates = registry.getTemplates();
            ElasticsearchRestTemplate defaultTemplate = registry.getTemplate(routeProperties.getDefaultSource());

            ElasticsearchRestTemplate proxy = RouteTemplateProxy.createProxy(templates, defaultTemplate, routeResolver);
            log.info("ElasticsearchRestTemplate with routing support created successfully");
            return proxy;

        } catch (Exception e) {
            // CGLIB 代理失败，智能降级
            int datasourceCount = routeProperties.getSources().size();

            if (datasourceCount > 1) {
                // 多数据源：必须报错
                log.error("Failed to create RouteTemplateProxy for {} datasources. " +
                                "This is caused by Spring Boot version compatibility (CGLIB cannot access protected members). " +
                                "Possible solutions: " +
                                "1. Upgrade to Spring Boot 2.7.x+ (tested compatible), or " +
                                "2. Upgrade to Spring Boot 3.x, or " +
                                "3. Contact support for alternative solutions",
                        datasourceCount, e);
                throw new BeanCreationException(
                        "Cannot create ElasticsearchRestTemplate with routing support for multiple datasources. " +
                                "See error log above for solutions.", e);

            } else {
                // 单数据源：降级到简单 template
                log.warn("Failed to create RouteTemplateProxy, fallback to default ElasticsearchRestTemplate " +
                        "(routing feature disabled, single datasource mode). " +
                        "This may be caused by Spring Boot version compatibility.", e);
                String defaultSource = routeProperties.getDefaultSource();
                RestHighLevelClient client = registry.getHighLevelClient(defaultSource);
                return new ElasticsearchRestTemplate(client);
            }
        }
    }

    @PostConstruct
    public void init() {
        log.info("===== SimpleElasticsearchSearch 自动配置加载成功 =====");
        log.info("使用 simple-elasticsearch-route-starter 提供的版本自适应客户端");
    }
}
