package io.github.surezzzzzz.sdk.elasticsearch.persistence.test.cases;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.configuration.SimpleElasticsearchPersistenceAutoConfiguration;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.constant.SimpleElasticsearchPersistenceConstant;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.*;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.result.PersistenceResult;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.engine.PersistenceEngine;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.exception.PersistenceExecutionException;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.executor.PersistenceExecutor;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.executor.PersistenceExecutorRegistry;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.processor.DocumentPreProcessorChain;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.support.PersistencePayloadCodec;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.support.PersistenceTargetResolver;
import io.github.surezzzzzz.sdk.elasticsearch.route.configuration.SimpleElasticsearchRouteProperties;
import io.github.surezzzzzz.sdk.elasticsearch.route.matcher.RoutePatternMatcher;
import io.github.surezzzzzz.sdk.elasticsearch.route.registry.SimpleElasticsearchRouteRegistry;
import io.github.surezzzzzz.sdk.elasticsearch.route.resolver.RouteResolver;
import io.github.surezzzzzz.sdk.elasticsearch.route.resolver.WriteIndexResolver;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 默认装配、扩展让位和真实 Route 解析测试不依赖受控协议 fixture 的固定目标。
 */
@Slf4j
class PersistenceExtensionTest {
    private SimpleElasticsearchRouteProperties routeProperties() {
        SimpleElasticsearchRouteProperties properties = new SimpleElasticsearchRouteProperties();
        properties.setDefaultSource("primary");
        properties.getSources().put("primary", new SimpleElasticsearchRouteProperties.DataSourceConfig());
        properties.getSources().put("secondary", new SimpleElasticsearchRouteProperties.DataSourceConfig());
        properties.setRules(Arrays.asList(rule("sample-*", "primary"), rule("other-*", "secondary")));
        return properties;
    }

    private SimpleElasticsearchRouteProperties.RouteRule rule(String pattern, String source) {
        SimpleElasticsearchRouteProperties.RouteRule rule = new SimpleElasticsearchRouteProperties.RouteRule();
        rule.setPattern(pattern);
        rule.setDatasource(source);
        rule.setType("wildcard");
        return rule;
    }

    private RouteResolver routes(SimpleElasticsearchRouteProperties properties) {
        RouteResolver routes = new RouteResolver(properties, new RoutePatternMatcher());
        routes.init();
        return routes;
    }

