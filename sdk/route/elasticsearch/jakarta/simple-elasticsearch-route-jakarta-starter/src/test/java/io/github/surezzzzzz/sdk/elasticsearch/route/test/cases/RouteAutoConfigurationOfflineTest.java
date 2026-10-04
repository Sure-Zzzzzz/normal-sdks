package io.github.surezzzzzz.sdk.elasticsearch.route.test.cases;

import io.github.surezzzzzz.sdk.elasticsearch.route.configuration.SimpleElasticsearchRouteProperties;
import io.github.surezzzzzz.sdk.elasticsearch.route.exception.ConfigurationException;
import io.github.surezzzzzz.sdk.elasticsearch.route.exception.RouteException;
import io.github.surezzzzzz.sdk.elasticsearch.route.registry.SimpleElasticsearchRouteRegistry;
import io.github.surezzzzzz.sdk.elasticsearch.route.test.SimpleElasticsearchRouteTestApplication;
import io.github.surezzzzzz.sdk.elasticsearch.route.validator.SimpleElasticsearchRouteValidator;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 自动配置离线装配测试。关闭版本探测后，构建客户端和模板不需要连接 Elasticsearch。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleElasticsearchRouteTestApplication.class,
        properties = "io.github.surezzzzzz.sdk.elasticsearch.route.version-detect.enabled=false")
public class RouteAutoConfigurationOfflineTest {

    @Autowired
    private ElasticsearchOperations operations;

    @Autowired
    private SimpleElasticsearchRouteRegistry registry;

    @Autowired
    private SimpleElasticsearchRouteValidator validator;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    public void createsRoutedOperationsAfterValidation() {
        log.info("验证离线自动配置创建路由模板和注册表");

        assertNotNull(validator);
        assertNotNull(registry.getTemplate("primary"));
        assertNotNull(registry.getTemplate("secondary"));
        assertSame(registry.getTemplate("primary").getElasticsearchConverter(),
                registry.getTemplate("secondary").getElasticsearchConverter(),
                "所有数据源模板应使用同一个应用转换器");
        assertNotNull(operations);
    }

    @Test
    public void closesPreviouslyCreatedClientWhenInitializationFails() {
        SimpleElasticsearchRouteProperties testProperties = new SimpleElasticsearchRouteProperties();
        testProperties.setDefaultSource("primary");
        testProperties.getVersionDetect().setEnabled(false);
        SimpleElasticsearchRouteProperties.DataSourceConfig primary =
                new SimpleElasticsearchRouteProperties.DataSourceConfig();
        primary.setUrls("http://localhost:19204");
        SimpleElasticsearchRouteProperties.DataSourceConfig invalid =
                new SimpleElasticsearchRouteProperties.DataSourceConfig();
        invalid.setUrls("ftp://localhost:19214");
        Map<String, SimpleElasticsearchRouteProperties.DataSourceConfig> sources = new LinkedHashMap<>();
        sources.put("primary", primary);
        sources.put("invalid", invalid);
        testProperties.setSources(sources);

        AtomicBoolean hadCreatedClient = new AtomicBoolean();
        SimpleElasticsearchRouteRegistry failingRegistry = new SimpleElasticsearchRouteRegistry(
                testProperties, null, operations.getElasticsearchConverter(), applicationContext) {
            @Override
            public void destroy() {
                hadCreatedClient.set(getLowLevelClient("primary") != null);
                super.destroy();
            }
        };

        assertThrows(ConfigurationException.class, failingRegistry::init);
        assertTrue(hadCreatedClient.get(), "初始化失败前应已创建第一个数据源客户端");
        assertThrows(RouteException.class, () -> failingRegistry.getLowLevelClient("primary"));
    }
}
