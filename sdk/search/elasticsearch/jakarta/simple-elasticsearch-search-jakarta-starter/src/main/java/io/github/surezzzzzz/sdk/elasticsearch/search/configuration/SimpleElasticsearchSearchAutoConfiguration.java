package io.github.surezzzzzz.sdk.elasticsearch.search.configuration;

import io.github.surezzzzzz.sdk.elasticsearch.route.configuration.SimpleElasticsearchRouteConfiguration;
import io.github.surezzzzzz.sdk.elasticsearch.route.configuration.SimpleElasticsearchRouteProperties;
import io.github.surezzzzzz.sdk.elasticsearch.route.registry.SimpleElasticsearchRouteRegistry;
import io.github.surezzzzzz.sdk.elasticsearch.route.resolver.RouteResolver;
import io.github.surezzzzzz.sdk.elasticsearch.search.SimpleElasticsearchSearchPackage;
import io.github.surezzzzzz.sdk.elasticsearch.search.agg.AggregationDslBuilder;
import io.github.surezzzzzz.sdk.elasticsearch.search.agg.AggregationResponseParser;
import io.github.surezzzzzz.sdk.elasticsearch.search.agg.DefaultAggregationDslBuilder;
import io.github.surezzzzzz.sdk.elasticsearch.search.annotation.SimpleElasticsearchSearchComponent;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.SimpleElasticsearchSearchConstant;
import io.github.surezzzzzz.sdk.elasticsearch.search.endpoint.SearchApiExceptionHandler;
import io.github.surezzzzzz.sdk.elasticsearch.search.endpoint.SearchExpressionApiEndpoint;
import io.github.surezzzzzz.sdk.elasticsearch.search.endpoint.SimpleElasticsearchSearchApiEndpoint;
import io.github.surezzzzzz.sdk.elasticsearch.search.engine.DefaultSearchEngine;
import io.github.surezzzzzz.sdk.elasticsearch.search.engine.SearchEngine;
import io.github.surezzzzzz.sdk.elasticsearch.search.expression.DefaultExpressionService;
import io.github.surezzzzzz.sdk.elasticsearch.search.expression.ExpressionService;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.MappingManager;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.SearchIndexResolver;
import io.github.surezzzzzz.sdk.elasticsearch.search.pagination.AesGcmCursorTokenCodec;
import io.github.surezzzzzz.sdk.elasticsearch.search.pagination.CursorTokenCodec;
import io.github.surezzzzzz.sdk.elasticsearch.search.processor.SensitiveFieldProcessor;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.DefaultSearchDslBuilder;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.DefaultSearchResponseParser;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.SearchDslBuilder;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.SearchResponseParser;
import io.github.surezzzzzz.sdk.elasticsearch.search.support.JacksonSearchPayloadCodec;
import io.github.surezzzzzz.sdk.elasticsearch.search.support.SearchPayloadCodec;
import io.github.surezzzzzz.sdk.elasticsearch.search.support.SearchRestHelper;
import io.github.surezzzzzz.sdk.expression.condition.parser.configuration.ConditionExpressionParserAutoConfiguration;
import io.github.surezzzzzz.sdk.expression.condition.parser.parser.ConditionExpressionParser;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static io.github.surezzzzzz.sdk.elasticsearch.search.constant.SearchProtocolConstant.*;

/**
 * 无 Route 时启用即失败；Web 只在 Servlet 环境且显式启用 API 时装配。
 */
@AutoConfiguration(after = {SimpleElasticsearchRouteConfiguration.class, ConditionExpressionParserAutoConfiguration.class})
@EnableConfigurationProperties(SimpleElasticsearchSearchProperties.class)
@ConditionalOnProperty(prefix = SimpleElasticsearchSearchConstant.CONFIG_PREFIX, name = SimpleElasticsearchSearchConstant.CONFIG_ENABLE, havingValue = VALUE_TRUE)
@ComponentScan(basePackageClasses = SimpleElasticsearchSearchPackage.class, useDefaultFilters = false,
        includeFilters = @ComponentScan.Filter(SimpleElasticsearchSearchComponent.class))
public class SimpleElasticsearchSearchAutoConfiguration {
    /**
     * 创建独占 JSON 编解码器，允许宿主按类型替换。
     */
    @Bean
    @ConditionalOnMissingBean
    public SearchPayloadCodec searchPayloadCodec() {
        return new JacksonSearchPayloadCodec();
    }

    /**
     * 创建仅借用 Route 客户端的 HTTP 适配器。
     */
    @Bean
    @ConditionalOnMissingBean
    public SearchRestHelper searchRestHelper(SimpleElasticsearchRouteRegistry registry, SearchPayloadCodec codec) {
        return new SearchRestHelper(registry, codec);
    }

    /**
     * 创建并校验允许索引与数据源归属。
     */
    @Bean
    @ConditionalOnMissingBean
    public SearchIndexResolver searchIndexResolver(SimpleElasticsearchSearchProperties properties, RouteResolver routes, SimpleElasticsearchRouteProperties routeProperties) {
        return new SearchIndexResolver(properties, routes, routeProperties);
    }

    /**
     * 创建响应字段保护器。
     */
    @Bean
    @ConditionalOnMissingBean
    public SensitiveFieldProcessor sensitiveFieldProcessor() {
        return new SensitiveFieldProcessor();
    }

    /**
     * 创建有界元数据缓存，并按配置执行启动加载。
     */
    @Bean(initMethod = VALUE_INITIALIZE)
    @ConditionalOnMissingBean
    public MappingManager mappingManager(SearchRestHelper rest, SimpleElasticsearchSearchProperties properties, SearchIndexResolver indices, ApplicationEventPublisher publisher) {
        return new MappingManager(rest, properties, indices, publisher);
    }

