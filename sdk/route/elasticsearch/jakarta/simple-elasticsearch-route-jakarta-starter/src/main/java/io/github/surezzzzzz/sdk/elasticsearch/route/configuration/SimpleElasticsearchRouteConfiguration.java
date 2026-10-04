package io.github.surezzzzzz.sdk.elasticsearch.route.configuration;

import io.github.surezzzzzz.sdk.elasticsearch.route.constant.SimpleElasticsearchRouteConstant;
import io.github.surezzzzzz.sdk.elasticsearch.route.extractor.*;
import io.github.surezzzzzz.sdk.elasticsearch.route.matcher.RoutePatternMatcher;
import io.github.surezzzzzz.sdk.elasticsearch.route.proxy.JdkRouteTemplateProxy;
import io.github.surezzzzzz.sdk.elasticsearch.route.proxy.RouteRoutingInterceptor;
import io.github.surezzzzzz.sdk.elasticsearch.route.registry.SimpleElasticsearchRouteRegistry;
import io.github.surezzzzzz.sdk.elasticsearch.route.resolver.DefaultWriteIndexResolver;
import io.github.surezzzzzz.sdk.elasticsearch.route.resolver.RouteResolver;
import io.github.surezzzzzz.sdk.elasticsearch.route.resolver.WriteIndexResolver;
import io.github.surezzzzzz.sdk.elasticsearch.route.support.SpELHelper;
import io.github.surezzzzzz.sdk.elasticsearch.route.validator.SimpleElasticsearchRouteValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.convert.ElasticsearchConverter;

import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * Simple Elasticsearch Route Jakarta 自动配置。
 *
 * <p>本配置先于 Spring Boot 默认的 Elasticsearch template 注册代理；已有业务方
 * ElasticsearchOperations 时整套 Route 让位，不创建连接、线程池或版本探测任务。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@RequiredArgsConstructor
@AutoConfiguration(beforeName = "org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchDataAutoConfiguration")
@ConditionalOnClass(name = {
        "org.springframework.data.elasticsearch.core.ElasticsearchOperations",
        "org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate",
        "org.springframework.data.elasticsearch.core.convert.ElasticsearchConverter",
        "org.elasticsearch.client.RestClient",
        "co.elastic.clients.elasticsearch.ElasticsearchClient"
})
@EnableConfigurationProperties(SimpleElasticsearchRouteProperties.class)
@ConditionalOnProperty(prefix = SimpleElasticsearchRouteConstant.CONFIG_PREFIX, name = "enable", havingValue = "true")
@ConditionalOnMissingBean(ElasticsearchOperations.class)
public class SimpleElasticsearchRouteConfiguration implements DisposableBean {

    private final SimpleElasticsearchRouteProperties properties;

    /**
     * 异步写线程池按数据源隔离，关闭时由当前自动配置统一回收。
     */
    private final Map<String, ExecutorService> asyncWriteExecutorMap = new ConcurrentHashMap<>();

    /**
     * 显式注册内部组件，使类级让位条件在自动配置解析时生效。
     */
    @Bean
    @ConditionalOnMissingBean(RoutePatternMatcher.class)
    public RoutePatternMatcher routePatternMatcher() {
        return new RoutePatternMatcher();
    }

    @Bean
    @ConditionalOnMissingBean(RouteResolver.class)
    public RouteResolver routeResolver(RoutePatternMatcher patternMatcher) {
        return new RouteResolver(properties, patternMatcher);
    }

    @Bean
    @ConditionalOnMissingBean(IndexCoordinatesExtractor.class)
    public IndexCoordinatesExtractor indexCoordinatesExtractor() {
        return new IndexCoordinatesExtractor();
    }

    @Bean
    @ConditionalOnMissingBean(IndexQueryExtractor.class)
    public IndexQueryExtractor indexQueryExtractor() {
        return new IndexQueryExtractor();
    }

    @Bean
    @ConditionalOnMissingBean(EntityObjectExtractor.class)
    public EntityObjectExtractor entityObjectExtractor() {
        return new EntityObjectExtractor();
    }

    @Bean
    @ConditionalOnMissingBean(ClassTypeExtractor.class)
    public ClassTypeExtractor classTypeExtractor() {
        return new ClassTypeExtractor();
    }

    @Bean
    @ConditionalOnMissingBean(SpELHelper.class)
    public SpELHelper spELHelper() {
        return new SpELHelper();
    }

    /**
     * 显式创建校验器，避免 component scan 的不确定初始化顺序。所有有副作用的 Bean 都将它
     * 作为依赖，从而保证配置校验先于 client、探测线程和 executor 创建。
     */
    @Bean
    @ConditionalOnMissingBean(SimpleElasticsearchRouteValidator.class)
    public SimpleElasticsearchRouteValidator simpleElasticsearchRouteValidator() {
        return new SimpleElasticsearchRouteValidator(properties);
    }

