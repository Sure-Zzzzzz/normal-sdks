package io.github.surezzzzzz.sdk.elasticsearch.route.configuration;

import io.github.surezzzzzz.sdk.elasticsearch.route.SimpleElasticsearchRoutePackage;
import io.github.surezzzzzz.sdk.elasticsearch.route.annotation.SimpleElasticsearchRouteComponent;
import io.github.surezzzzzz.sdk.elasticsearch.route.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.elasticsearch.route.constant.ProxyType;
import io.github.surezzzzzz.sdk.elasticsearch.route.constant.SimpleElasticsearchRouteConstant;
import io.github.surezzzzzz.sdk.elasticsearch.route.extractor.IndexNameExtractor;
import io.github.surezzzzzz.sdk.elasticsearch.route.proxy.JdkRouteTemplateProxy;
import io.github.surezzzzzz.sdk.elasticsearch.route.proxy.RouteRoutingInterceptor;
import io.github.surezzzzzz.sdk.elasticsearch.route.proxy.RouteTemplateProxy;
import io.github.surezzzzzz.sdk.elasticsearch.route.registry.SimpleElasticsearchRouteRegistry;
import io.github.surezzzzzz.sdk.elasticsearch.route.resolver.DefaultWriteIndexResolver;
import io.github.surezzzzzz.sdk.elasticsearch.route.resolver.RouteResolver;
import io.github.surezzzzzz.sdk.elasticsearch.route.resolver.WriteIndexResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;

import javax.annotation.PostConstruct;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Simple Elasticsearch Route Auto Configuration
 *
 * @author surezzzzzz
 */
@Slf4j
@RequiredArgsConstructor
@Configuration
@EnableConfigurationProperties(SimpleElasticsearchRouteProperties.class)
@ComponentScan(
        basePackageClasses = SimpleElasticsearchRoutePackage.class,
        includeFilters = @ComponentScan.Filter(SimpleElasticsearchRouteComponent.class)
)
@ConditionalOnProperty(prefix = SimpleElasticsearchRouteConstant.CONFIG_PREFIX, name = "enable", havingValue = "true")
public class SimpleElasticsearchRouteConfiguration implements DisposableBean {

    /**
     * 判断当前依赖中是否存在 Elasticsearch RestHighLevelClient 7.x 标记类。
     */
    private static final boolean ES_CLIENT_7X_AVAILABLE;

    static {
        boolean available = false;
        try {
            Class.forName(SimpleElasticsearchRouteConstant.ES_CLIENT_7X_MARKER_CLASS);
            available = true;
        } catch (ClassNotFoundException ignored) {
            // 旧版 RestHighLevelClient 依赖中没有该标记类。
        }
        ES_CLIENT_7X_AVAILABLE = available;
    }

    private static String getSpringBootVersion() {
        try {
            Class<?> clazz = Class.forName(SimpleElasticsearchRouteConstant.CLASS_SPRING_BOOT_VERSION);
            java.lang.reflect.Method m = clazz.getMethod(SimpleElasticsearchRouteConstant.METHOD_VERSION);
            return (String) m.invoke(null);
        } catch (Exception e) {
            return SimpleElasticsearchRouteConstant.VERSION_UNKNOWN;
        }
    }

    private final SimpleElasticsearchRouteProperties properties;
    private final SimpleElasticsearchRouteRegistry routeRegistry;

