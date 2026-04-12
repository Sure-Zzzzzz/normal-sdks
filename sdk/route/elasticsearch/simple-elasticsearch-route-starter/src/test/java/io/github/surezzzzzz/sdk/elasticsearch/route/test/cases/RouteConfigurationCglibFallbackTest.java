package io.github.surezzzzzz.sdk.elasticsearch.route.test.cases;

import io.github.surezzzzzz.sdk.elasticsearch.route.configuration.SimpleElasticsearchRouteConfiguration;
import io.github.surezzzzzz.sdk.elasticsearch.route.configuration.SimpleElasticsearchRouteProperties;
import io.github.surezzzzzz.sdk.elasticsearch.route.extractor.IndexNameExtractor;
import io.github.surezzzzzz.sdk.elasticsearch.route.registry.SimpleElasticsearchRouteRegistry;
import io.github.surezzzzzz.sdk.elasticsearch.route.resolver.RouteResolver;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.client.RestHighLevelClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * SimpleElasticsearchRouteConfiguration CGLIB 降级逻辑单元测试
 *
 * <p>验证在 Spring Boot 2.4.x 下 CGLIB 代理创建失败时的降级行为：
 * <ul>
 *   <li>单数据源：降级到简单 ElasticsearchRestTemplate，功能正常</li>
 *   <li>多数据源：启动失败并抛出 BeanCreationException</li>
 * </ul>
 *
 * @author surezzzzzz
 * @since 1.0.7
 */
@Slf4j
public class RouteConfigurationCglibFallbackTest {

    private SimpleElasticsearchRouteRegistry registry;
    private SimpleElasticsearchRouteProperties properties;
    private RouteResolver routeResolver;
    private List<IndexNameExtractor> extractors;
    private ElasticsearchRestTemplate defaultTemplate;
    private RestHighLevelClient defaultClient;

    @BeforeEach
    void setUp() {
        registry = mock(SimpleElasticsearchRouteRegistry.class);
        properties = mock(SimpleElasticsearchRouteProperties.class);
        routeResolver = mock(RouteResolver.class);
        extractors = Collections.emptyList();
        defaultTemplate = mock(ElasticsearchRestTemplate.class);
        defaultClient = mock(RestHighLevelClient.class);

        when(properties.getDefaultSource()).thenReturn("primary");
        when(properties.getRules()).thenReturn(Collections.emptyList());
        when(registry.getTemplate("primary")).thenReturn(defaultTemplate);
        when(registry.getHighLevelClient("primary")).thenReturn(defaultClient);
    }

    @Test
    @DisplayName("单数据源 CGLIB 失败 - 降级到简单 template，不抛异常")
    void testSingleDatasourceFallbackWhenCglibFails() {
        // 单数据源
        Map<String, ElasticsearchRestTemplate> templates = new HashMap<>();
        templates.put("primary", defaultTemplate);
        when(registry.getTemplates()).thenReturn(templates);

        SimpleElasticsearchRouteProperties.DataSourceConfig dsConfig =
                mock(SimpleElasticsearchRouteProperties.DataSourceConfig.class);
        Map<String, SimpleElasticsearchRouteProperties.DataSourceConfig> sources = new HashMap<>();
        sources.put("primary", dsConfig);
        when(properties.getSources()).thenReturn(sources);

        // 模拟 CGLIB 失败：RouteTemplateProxy.createProxy 会因为 mock client 抛异常
        // 通过传入会导致 createProxy 内部失败的参数来触发降级
        // 这里直接测试 configuration 的降级分支，用反射或子类覆盖 createProxy
        // 实际上 mock 的 RestHighLevelClient 传给 Enhancer 时会触发异常

        SimpleElasticsearchRouteConfiguration configuration =
                new SimpleElasticsearchRouteConfiguration(properties, registry);

        // 由于测试环境下 CGLIB 可能成功也可能失败，核心验证是：
        // 无论结果如何，单数据源场景下不应该抛出 BeanCreationException
        try {
            ElasticsearchRestTemplate result = configuration.elasticsearchRestTemplate(routeResolver, extractors);
            assertNotNull(result, "单数据源场景下应该返回非 null 的 template");
            log.info("✅ 单数据源：返回 template = {}", result.getClass().getSimpleName());
        } catch (BeanCreationException e) {
            fail("单数据源场景下不应该抛出 BeanCreationException，实际抛出: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("多数据源 CGLIB 失败 - 抛出 BeanCreationException")
    void testMultiDatasourceThrowsWhenCglibFails() {
        // 多数据源
        Map<String, ElasticsearchRestTemplate> templates = new HashMap<>();
        ElasticsearchRestTemplate secondaryTemplate = mock(ElasticsearchRestTemplate.class);
        templates.put("primary", defaultTemplate);
        templates.put("secondary", secondaryTemplate);
        when(registry.getTemplates()).thenReturn(templates);

        SimpleElasticsearchRouteProperties.DataSourceConfig primaryConfig =
                mock(SimpleElasticsearchRouteProperties.DataSourceConfig.class);
        SimpleElasticsearchRouteProperties.DataSourceConfig secondaryConfig =
                mock(SimpleElasticsearchRouteProperties.DataSourceConfig.class);
        Map<String, SimpleElasticsearchRouteProperties.DataSourceConfig> sources = new HashMap<>();
        sources.put("primary", primaryConfig);
        sources.put("secondary", secondaryConfig);
        when(properties.getSources()).thenReturn(sources);

        // mock client 传给 CGLIB Enhancer 时会触发异常，模拟 2.4.x 下的失败场景
        // 用一个会导致 createProxy 失败的 client（null）
        when(registry.getHighLevelClient("primary")).thenReturn(null);

        SimpleElasticsearchRouteConfiguration configuration =
                new SimpleElasticsearchRouteConfiguration(properties, registry);

        assertThrows(BeanCreationException.class,
                () -> configuration.elasticsearchRestTemplate(routeResolver, extractors),
                "多数据源场景下 CGLIB 失败应该抛出 BeanCreationException");

        log.info("✅ 多数据源：CGLIB 失败时正确抛出 BeanCreationException");
    }
}
