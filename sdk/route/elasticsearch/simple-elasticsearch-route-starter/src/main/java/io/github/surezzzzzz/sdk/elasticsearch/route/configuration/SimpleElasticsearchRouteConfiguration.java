package io.github.surezzzzzz.sdk.elasticsearch.route.configuration;

import io.github.surezzzzzz.sdk.elasticsearch.route.SimpleElasticsearchRoutePackage;
import io.github.surezzzzzz.sdk.elasticsearch.route.annotation.SimpleElasticsearchRouteComponent;
import io.github.surezzzzzz.sdk.elasticsearch.route.support.RouteResolver;
import io.github.surezzzzzz.sdk.elasticsearch.route.support.RouteTemplateProxy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.ssl.SSLContextBuilder;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import javax.net.ssl.SSLContext;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Simple Elasticsearch Route Auto Configuration
 *
 * @author surezzzzzz
 */
@Slf4j
@Configuration
@ComponentScan(
        basePackageClasses = SimpleElasticsearchRoutePackage.class,
        includeFilters = @ComponentScan.Filter(SimpleElasticsearchRouteComponent.class)
)
@ConditionalOnProperty(prefix = "io.github.surezzzzzz.sdk.elasticsearch.route", name = "enable", havingValue = "true")
@RequiredArgsConstructor
public class SimpleElasticsearchRouteConfiguration {

    private final SimpleElasticsearchRouteProperties properties;

    /**
     * 存储所有数据源的 Template（不作为 Bean）
     */
    private final Map<String, ElasticsearchRestTemplate> templatesMap = new HashMap<>();

    @PostConstruct
    public void init() {
        log.info("Initializing Elasticsearch route configuration...");
        createAllTemplates();
    }

    /**
     * 创建所有数据源的 ElasticsearchRestTemplate
     */
    private void createAllTemplates() {
        properties.getSources().forEach((key, config) -> {
            try {
                // ========== 解析 Hosts ==========
                HttpHost[] hosts = parseHosts(config.getHosts(), config.isUseSsl());

                // ========== 创建 RestClientBuilder ==========
                RestClientBuilder restClientBuilder = RestClient.builder(hosts);

                // ========== HTTP 客户端配置 ==========
                restClientBuilder.setHttpClientConfigCallback(httpClientBuilder -> {

                    // Keep-Alive 策略
                    if (config.getKeepAliveStrategy() != null) {
                        httpClientBuilder.setKeepAliveStrategy(
                                (response, context) -> TimeUnit.SECONDS.toMillis(config.getKeepAliveStrategy())
                        );
                        log.debug("Datasource [{}] keep-alive: {}s", key, config.getKeepAliveStrategy());
                    }

                    // 最大连接数
                    if (config.getMaxConnTotal() != null) {
                        httpClientBuilder.setMaxConnTotal(config.getMaxConnTotal());
                        log.debug("Datasource [{}] max-conn-total: {}", key, config.getMaxConnTotal());
                    }

                    // 每个路由的最大连接数
                    if (config.getMaxConnPerRoute() != null) {
                        httpClientBuilder.setMaxConnPerRoute(config.getMaxConnPerRoute());
                        log.debug("Datasource [{}] max-conn-per-route: {}", key, config.getMaxConnPerRoute());
                    }

                    // 连接重用策略
                    if (config.isEnableConnectionReuse()) {
                        httpClientBuilder.setConnectionReuseStrategy(
                                DefaultConnectionReuseStrategy.INSTANCE
                        );
                        log.debug("Datasource [{}] connection reuse enabled", key);
                    }

                    // ========== 认证配置 ==========
                    if (StringUtils.hasText(config.getUsername()) &&
                            StringUtils.hasText(config.getPassword())) {
                        BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
                        credentialsProvider.setCredentials(
                                AuthScope.ANY,
                                new UsernamePasswordCredentials(config.getUsername(), config.getPassword())
                        );
                        httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
                        log.debug("Datasource [{}] basic auth configured", key);
                    }

                    // ========== SSL/TLS 配置 ==========
                    if (config.isUseSsl()) {
                        try {
                            SSLContext sslContext;
                            if (config.isSkipSslValidation()) {
                                log.warn("⚠️  Datasource [{}] is configured to skip SSL validation - DO NOT use in production!", key);
                                sslContext = SSLContextBuilder.create()
                                        .loadTrustMaterial((chain, authType) -> true)
                                        .build();
                                httpClientBuilder.setSSLContext(sslContext);
                                httpClientBuilder.setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE);
                            } else {
                                sslContext = SSLContext.getInstance("TLS");
                                sslContext.init(null, null, null);
                                httpClientBuilder.setSSLContext(sslContext);
                            }
                            log.debug("Datasource [{}] SSL enabled", key);
                        } catch (Exception e) {
                            throw new RuntimeException("Failed to configure SSL for datasource: " + key, e);
                        }
                    }

                    // ========== 代理配置 ==========
                    if (StringUtils.hasText(config.getProxyHost()) && config.getProxyPort() != null) {
                        HttpHost proxy = new HttpHost(config.getProxyHost(), config.getProxyPort());
                        httpClientBuilder.setProxy(proxy);
                        log.debug("Datasource [{}] using proxy: {}:{}", key, config.getProxyHost(), config.getProxyPort());
                    }

                    return httpClientBuilder;
                });

                // ========== 请求配置（超时）==========
                restClientBuilder.setRequestConfigCallback(requestConfigBuilder -> {

                    // 连接超时
                    if (config.getConnectTimeout() != null) {
                        requestConfigBuilder.setConnectTimeout(config.getConnectTimeout());
                    }

                    // Socket 超时
                    if (config.getSocketTimeout() != null) {
                        requestConfigBuilder.setSocketTimeout(config.getSocketTimeout());
                    }

                    return requestConfigBuilder;
                });

                // ========== 路径前缀 ==========
                if (StringUtils.hasText(config.getPathPrefix())) {
                    restClientBuilder.setPathPrefix(config.getPathPrefix());
                    log.debug("Datasource [{}] using path prefix: {}", key, config.getPathPrefix());
                }

                // ========== 创建 RestHighLevelClient ==========
                RestHighLevelClient client = new RestHighLevelClient(restClientBuilder);
                ElasticsearchRestTemplate template = new ElasticsearchRestTemplate(client);

                templatesMap.put(key, template);

                log.info("✓ Elasticsearch datasource [{}] initialized successfully - hosts: {}",
                        key, config.getHosts());

            } catch (Exception e) {
                log.error("✗ Failed to create Elasticsearch client for datasource [{}]", key, e);
                throw new RuntimeException("Failed to initialize datasource: " + key, e);
            }
        });

