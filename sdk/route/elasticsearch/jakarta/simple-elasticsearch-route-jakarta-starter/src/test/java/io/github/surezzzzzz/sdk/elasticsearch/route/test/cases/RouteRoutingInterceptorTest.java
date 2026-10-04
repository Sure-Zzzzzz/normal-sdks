package io.github.surezzzzzz.sdk.elasticsearch.route.test.cases;

import io.github.surezzzzzz.sdk.elasticsearch.route.configuration.SimpleElasticsearchRouteProperties;
import io.github.surezzzzzz.sdk.elasticsearch.route.exception.RouteException;
import io.github.surezzzzzz.sdk.elasticsearch.route.extractor.*;
import io.github.surezzzzzz.sdk.elasticsearch.route.matcher.RoutePatternMatcher;
import io.github.surezzzzzz.sdk.elasticsearch.route.proxy.JdkRouteTemplateProxy;
import io.github.surezzzzzz.sdk.elasticsearch.route.proxy.RouteRoutingInterceptor;
import io.github.surezzzzzz.sdk.elasticsearch.route.resolver.DefaultWriteIndexResolver;
import io.github.surezzzzzz.sdk.elasticsearch.route.resolver.RouteResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.RefreshPolicy;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.SqlQuery;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 路由代理离线测试。
 *
 * <p>底层模板使用记录调用的 JDK proxy，不连接 Elasticsearch；断言真实 Spring Data
 * 接口重载是否被安全改写，防止分片或批量请求回退到默认数据源。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
public class RouteRoutingInterceptorTest {

    private final List<Invocation> invocations = new ArrayList<>();

    private ElasticsearchOperations operations;
    private RuntimeException saveFailure;

    @BeforeEach
    public void setUp() {
        SimpleElasticsearchRouteProperties properties = createProperties();
        RouteResolver resolver = new RouteResolver(properties, new RoutePatternMatcher());
        resolver.init();

        ElasticsearchOperations primary = createRecordingTemplate("primary");
        ElasticsearchOperations secondary = createRecordingTemplate("secondary");
        Map<String, ElasticsearchOperations> templates = new HashMap<>();
        templates.put("primary", primary);
        templates.put("secondary", secondary);

        Map<String, ExecutorService> executors = new HashMap<>();
        executors.put("primary", new RejectingExecutorService());
        executors.put("secondary", new RejectingExecutorService());
        List<IndexNameExtractor> extractors = Arrays.asList(
                new IndexCoordinatesExtractor(),
                new IndexQueryExtractor(),
                new EntityObjectExtractor(),
                new ClassTypeExtractor());
        RouteRoutingInterceptor interceptor = new RouteRoutingInterceptor(
                templates, resolver, extractors, executors,
                new DefaultWriteIndexResolver(resolver, ZoneId.of("UTC")));
        operations = JdkRouteTemplateProxy.createProxy(interceptor, primary);
    }

    @Test
    public void crossDatasourceBatchMustFailBeforeTemplateInvocation() {
        log.info("验证跨数据源批量写在网络调用前失败");

        assertThrows(RouteException.class,
                () -> operations.save(Arrays.asList(new PrimaryDocument(), new SecondaryDocument())));

        assertFalse(hasOperation("save"), "跨数据源批量写不能调用任一底层模板");
    }

    @Test
    public void deleteByIdMustReplaceTrailingClassWithIndexCoordinates() {
        log.info("验证 delete(id, Class) 改写为 delete(id, IndexCoordinates)");

        operations.delete("document-id", SecondaryWriteDocument.class);

        Invocation invocation = lastInvocation("delete");
        assertEquals("secondary", invocation.getDatasource());
        assertArrayEquals(new Class<?>[]{String.class, IndexCoordinates.class},
                invocation.getMethod().getParameterTypes());
        assertArrayEquals(new String[]{"secondary-write-shard"},
                ((IndexCoordinates) invocation.getArgs()[1]).getIndexNames());
    }

