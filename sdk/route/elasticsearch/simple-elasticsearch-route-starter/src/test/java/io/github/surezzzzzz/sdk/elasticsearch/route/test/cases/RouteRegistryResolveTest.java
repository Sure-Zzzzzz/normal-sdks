package io.github.surezzzzzz.sdk.elasticsearch.route.test.cases;

import io.github.surezzzzzz.sdk.elasticsearch.route.configuration.SimpleElasticsearchRouteProperties;
import io.github.surezzzzzz.sdk.elasticsearch.route.exception.RouteException;
import io.github.surezzzzzz.sdk.elasticsearch.route.registry.SimpleElasticsearchRouteRegistry;
import io.github.surezzzzzz.sdk.elasticsearch.route.resolver.RoutePatternMatcher;
import io.github.surezzzzzz.sdk.elasticsearch.route.resolver.RouteResolver;
import io.github.surezzzzzz.sdk.elasticsearch.route.test.SimpleElasticsearchRouteTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SimpleElasticsearchRouteRegistry.resolveDataSourceOrThrow 单元测试
 *
 * @author Sure
 * @since 1.0.3
 */
@Slf4j
@SpringBootTest(classes = SimpleElasticsearchRouteTestApplication.class)
public class RouteRegistryResolveTest {

    @Test
    public void testResolveSingleDatasource() {
        log.info("=== testResolveSingleDatasource ===");
        SimpleElasticsearchRouteRegistry registry = createRegistryWithRules();

        String ds = registry.resolveDataSourceOrThrow(new String[]{"order-2025"});
        assertEquals("primary", ds);

        String ds2 = registry.resolveDataSourceOrThrow(new String[]{"user-1"});
        assertEquals("secondary", ds2);
    }

    @Test
    public void testResolveDefaultOnEmpty() {
        log.info("=== testResolveDefaultOnEmpty ===");
        SimpleElasticsearchRouteRegistry registry = createRegistryWithRules();

        assertEquals("primary", registry.resolveDataSourceOrThrow(null));
        assertEquals("primary", registry.resolveDataSourceOrThrow(new String[]{}));
    }

    @Test
    public void testCrossDatasourceNotSupported() {
        log.info("=== testCrossDatasourceNotSupported ===");
        SimpleElasticsearchRouteRegistry registry = createRegistryWithRules();

        RouteException ex = assertThrows(RouteException.class,
                () -> registry.resolveDataSourceOrThrow(new String[]{"order-2025", "user-1"}));
        assertTrue(ex.getMessage().contains("Cross datasource"));
    }

    private SimpleElasticsearchRouteRegistry createRegistryWithRules() {
        SimpleElasticsearchRouteProperties properties = new SimpleElasticsearchRouteProperties();
        properties.setEnable(true);
        properties.setDefaultSource("primary");
        properties.setSources(new HashMap<>());

        List<SimpleElasticsearchRouteProperties.RouteRule> rules = new ArrayList<>();
        rules.add(createRule("user-", "prefix", "secondary", 1));
        rules.add(createRule("order-", "prefix", "primary", 1));
        properties.setRules(rules);

        RoutePatternMatcher matcher = new RoutePatternMatcher();
        RouteResolver resolver = new RouteResolver(properties, matcher);
        resolver.init();

        return new SimpleElasticsearchRouteRegistry(properties, resolver);
    }

    private SimpleElasticsearchRouteProperties.RouteRule createRule(String pattern, String type, String ds, int priority) {
        SimpleElasticsearchRouteProperties.RouteRule rule = new SimpleElasticsearchRouteProperties.RouteRule();
        rule.setPattern(pattern);
        rule.setType(type);
        rule.setDatasource(ds);
        rule.setPriority(priority);
        rule.setEnable(true);
        return rule;
    }
}

