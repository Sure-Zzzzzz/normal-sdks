package io.github.surezzzzzz.sdk.elasticsearch.search.test.cases;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.elasticsearch.route.configuration.SimpleElasticsearchRouteProperties;
import io.github.surezzzzzz.sdk.elasticsearch.route.matcher.RoutePatternMatcher;
import io.github.surezzzzzz.sdk.elasticsearch.route.registry.SimpleElasticsearchRouteRegistry;
import io.github.surezzzzzz.sdk.elasticsearch.route.resolver.RouteResolver;
import io.github.surezzzzzz.sdk.elasticsearch.search.agg.AggregationDslBuilder;
import io.github.surezzzzzz.sdk.elasticsearch.search.configuration.SimpleElasticsearchSearchAutoConfiguration;
import io.github.surezzzzzz.sdk.elasticsearch.search.endpoint.SearchExpressionApiEndpoint;
import io.github.surezzzzzz.sdk.elasticsearch.search.endpoint.SimpleElasticsearchSearchApiEndpoint;
import io.github.surezzzzzz.sdk.elasticsearch.search.engine.SearchEngine;
import io.github.surezzzzzz.sdk.elasticsearch.search.expression.ExpressionService;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.SearchDslBuilder;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.SearchResponseParser;
import io.github.surezzzzzz.sdk.elasticsearch.search.support.SearchPayloadCodec;
import io.github.surezzzzzz.sdk.expression.condition.parser.configuration.ConditionExpressionParserAutoConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 默认业务 Bean 让位、宿主 JSON 隔离与配置拒绝通过独立 Spring 容器核验。
 */
@Slf4j
class SearchExtensionTest {
    private ApplicationContextRunner context() {
        SimpleElasticsearchRouteProperties properties = new SimpleElasticsearchRouteProperties();
        properties.setDefaultSource("primary");
        properties.getSources().put("primary", new SimpleElasticsearchRouteProperties.DataSourceConfig());
        RouteResolver routes = new RouteResolver(properties, new RoutePatternMatcher());
        routes.init();
        return new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(ConditionExpressionParserAutoConfiguration.class, SimpleElasticsearchSearchAutoConfiguration.class))
                .withPropertyValues("io.github.surezzzzzz.sdk.elasticsearch.search.enable=true",
                        "io.github.surezzzzzz.sdk.elasticsearch.search.indices[0].name=sample-record",
                        "io.github.surezzzzzz.sdk.elasticsearch.search.indices[0].lazy-load=true")
                .withBean(SimpleElasticsearchRouteProperties.class, () -> properties).withBean(RouteResolver.class, () -> routes)
                .withBean(SimpleElasticsearchRouteRegistry.class, () -> mock(SimpleElasticsearchRouteRegistry.class));
    }

    @Test
    void defaultConfigurationIsNonWebAndIndependentOfHostJson() {
        ObjectMapper host = mock(ObjectMapper.class);
        context().withBean(ObjectMapper.class, () -> host).run(context -> {
            assertNull(context.getStartupFailure());
            assertEquals(1, context.getBeansOfType(SearchEngine.class).size());
            assertEquals(1, context.getBeansOfType(SearchDslBuilder.class).size());
            assertEquals(0, context.getBeansOfType(SimpleElasticsearchSearchApiEndpoint.class).size());
            assertEquals(0, context.getBeansOfType(SearchExpressionApiEndpoint.class).size());
            assertEquals(1, context.getBeansOfType(ExpressionService.class).size());
            assertEquals(1, context.getBean(SearchPayloadCodec.class).decode("{\"amount\":1}").get("amount"));
            verifyNoInteractions(host);
        });
    }

    @Test
    void everyPublicStrategyYieldsToHostBean() {
        SearchEngine engine = mock(SearchEngine.class);
        SearchPayloadCodec codec = mock(SearchPayloadCodec.class);
        SearchDslBuilder queries = mock(SearchDslBuilder.class);
        AggregationDslBuilder aggs = mock(AggregationDslBuilder.class);
        SearchResponseParser parser = mock(SearchResponseParser.class);
        ExpressionService expressions = mock(ExpressionService.class);
        context().withBean(SearchEngine.class, () -> engine).withBean(SearchPayloadCodec.class, () -> codec)
                .withBean(SearchDslBuilder.class, () -> queries).withBean(AggregationDslBuilder.class, () -> aggs).withBean(SearchResponseParser.class, () -> parser)
                .withBean(ExpressionService.class, () -> expressions)
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertSame(engine, context.getBean(SearchEngine.class));
                    assertSame(codec, context.getBean(SearchPayloadCodec.class));
                    assertSame(queries, context.getBean(SearchDslBuilder.class));
                    assertSame(aggs, context.getBean(AggregationDslBuilder.class));
                    assertSame(parser, context.getBean(SearchResponseParser.class));
                    assertSame(expressions, context.getBean(ExpressionService.class));
                    assertEquals(1, context.getBeansOfType(SearchEngine.class).size());
                });
    }

    @Test
    void invalidConfigurationFailsBeforeFirstQuery() {
        for (String setting : new String[]{"query-limits.max-depth=0", "query-limits.max-indices=0", "query-limits.default-size=0", "mapping-refresh.interval-seconds=0", "indices[0].zone-id=invalid"})
            context().withPropertyValues("io.github.surezzzzzz.sdk.elasticsearch.search." + setting).run(context -> assertNotNull(context.getStartupFailure()));
    }
}