    /**
     * 异步写线程池（按 datasource key 隔离）
     */
    private final Map<String, ExecutorService> asyncWriteExecutorMap = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        initAsyncWriteExecutors();
    }

    /**
     * 初始化异步写线程池
     */
    private void initAsyncWriteExecutors() {
        for (Map.Entry<String, SimpleElasticsearchRouteProperties.DataSourceConfig> entry
                : properties.getSources().entrySet()) {
            String dsKey = entry.getKey();
            int poolSize = entry.getValue().getAsyncWriteThreadPoolSize();
            int maxPoolSize = poolSize * SimpleElasticsearchRouteConstant.ASYNC_WRITE_MAX_POOL_MULTIPLIER;
            ExecutorService executor = new ThreadPoolExecutor(
                    poolSize,
                    maxPoolSize,
                    SimpleElasticsearchRouteConstant.ASYNC_WRITE_KEEP_ALIVE_SECONDS,
                    TimeUnit.SECONDS,
                    new java.util.concurrent.LinkedBlockingQueue<>(
                            SimpleElasticsearchRouteConstant.ASYNC_WRITE_QUEUE_CAPACITY),
                    r -> {
                        Thread t = new Thread(r);
                        t.setName(SimpleElasticsearchRouteConstant.ASYNC_WRITE_THREAD_NAME_PREFIX + dsKey);
                        t.setDaemon(true);
                        return t;
                    },
                    new ThreadPoolExecutor.CallerRunsPolicy()
            );
            asyncWriteExecutorMap.put(dsKey, executor);
            log.info("异步写线程池初始化完成，datasource=[{}]，poolSize=[{}]，maxPoolSize=[{}]",
                    dsKey, poolSize, maxPoolSize);
        }
    }

    @Override
    public void destroy() {
        log.info("开始关闭异步写线程池，共 {} 个", asyncWriteExecutorMap.size());
        for (Map.Entry<String, ExecutorService> entry : asyncWriteExecutorMap.entrySet()) {
            String dsKey = entry.getKey();
            ExecutorService executor = entry.getValue();
            executor.shutdown();
            try {
                if (!executor.awaitTermination(
                        SimpleElasticsearchRouteConstant.ASYNC_WRITE_SHUTDOWN_AWAIT_SECONDS,
                        TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                    log.warn("异步写线程池 [{}] 在 {}s 内未关闭完成，已强制中断",
                            dsKey, SimpleElasticsearchRouteConstant.ASYNC_WRITE_SHUTDOWN_AWAIT_SECONDS);
                } else {
                    log.info("异步写线程池 [{}] 已优雅关闭", dsKey);
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
                log.warn("异步写线程池 [{}] 关闭被中断", dsKey);
            }
        }
    }

    /**
     * 创建路由代理 ElasticsearchRestTemplate
     *
     * <p>根据 proxyType 配置选择 CGLIB 或 JDK 代理：
     * <ul>
     *   <li>CGLIB：方法覆盖最全，支持所有继承方法</li>
     *   <li>JDK：依赖 ElasticsearchOperations 接口，部分非接口方法走透传</li>
     *   <li>AUTO：优先 CGLIB，失败后回退到 JDK</li>
     * </ul>
     */
    @Bean
    @ConditionalOnMissingBean(WriteIndexResolver.class)
    public WriteIndexResolver writeIndexResolver(RouteResolver routeResolver) {
        ZoneId globalZoneId = resolveGlobalZoneId(properties.getEffectiveWriteIndexZoneId(),
                properties.getEffectiveWriteIndexZoneIdConfigName());
        log.info("初始化 WriteIndexResolver，globalWriteIndexZoneId=[{}]", globalZoneId);
        return new DefaultWriteIndexResolver(routeResolver, globalZoneId);
    }

    @Bean
    @Primary
    @ConditionalOnMissingBean(name = "elasticsearchRestTemplate")
    public ElasticsearchRestTemplate elasticsearchRestTemplate(
            RouteResolver routeResolver,
            List<IndexNameExtractor> indexNameExtractors,
            WriteIndexResolver writeIndexResolver) {

        Map<String, ElasticsearchRestTemplate> templatesMap = routeRegistry.getTemplates();
        String defaultKey = properties.getDefaultSource();

        ElasticsearchRestTemplate defaultTemplate = routeRegistry.getTemplate(defaultKey);
        RestHighLevelClient defaultClient = routeRegistry.getHighLevelClient(defaultKey);

        if (!ES_CLIENT_7X_AVAILABLE) {
            log.info("Spring Boot [{}] 当前使用 Spring Data Elasticsearch 3.x 风格 API，路由代理将对 save/get/index 使用 HTTP 兼容调用",
                    getSpringBootVersion());
        }

        log.info("创建路由代理，当前 datasource 数：{}，默认 datasource：[{}]", templatesMap.size(), defaultKey);
        log.info("配置的路由规则数：{}，proxyType：[{}]",
                properties.getRules().size(), getProxyType());
        log.info("加载的 IndexNameExtractor 数：{}",
                indexNameExtractors.size());

        // 构建路由拦截器
        RouteRoutingInterceptor routingInterceptor = new RouteRoutingInterceptor(
                templatesMap, defaultTemplate, routeResolver,
                indexNameExtractors, asyncWriteExecutorMap, writeIndexResolver);

        ProxyType proxyType = getProxyType();
        return createProxy(proxyType, routingInterceptor, defaultTemplate, defaultClient);
    }

    /**
     * 根据 proxyType 创建代理
     */
    private ElasticsearchRestTemplate createProxy(
            ProxyType proxyType,
            RouteRoutingInterceptor routingInterceptor,
            ElasticsearchRestTemplate defaultTemplate,
            RestHighLevelClient defaultClient) {

        // AUTO：先 CGLIB，失败后 JDK
        if (proxyType == ProxyType.AUTO) {
            return tryCglibOrJdk(routingInterceptor, defaultTemplate, defaultClient);
        }

        // 强制 CGLIB
        if (proxyType == ProxyType.CGLIB) {
            return createCglibProxy(routingInterceptor, defaultClient);
        }

        // 强制 JDK
        return createJdkProxy(routingInterceptor, defaultTemplate);
    }

    /**
     * AUTO：尝试 CGLIB，失败后回退到 JDK
     */
    private ElasticsearchRestTemplate tryCglibOrJdk(
            RouteRoutingInterceptor routingInterceptor,
            ElasticsearchRestTemplate defaultTemplate,
            RestHighLevelClient defaultClient) {

        try {
            return createCglibProxy(routingInterceptor, defaultClient);
        } catch (Exception e) {
            log.warn("CGLIB 代理创建失败，自动回退到 JDK 代理。原因：[{}]", e.getMessage());
            try {
                return createJdkProxy(routingInterceptor, defaultTemplate);
            } catch (Exception ex) {
                throw new BeanCreationException("elasticsearchRestTemplate",
                        ErrorMessage.PROXY_CREATION_FAILED, ex);
            }
        }
    }

    /**
     * 创建 CGLIB 代理
     */
    private ElasticsearchRestTemplate createCglibProxy(
            RouteRoutingInterceptor routingInterceptor,
            RestHighLevelClient defaultClient) {

        return RouteTemplateProxy.createProxy(routingInterceptor, defaultClient);
    }

    /**
     * 创建 JDK 代理
     */
    private ElasticsearchRestTemplate createJdkProxy(
            RouteRoutingInterceptor routingInterceptor,
            ElasticsearchRestTemplate defaultTemplate) {

        return JdkRouteTemplateProxy.createProxy(routingInterceptor, defaultTemplate);
    }

    /**
     * 解析全局写索引时区，非法值 WARN 降级到 JVM 默认。
     */
    private ZoneId resolveGlobalZoneId(String zoneIdStr, String configName) {
        if (zoneIdStr == null || zoneIdStr.trim().isEmpty()) {
            return ZoneId.systemDefault();
        }
        try {
            return ZoneId.of(zoneIdStr);
        } catch (Exception e) {
            log.warn("全局 [{}]=[{}] 非法，降级为 JVM 默认时区，错误=[{}]",
                    configName, zoneIdStr, e.getMessage());
            return ZoneId.systemDefault();
        }
    }

    /**
     * 获取 proxyType 配置
     */
    private ProxyType getProxyType() {
        String configured = properties.getProxyType();
        ProxyType type = ProxyType.fromCode(configured);
        return type != null ? type : ProxyType.AUTO;
    }
}