    /**
     * 创建结构化查询编译器。
     */
    @Bean
    @ConditionalOnMissingBean
    public SearchDslBuilder searchDslBuilder(SimpleElasticsearchSearchProperties properties, SensitiveFieldProcessor sensitive) {
        return new DefaultSearchDslBuilder(properties, sensitive);
    }

    /**
     * 创建结构化聚合编译器。
     */
    @Bean
    @ConditionalOnMissingBean
    public AggregationDslBuilder aggregationDslBuilder(SearchDslBuilder queries, SensitiveFieldProcessor sensitive, SimpleElasticsearchSearchProperties properties) {
        return new DefaultAggregationDslBuilder(queries, sensitive, properties);
    }

    /**
     * 创建精确总数和敏感字段保护的结果解析器。
     */
    @Bean
    @ConditionalOnMissingBean
    public SearchResponseParser searchResponseParser(SensitiveFieldProcessor sensitive, SimpleElasticsearchSearchProperties properties) {
        return new DefaultSearchResponseParser(sensitive, properties);
    }

    /**
     * 创建统一聚合结果解析器。
     */
    @Bean
    @ConditionalOnMissingBean
    public AggregationResponseParser aggregationResponseParser() {
        return new AggregationResponseParser();
    }

    /**
     * 创建使用共享密钥的认证加密游标编解码器。
     */
    @Bean
    @ConditionalOnMissingBean
    public CursorTokenCodec cursorTokenCodec(SimpleElasticsearchSearchProperties properties, SearchPayloadCodec codec) {
        return new AesGcmCursorTokenCodec(properties, codec);
    }

    /**
     * 创建可整体替换的业务查询门面。
     */
    @Bean
    @ConditionalOnMissingBean(SearchEngine.class)
    public SearchEngine searchEngine(SimpleElasticsearchSearchProperties properties, SearchPayloadCodec codec, SearchRestHelper rest,
                                     SearchIndexResolver indices, MappingManager mappings, SensitiveFieldProcessor sensitive, SearchDslBuilder queries,
                                     AggregationDslBuilder aggs, SearchResponseParser parser, AggregationResponseParser aggParser, CursorTokenCodec cursors,
                                     ApplicationEventPublisher publisher) {
        return new DefaultSearchEngine(properties, codec, rest, indices, mappings, sensitive, queries, aggs, parser, aggParser, cursors, publisher);
    }

    /**
     * 表达式服务让位于宿主实现，默认仅适配正式 Condition 制品和既有 Engine。
     */
    @Bean
    @ConditionalOnMissingBean(ExpressionService.class)
    public ExpressionService expressionService(ConditionExpressionParser parser, SimpleElasticsearchSearchProperties properties,
                                               SearchIndexResolver indices, MappingManager mappings, SearchDslBuilder queries,
                                               SearchEngine engine, SearchPayloadCodec codec, CursorTokenCodec cursors) {
        return new DefaultExpressionService(parser, properties, indices, mappings, queries, engine, codec, cursors);
    }

    /**
     * 创建 Spring 托管的定时刷新线程，关闭时释放资源。
     */
    @Bean(destroyMethod = VALUE_SHUTDOWN)
    @ConditionalOnMissingBean(name = MAPPING_REFRESH_EXECUTOR_BEAN_NAME)
    @ConditionalOnProperty(prefix = SimpleElasticsearchSearchConstant.CONFIG_PREFIX, name = VALUE_MAPPING_REFRESH_ENABLED, havingValue = VALUE_TRUE)
    public ScheduledExecutorService esSearchMappingRefreshExecutor(MappingManager mappings, SimpleElasticsearchSearchProperties properties) {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, VALUE_ES_SEARCH_MAPPING_REFRESH);
            thread.setDaemon(true);
            return thread;
        });
        int interval = properties.getMappingRefresh().getIntervalSeconds();
        executor.scheduleWithFixedDelay(mappings::refreshAll, interval, interval, TimeUnit.SECONDS);
        return executor;
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = VALUE_ORG_SPRINGFRAMEWORK_WEB_BIND_ANNOTATION_RESTCONTROLLER)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnProperty(prefix = SimpleElasticsearchSearchConstant.CONFIG_PREFIX, name = VALUE_API_ENABLED, havingValue = VALUE_TRUE)
    public static class WebConfiguration {
        /**
         * 仅在显式启用的 Servlet 环境创建 HTTP 端点。
         */
        @Bean
        @ConditionalOnMissingBean
        public SimpleElasticsearchSearchApiEndpoint simpleElasticsearchSearchApiEndpoint(SearchEngine engine, MappingManager mappings,
                                                                                         SearchIndexResolver indices, SimpleElasticsearchSearchProperties properties, SensitiveFieldProcessor sensitive) {
            return new SimpleElasticsearchSearchApiEndpoint(engine, mappings, indices, properties, sensitive);
        }

        /**
         * 表达式 HTTP 与结构化 HTTP 共用开关和安全边界。
         */
        @Bean
        @ConditionalOnMissingBean
        public SearchExpressionApiEndpoint searchExpressionApiEndpoint(ExpressionService expressions) {
            return new SearchExpressionApiEndpoint(expressions);
        }

        /**
         * 创建仅作用于本组件端点的错误处理器。
         */
        @Bean
        @ConditionalOnMissingBean
        public SearchApiExceptionHandler searchApiExceptionHandler() {
            return new SearchApiExceptionHandler();
        }
    }
}