        if (templatesMap.isEmpty()) {
            throw new IllegalStateException("No Elasticsearch datasource initialized!");
        }

        log.info("Created {} Elasticsearch datasource(s)", templatesMap.size());
    }

    /**
     * 解析 hosts 字符串为 HttpHost 数组
     */
    private HttpHost[] parseHosts(String hostsStr, boolean useSsl) {
        String[] hostArray = hostsStr.split(",");
        HttpHost[] httpHosts = new HttpHost[hostArray.length];

        String scheme = useSsl ? "https" : "http";

        for (int i = 0; i < hostArray.length; i++) {
            String host = hostArray[i].trim();
            String[] parts = host.split(":");

            String hostname = parts[0];
            int port = parts.length > 1 ? Integer.parseInt(parts[1]) : 9200;

            httpHosts[i] = new HttpHost(hostname, port, scheme);
        }

        return httpHosts;
    }

    /**
     * 创建路由代理 ElasticsearchRestTemplate
     */
    @Bean
    @Primary
    @ConditionalOnMissingBean(name = "elasticsearchRestTemplate")
    public ElasticsearchRestTemplate elasticsearchRestTemplate(RouteResolver routeResolver) {

        log.info("Creating routing ElasticsearchRestTemplate proxy with {} datasource(s)",
                templatesMap.size());
        log.info("Configured route rules: {} rule(s)", properties.getRules().size());

        // 获取默认模板
        String defaultKey = properties.getDefaultSource();
        ElasticsearchRestTemplate defaultTemplate = templatesMap.get(defaultKey);

        if (defaultTemplate == null) {
            throw new IllegalStateException(
                    "Default datasource [" + defaultKey + "] not found in configured datasources: "
                            + templatesMap.keySet());
        }

        log.info("Default Elasticsearch datasource set to: [{}]", defaultKey);

        // 创建动态代理
        ElasticsearchRestTemplate proxy = RouteTemplateProxy.createProxy(
                templatesMap,
                defaultTemplate,
                routeResolver
        );

        log.info("✓ Routing ElasticsearchRestTemplate initialized successfully");
        return proxy;
    }
}