    @Test
    public void indexOperationsMustKeepEntityBindingOrRejectShardRewrite() {
        log.info("验证索引管理保留实体映射绑定，日期分片不能静默丢失绑定");

        operations.indexOps(PrimaryDocument.class);
        Invocation ordinary = lastInvocation("indexOps");
        assertArrayEquals(new Class<?>[]{Class.class}, ordinary.getMethod().getParameterTypes());
        assertEquals(PrimaryDocument.class, ordinary.getArgs()[0]);

        int callsBeforeShard = invocations.size();
        assertThrows(RouteException.class, () -> operations.indexOps(SecondaryWriteDocument.class));
        assertEquals(callsBeforeShard, invocations.size(), "不安全的分片索引管理不能调用底层模板");
    }

    @Test
    public void openPointInTimeMustRewriteLeadingIndexCoordinatesAndCloseOnSameDatasource() {
        log.info("验证 PIT 打开和关闭保持同一数据源");

        String pointInTimeId = operations.openPointInTime(IndexCoordinates.of("pit-log"), Duration.ofMinutes(1));
        Invocation open = lastInvocation("openPointInTime");
        assertEquals("secondary", open.getDatasource());
        assertArrayEquals(new String[]{"pit-log-*"},
                ((IndexCoordinates) open.getArgs()[0]).getIndexNames());

        operations.closePointInTime(pointInTimeId);
        Invocation close = lastInvocation("closePointInTime");
        assertEquals("secondary", close.getDatasource());
        assertThrows(RouteException.class, () -> operations.closePointInTime(pointInTimeId),
                "成功关闭后不能再把同一 PIT 路由到默认数据源");
    }

    @Test
    public void fluentConfigurationMustBeReplayedOnRoutedTemplate() {
        log.info("验证 fluent 配置不会丢失 secondary 路由");

        operations.withRefreshPolicy(RefreshPolicy.IMMEDIATE).save(new SecondaryDocument());

        assertEquals("primary", invocations.get(0).getDatasource());
        assertEquals("withRefreshPolicy", invocations.get(0).getMethod().getName());
        assertEquals("secondary", invocations.get(1).getDatasource());
        assertEquals("withRefreshPolicy", invocations.get(1).getMethod().getName());
        Invocation save = lastInvocation("save");
        assertEquals("secondary", save.getDatasource());
        assertArrayEquals(new Class<?>[]{Object.class}, save.getMethod().getParameterTypes());
        assertEquals(SecondaryDocument.class, save.getArgs()[0].getClass());
    }

    @Test
    public void rejectedAsyncWriteMustReturnImmediatelyWithoutCallingTemplate() {
        log.info("验证异步写被拒绝时不回退到业务线程");

        Object result = operations.save(new AsyncDocument());

        assertNull(result, "异步写被拒绝时仍应立即返回");
        assertFalse(hasOperation("save"), "被拒绝的异步写不能在调用线程执行");
    }

    @Test
    public void sqlAndScriptOperationsMustFailClosedBeforeTemplateInvocation() {
        log.info("验证没有可路由索引的 SQL 与脚本操作不会落到默认数据源");

        assertThrows(RouteException.class,
                () -> operations.search(SqlQuery.builder("select * from any_index").build()));
        assertThrows(RouteException.class, () -> operations.deleteScript("script-id"));

        assertFalse(hasOperation("search"), "SQL 操作不能调用默认数据源模板");
        assertFalse(hasOperation("deleteScript"), "脚本操作不能调用默认数据源模板");
    }

    @Test
    public void templateRuntimeExceptionMustNotBeWrappedByReflection() {
        log.info("验证底层模板运行时异常保持原始类型");

        saveFailure = new IllegalStateException("template-save-failure");
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> operations.save(new PrimaryDocument()));