    /**
     * 创建异步写线程池。队满时记录并丢弃任务，保持 async-write 对业务线程立即返回的契约；
     * 仅适用于调用方允许少量丢失的异步写场景。
     */
    @Bean(name = SimpleElasticsearchRouteConstant.BEAN_ASYNC_WRITE_EXECUTOR_MAP)
    public Map<String, ExecutorService> simpleElasticsearchRouteAsyncWriteExecutorMap(
            SimpleElasticsearchRouteValidator validator) {
        properties.getSources().forEach((datasourceKey, config) -> {
            int poolSize = config.getAsyncWriteThreadPoolSize();
            ExecutorService executor = new ThreadPoolExecutor(
                    poolSize,
                    poolSize * SimpleElasticsearchRouteConstant.ASYNC_WRITE_MAX_POOL_MULTIPLIER,
                    SimpleElasticsearchRouteConstant.ASYNC_WRITE_KEEP_ALIVE_SECONDS,
                    TimeUnit.SECONDS,
                    new LinkedBlockingQueue<>(SimpleElasticsearchRouteConstant.ASYNC_WRITE_QUEUE_CAPACITY),
                    runnable -> {
                        Thread thread = new Thread(runnable);
                        thread.setName(SimpleElasticsearchRouteConstant.ASYNC_WRITE_THREAD_NAME_PREFIX + datasourceKey);
                        thread.setDaemon(true);
                        return thread;
                    },
                    (runnable, rejectedExecutor) -> log.warn(
                            "异步写队列已满，datasource=[{}]，任务已丢弃", datasourceKey));
            asyncWriteExecutorMap.put(datasourceKey, executor);
        });
        return asyncWriteExecutorMap;
    }

    /**
     * 手工构造每个数据源的 ELC template，复用 Spring Boot 已建立的 converter，并将
     * ApplicationContext 传入 template 以发现 EntityCallbacks。
     */
    @Bean
    @ConditionalOnMissingBean(SimpleElasticsearchRouteRegistry.class)
    public SimpleElasticsearchRouteRegistry simpleElasticsearchRouteRegistry(
            SimpleElasticsearchRouteValidator validator,
            RouteResolver routeResolver,
            ElasticsearchConverter elasticsearchConverter,
            ApplicationContext applicationContext) {
        return new SimpleElasticsearchRouteRegistry(
                properties, routeResolver, elasticsearchConverter, applicationContext);
    }

    @Bean
    @ConditionalOnMissingBean(WriteIndexResolver.class)
    public WriteIndexResolver writeIndexResolver(RouteResolver routeResolver) {
        ZoneId zoneId = resolveGlobalZoneId(properties.getEffectiveWriteIndexZoneId(),
                properties.getEffectiveWriteIndexZoneIdConfigName());
        return new DefaultWriteIndexResolver(routeResolver, zoneId);
    }

    /**
     * 注册 Spring Data 5 的接口代理。以接口而非具体模板类型注入，避免调用方依赖 ELC
     * 的内部实现类，也使每次操作均能先完成索引路由。
     */
    @Bean(name = SimpleElasticsearchRouteConstant.BEAN_ELASTICSEARCH_TEMPLATE)
    @Primary
    public ElasticsearchOperations elasticsearchOperations(
            SimpleElasticsearchRouteRegistry routeRegistry,
            RouteResolver routeResolver,
            List<IndexNameExtractor> indexNameExtractors,
            WriteIndexResolver writeIndexResolver,
            @Qualifier(SimpleElasticsearchRouteConstant.BEAN_ASYNC_WRITE_EXECUTOR_MAP)
            Map<String, ExecutorService> executors) {
        Map<String, ElasticsearchOperations> templates = routeRegistry.getTemplates();
        ElasticsearchOperations defaultTemplate = routeRegistry.getTemplate(properties.getDefaultSource());
        RouteRoutingInterceptor interceptor = new RouteRoutingInterceptor(
                templates, routeResolver, indexNameExtractors, executors, writeIndexResolver);
        return JdkRouteTemplateProxy.createProxy(interceptor, defaultTemplate);
    }

    @Override
    public void destroy() {
        asyncWriteExecutorMap.forEach((datasourceKey, executor) -> {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(
                        SimpleElasticsearchRouteConstant.ASYNC_WRITE_SHUTDOWN_AWAIT_SECONDS,
                        TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                    log.warn("异步写线程池 [{}] 未在规定时间内关闭，已强制中断", datasourceKey);
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        });
    }

    private ZoneId resolveGlobalZoneId(String configuredZoneId, String configName) {
        if (configuredZoneId == null || configuredZoneId.trim().isEmpty()) {
            return ZoneId.systemDefault();
        }
        try {
            return ZoneId.of(configuredZoneId);
        } catch (Exception e) {
            log.warn("全局 [{}]=[{}] 非法，降级为 JVM 默认时区", configName, configuredZoneId);
            return ZoneId.systemDefault();
        }
    }
}
