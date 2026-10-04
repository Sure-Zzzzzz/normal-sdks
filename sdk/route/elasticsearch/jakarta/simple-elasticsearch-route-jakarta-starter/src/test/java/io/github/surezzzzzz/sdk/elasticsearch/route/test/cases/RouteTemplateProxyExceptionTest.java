package io.github.surezzzzzz.sdk.elasticsearch.route.test.cases;

import io.github.surezzzzzz.sdk.elasticsearch.route.configuration.SimpleElasticsearchRouteProperties;
import io.github.surezzzzzz.sdk.elasticsearch.route.extractor.*;
import io.github.surezzzzzz.sdk.elasticsearch.route.matcher.RoutePatternMatcher;
import io.github.surezzzzzz.sdk.elasticsearch.route.proxy.JdkRouteTemplateProxy;
import io.github.surezzzzzz.sdk.elasticsearch.route.proxy.RouteRoutingInterceptor;
import io.github.surezzzzzz.sdk.elasticsearch.route.resolver.DefaultWriteIndexResolver;
import io.github.surezzzzzz.sdk.elasticsearch.route.resolver.RouteResolver;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;

import java.lang.reflect.Proxy;
import java.time.ZoneId;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * RouteTemplateProxy 异常解包离线测试。
 *
 * @author surezzzzzz
 */
@Slf4j
public class RouteTemplateProxyExceptionTest {

    @Test
    public void preservesTemplateRuntimeException() {
        log.info("验证路由代理保留底层运行时异常类型");

        IllegalStateException expected = new IllegalStateException("template-failure");
        ElasticsearchOperations template = createFailingTemplate(expected);
        ElasticsearchOperations operations = createRoutedOperations(template);

        IllegalStateException actual = assertThrows(IllegalStateException.class,
                () -> operations.save(new Object()));

        assertSame(expected, actual, "路由代理不能把底层异常包装为 InvocationTargetException");
    }

    private ElasticsearchOperations createRoutedOperations(ElasticsearchOperations template) {
        SimpleElasticsearchRouteProperties properties = new SimpleElasticsearchRouteProperties();
        properties.setDefaultSource("primary");
        properties.setSources(Collections.singletonMap(
                "primary", new SimpleElasticsearchRouteProperties.DataSourceConfig()));
        RouteResolver resolver = new RouteResolver(properties, new RoutePatternMatcher());
        resolver.init();

        Map<String, ElasticsearchOperations> templates = new LinkedHashMap<>();
        templates.put("primary", template);
        List<IndexNameExtractor> extractors = Arrays.asList(
                new IndexCoordinatesExtractor(),
                new IndexQueryExtractor(),
                new EntityObjectExtractor(),
                new ClassTypeExtractor());
        RouteRoutingInterceptor interceptor = new RouteRoutingInterceptor(
                templates, resolver, extractors, Collections.emptyMap(),
                new DefaultWriteIndexResolver(resolver, ZoneId.of("UTC")));
        return JdkRouteTemplateProxy.createProxy(interceptor, template);
    }

    private ElasticsearchOperations createFailingTemplate(IllegalStateException expected) {
        return (ElasticsearchOperations) Proxy.newProxyInstance(
                ElasticsearchOperations.class.getClassLoader(),
                new Class<?>[]{ElasticsearchOperations.class},
                (proxy, method, args) -> {
                    if ("save".equals(method.getName())) {
                        throw expected;
                    }
                    if (ElasticsearchOperations.class.isAssignableFrom(method.getReturnType())) {
                        return proxy;
                    }
                    return null;
                });
    }
}
