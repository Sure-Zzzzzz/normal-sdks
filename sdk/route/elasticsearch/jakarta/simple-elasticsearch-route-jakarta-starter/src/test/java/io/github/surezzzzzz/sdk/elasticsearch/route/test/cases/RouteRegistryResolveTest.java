package io.github.surezzzzzz.sdk.elasticsearch.route.test.cases;

import io.github.surezzzzzz.sdk.elasticsearch.route.configuration.SimpleElasticsearchRouteProperties;
import io.github.surezzzzzz.sdk.elasticsearch.route.exception.RouteException;
import io.github.surezzzzzz.sdk.elasticsearch.route.matcher.RoutePatternMatcher;
import io.github.surezzzzzz.sdk.elasticsearch.route.resolver.RouteResolver;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

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
public class RouteRegistryResolveTest {

    @Test
    public void testResolveSingleDatasource() {
        log.info("=== testResolveSingleDatasource ===");
        RouteResolver resolver = createResolverWithRules();

        String ds = resolver.resolveDataSourceOrThrow(new String[]{"order-2025"});
        assertEquals("primary", ds);

        String ds2 = resolver.resolveDataSourceOrThrow(new String[]{"user-1"});
        assertEquals("secondary", ds2);
    }

    @Test
    public void testResolveDefaultOnEmpty() {
        log.info("=== testResolveDefaultOnEmpty ===");
        RouteResolver resolver = createResolverWithRules();

        assertEquals("primary", resolver.resolveDataSourceOrThrow(null));
        assertEquals("primary", resolver.resolveDataSourceOrThrow(new String[]{}));
    }

    @Test
    public void testCrossDatasourceNotSupported() {
        log.info("=== testCrossDatasourceNotSupported ===");
        RouteResolver resolver = createResolverWithRules();

        RouteException ex = assertThrows(RouteException.class,
                () -> resolver.resolveDataSourceOrThrow(new String[]{"order-2025", "user-1"}));
        assertTrue(ex.getMessage().contains("不支持跨数据源操作"));
    }

    private RouteResolver createResolverWithRules() {
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

        return resolver;
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
