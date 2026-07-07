package io.github.surezzzzzz.sdk.elasticsearch.route.test.cases;

import io.github.surezzzzzz.sdk.elasticsearch.route.configuration.SimpleElasticsearchRouteConfiguration;
import io.github.surezzzzzz.sdk.elasticsearch.route.configuration.SimpleElasticsearchRouteProperties;
import io.github.surezzzzzz.sdk.elasticsearch.route.extractor.IndexNameExtractor;
import io.github.surezzzzzz.sdk.elasticsearch.route.registry.SimpleElasticsearchRouteRegistry;
import io.github.surezzzzzz.sdk.elasticsearch.route.resolver.DefaultWriteIndexResolver;
import io.github.surezzzzzz.sdk.elasticsearch.route.resolver.RouteResolver;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.client.RestHighLevelClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;

import java.time.ZoneId;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * SimpleElasticsearchRouteConfiguration AUTO 模式代理创建逻辑单元测试
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
    @DisplayName("单数据源 AUTO 模式返回代理对象不抛异常")
    void testSingleDatasourceAutoModeReturnsTemplate() {
        Map<String, ElasticsearchRestTemplate> templates = new HashMap<>();
        templates.put("primary", defaultTemplate);
        when(registry.getTemplates()).thenReturn(templates);
        SimpleElasticsearchRouteProperties.DataSourceConfig dsConfig =
                mock(SimpleElasticsearchRouteProperties.DataSourceConfig.class);
        Map<String, SimpleElasticsearchRouteProperties.DataSourceConfig> sources = new HashMap<>();
        sources.put("primary", dsConfig);
        when(properties.getSources()).thenReturn(sources);

        SimpleElasticsearchRouteConfiguration configuration =
                new SimpleElasticsearchRouteConfiguration(properties, registry);

        try {
            ElasticsearchRestTemplate result = configuration.elasticsearchRestTemplate(routeResolver, extractors,
                    new DefaultWriteIndexResolver(routeResolver, ZoneId.systemDefault()));
            log.info("单数据源返回 template = {}", result == null ? null : result.getClass().getSimpleName());
            assertNotNull(result, "单数据源场景下应该返回非 null 的 template");
        } catch (BeanCreationException e) {
            fail("单数据源场景下不应该抛出 BeanCreationException，实际抛出: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("多数据源 AUTO 模式 - CGLIB+JDK 均失败时抛出 BeanCreationException")
    void testMultiDatasourceBothProxyFailThrowsBeanCreationException() {
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
        when(registry.getHighLevelClient("primary")).thenReturn(null);
        SimpleElasticsearchRouteConfiguration configuration =
                new SimpleElasticsearchRouteConfiguration(properties, registry);

        assertThrows(BeanCreationException.class,
                () -> configuration.elasticsearchRestTemplate(routeResolver, extractors,
                        new DefaultWriteIndexResolver(routeResolver, ZoneId.systemDefault())),
                "多数据源场景下 CGLIB 失败应该抛出 BeanCreationException");
        log.info("多数据源：CGLIB+JDK 均失败时正确抛出 BeanCreationException");
    }
}