        assertSame(saveFailure, exception, "路由代理不能泄漏 InvocationTargetException 包装");
    }

    private SimpleElasticsearchRouteProperties createProperties() {
        SimpleElasticsearchRouteProperties properties = new SimpleElasticsearchRouteProperties();
        properties.setEnable(true);
        properties.setDefaultSource("primary");
        Map<String, SimpleElasticsearchRouteProperties.DataSourceConfig> sources = new HashMap<>();
        sources.put("primary", new SimpleElasticsearchRouteProperties.DataSourceConfig());
        sources.put("secondary", new SimpleElasticsearchRouteProperties.DataSourceConfig());
        properties.setSources(sources);
        properties.setRules(Arrays.asList(
                rule("primary-document", "primary", null, null, false),
                rule("secondary-document", "secondary", null, null, false),
                rule("secondary-write", "secondary", "secondary-write-shard", null, false),
                rule("pit-log", "secondary", null, "pit-log-*", false),
                rule("async-document", "secondary", null, null, true)));
        return properties;
    }

    private SimpleElasticsearchRouteProperties.RouteRule rule(String pattern, String datasource,
                                                              String writeIndexTemplate, String readIndexPattern,
                                                              boolean asyncWrite) {
        SimpleElasticsearchRouteProperties.RouteRule rule = new SimpleElasticsearchRouteProperties.RouteRule();
        rule.setPattern(pattern);
        rule.setDatasource(datasource);
        rule.setPriority(1);
        rule.setWriteIndexTemplate(writeIndexTemplate);
        rule.setReadIndexPattern(readIndexPattern);
        rule.setAsyncWrite(asyncWrite);
        return rule;
    }

    private ElasticsearchOperations createRecordingTemplate(String datasource) {
        return (ElasticsearchOperations) Proxy.newProxyInstance(
                ElasticsearchOperations.class.getClassLoader(),
                new Class<?>[]{ElasticsearchOperations.class},
                (proxy, method, args) -> {
                    invocations.add(new Invocation(datasource, method,
                            args == null ? new Object[0] : Arrays.copyOf(args, args.length)));
                    if ("save".equals(method.getName()) && saveFailure != null) {
                        throw saveFailure;
                    }
                    if (ElasticsearchOperations.class.isAssignableFrom(method.getReturnType())) {
                        return proxy;
                    }
                    if ("openPointInTime".equals(method.getName())) {
                        return datasource + "-pit";
                    }
                    if ("closePointInTime".equals(method.getName())) {
                        return Boolean.TRUE;
                    }
                    if (method.getReturnType() == boolean.class) {
                        return false;
                    }
                    if (method.getReturnType() == long.class) {
                        return 0L;
                    }
                    if (method.getReturnType() == int.class) {
                        return 0;
                    }
                    if (Iterable.class.isAssignableFrom(method.getReturnType())) {
                        return Collections.emptyList();
                    }
                    return null;
                });
    }

    private boolean hasOperation(String methodName) {
        return invocations.stream().anyMatch(invocation -> methodName.equals(invocation.getMethod().getName()));
    }

    private Invocation lastInvocation(String methodName) {
        for (int index = invocations.size() - 1; index >= 0; index--) {
            Invocation invocation = invocations.get(index);
            if (methodName.equals(invocation.getMethod().getName())) {
                return invocation;
            }
        }
        throw new AssertionError("未记录方法调用: " + methodName);
    }

    @lombok.Getter
    @RequiredArgsConstructor
    private static class Invocation {
        private final String datasource;
        private final Method method;
        private final Object[] args;
    }

    private static class RejectingExecutorService extends AbstractExecutorService {

        @Override
        public void shutdown() {
        }

        @Override
        public List<Runnable> shutdownNow() {
            return Collections.emptyList();
        }

        @Override
        public boolean isShutdown() {
            return false;
        }

        @Override
        public boolean isTerminated() {
            return false;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return false;
        }

        @Override
        public void execute(Runnable command) {
            throw new RejectedExecutionException("test rejection");
        }
    }

    @Document(indexName = "primary-document")
    private static class PrimaryDocument {
    }

    @Document(indexName = "secondary-document")
    private static class SecondaryDocument {
    }

    @Document(indexName = "secondary-write")
    private static class SecondaryWriteDocument {
    }

    @Document(indexName = "async-document")
    private static class AsyncDocument {
    }
}