    private ApplicationContextRunner context() {
        SimpleElasticsearchRouteProperties properties = routeProperties();
        return new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(SimpleElasticsearchPersistenceAutoConfiguration.class))
                .withPropertyValues("io.github.surezzzzzz.sdk.elasticsearch.persistence.enable=true")
                .withBean(SimpleElasticsearchRouteProperties.class, () -> properties)
                .withBean(RouteResolver.class, () -> routes(properties))
                .withBean(WriteIndexResolver.class, () -> mock(WriteIndexResolver.class))
                .withBean(SimpleElasticsearchRouteRegistry.class, () -> mock(SimpleElasticsearchRouteRegistry.class));
    }

    @Test
    void defaultAssemblyIgnoresHostObjectMapperAndRegistersEveryRequest() {
        ObjectMapper host = mock(ObjectMapper.class);
        context().withBean(ObjectMapper.class, () -> host).run(context -> {
            assertNull(context.getStartupFailure());
            assertEquals(1, context.getBeansOfType(PersistenceEngine.class).size());
            assertEquals(Map.of("amount", 1), context.getBean(PersistencePayloadCodec.class).decode("{\"amount\":1}"));
            PersistenceExecutorRegistry registry = context.getBean(PersistenceExecutorRegistry.class);
            for (PersistenceRequest request : Arrays.asList(IndexRequest.builder().build(), UpdateRequest.builder().build(), DeleteRequest.builder().build(),
                    BulkRequest.builder().build(), UpdateByQueryRequest.builder().build(), DeleteByQueryRequest.builder().build(),
                    io.github.surezzzzzz.sdk.elasticsearch.persistence.model.request.TaskQueryRequest.builder().build()))
                assertNotNull(registry.find(request));
            assertNotNull(context.getBean(SimpleElasticsearchPersistenceConstant.ASYNC_EXECUTOR_BEAN_NAME));
            verifyNoInteractions(host);
        });
    }

    @Test
    void customFacadeCodecChainAndNamedExecutorWinWithoutDuplicates() {
        PersistenceEngine engine = mock(PersistenceEngine.class);
        PersistencePayloadCodec codec = mock(PersistencePayloadCodec.class);
        DocumentPreProcessorChain chain = new DocumentPreProcessorChain(Collections.emptyList());
        Executor executor = Runnable::run;
        context().withBean(PersistenceEngine.class, () -> engine).withBean(PersistencePayloadCodec.class, () -> codec)
                .withBean(DocumentPreProcessorChain.class, () -> chain)
                .withBean(SimpleElasticsearchPersistenceConstant.ASYNC_EXECUTOR_BEAN_NAME, Executor.class, () -> executor)
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertSame(engine, context.getBean(PersistenceEngine.class));
                    assertSame(codec, context.getBean(PersistencePayloadCodec.class));
                    assertSame(chain, context.getBean(DocumentPreProcessorChain.class));
                    assertSame(executor, context.getBean(SimpleElasticsearchPersistenceConstant.ASYNC_EXECUTOR_BEAN_NAME));
                    assertEquals(1, context.getBeansOfType(PersistenceEngine.class).size());
                });
    }

    @Test
    void customRequestExecutorReplacesDefaultAndDuplicateTypesReject() {
        PersistenceExecutor<IndexRequest, PersistenceResult> executor = new PersistenceExecutor<IndexRequest, PersistenceResult>() {
            public Class<IndexRequest> getRequestType() {
                return IndexRequest.class;
            }

            public PersistenceResult execute(IndexRequest request) {
                return PersistenceResult.builder().success(true).build();
            }
        };
        context().withBean("sampleExecutor", PersistenceExecutor.class, () -> executor).run(context -> {
            assertNull(context.getStartupFailure());
            assertSame(executor, context.getBean(PersistenceExecutorRegistry.class).find(IndexRequest.builder().build()));
        });
        assertThrows(PersistenceExecutionException.class, () -> new PersistenceExecutorRegistry(Arrays.asList(executor, executor)));
        PersistenceExecutorRegistry registry = new PersistenceExecutorRegistry(Collections.singletonList(executor));
        assertThrows(PersistenceExecutionException.class, () -> registry.find(null));
        assertThrows(PersistenceExecutionException.class, () -> registry.find(DeleteRequest.builder().build()));
    }

    @Test
    void invalidAsyncPoolFailsAtStartup() {
        context().withPropertyValues("io.github.surezzzzzz.sdk.elasticsearch.persistence.async.core-size=0")
                .run(context -> assertNotNull(context.getStartupFailure()));
    }

    @Test
    void concreteTargetsAndWildcardOwnershipUseRealRouteResolver() {
        SimpleElasticsearchRouteProperties properties = routeProperties();
        WriteIndexResolver writes = mock(WriteIndexResolver.class);
        when(writes.resolveWriteIndex("sample-current")).thenReturn("sample-2024");
        PersistenceTargetResolver targets = new PersistenceTargetResolver(routes(properties), writes, properties);
        assertEquals("sample-2024", targets.writeIndex("sample-current"));
        assertEquals("sample-history", targets.physicalIndex("sample-history"));
        assertEquals("primary", targets.datasource("sample-*"));
        assertEquals("secondary", targets.datasource("other-row"));
        assertThrows(PersistenceExecutionException.class, () -> targets.datasource("sample-row", "other-row"));
        assertThrows(PersistenceExecutionException.class, () -> targets.datasource("sample-?"));
        when(writes.resolveWriteIndex("sample-current")).thenReturn("other-2024");
        assertThrows(PersistenceExecutionException.class, () -> targets.writeIndex("sample-current"));
        properties.getRules().get(1).setPattern("sample-private-*");
        PersistenceTargetResolver overlapping = new PersistenceTargetResolver(routes(properties), writes, properties);
        assertThrows(PersistenceExecutionException.class, () -> overlapping.datasource("sample-*"));
    }
}
