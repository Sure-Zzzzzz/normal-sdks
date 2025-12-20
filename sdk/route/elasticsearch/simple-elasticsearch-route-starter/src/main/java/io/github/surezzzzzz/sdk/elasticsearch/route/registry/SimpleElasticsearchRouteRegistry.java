package io.github.surezzzzzz.sdk.elasticsearch.route.registry;

import io.github.surezzzzzz.sdk.elasticsearch.route.annotation.SimpleElasticsearchRouteComponent;
import io.github.surezzzzzz.sdk.elasticsearch.route.configuration.SimpleElasticsearchRouteProperties;
import io.github.surezzzzzz.sdk.elasticsearch.route.constant.ConfigConstant;
import io.github.surezzzzzz.sdk.elasticsearch.route.constant.ElasticsearchApiConstant;
import io.github.surezzzzzz.sdk.elasticsearch.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.elasticsearch.route.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.elasticsearch.route.exception.ConfigurationException;
import io.github.surezzzzzz.sdk.elasticsearch.route.exception.RouteException;
import io.github.surezzzzzz.sdk.elasticsearch.route.exception.VersionException;
import io.github.surezzzzzz.sdk.elasticsearch.route.support.RouteResolver;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.ssl.SSLContextBuilder;
import org.elasticsearch.client.*;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.net.ssl.SSLContext;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Elasticsearch 路由注册表
 * - 管理多数据源的 client/template
 * - 提供服务端版本信息（配置优先，探测兜底）
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleElasticsearchRouteComponent
@RequiredArgsConstructor
public class SimpleElasticsearchRouteRegistry {

    private static final Pattern VERSION_NUMBER_PATTERN =
            Pattern.compile(ElasticsearchApiConstant.VERSION_NUMBER_PATTERN_REGEX, Pattern.DOTALL);

    private final SimpleElasticsearchRouteProperties properties;
    private final RouteResolver routeResolver;

    private final Map<String, DataSourceClients> clientsMap = new ConcurrentHashMap<>();
    private final Map<String, ClusterInfo> clusterInfoMap = new ConcurrentHashMap<>();

    private volatile Map<String, ElasticsearchRestTemplate> templatesView = Collections.emptyMap();

    private ExecutorService versionDetectExecutor;

    @PostConstruct
    public void init() {
        log.info("Initializing SimpleElasticsearchRouteRegistry...");
        createAllClients();
        initClusterInfoAndDetect();
        log.info("✅ SimpleElasticsearchRouteRegistry initialized: datasources={}", clientsMap.keySet());
    }

    @PreDestroy
    public void destroy() {
        if (versionDetectExecutor != null) {
            versionDetectExecutor.shutdownNow();
        }

        clientsMap.forEach((key, clients) -> {
            try {
                clients.close();
            } catch (Exception e) {
                log.warn("Failed to close datasource [{}] client", key, e);
            }
        });
    }

    /**
     * 获取 ElasticsearchRestTemplate（Spring Data风格）
     *
     * <p>使用场景：
     * <ul>
     *   <li>使用Spring Data Elasticsearch Repository</li>
     *   <li>习惯Spring Template风格的API</li>
     * </ul>
     *
     * <p><b>版本兼容性说明：</b>
     * <ul>
     *   <li>Template 内部使用的是 Spring Data Elasticsearch API</li>
     *   <li>某些 Spring Data API（如 IndexOperations.getSettings()）可能在特定 ES 版本下不兼容</li>
     *   <li>如需版本自适应的操作，建议使用 {@link #getHighLevelClient(String)} 获取原生客户端</li>
     * </ul>
     *
     * @param datasourceKey 数据源key，传null使用默认数据源
     * @return ElasticsearchRestTemplate实例
     */
    public ElasticsearchRestTemplate getTemplate(String datasourceKey) {
        DataSourceClients clients = getClients(datasourceKey);
        return clients.getTemplate();
    }

    /**
     * 获取 RestHighLevelClient（版本自适应，推荐）
     *
     * <p>使用场景：
     * <ul>
     *   <li>执行查询、聚合、索引操作</li>
     *   <li>获取 mapping、获取索引信息</li>
     *   <li>需要使用 RestHighLevelClient 的高级API</li>
     *   <li><b>版本敏感的操作（推荐）</b></li>
     * </ul>
     *
     * <p><b>版本兼容性说明：</b>
     * <ul>
     *   <li>RestHighLevelClient 是 Elasticsearch 官方原生客户端</li>
     *   <li>route-starter 会根据配置的 server-version 自动创建对应版本的客户端</li>
     *   <li>使用原生 API 可以避免 Spring Data 的版本兼容性问题</li>
     *   <li><b>建议在需要版本兼容性保证时优先使用此客户端</b></li>
     * </ul>
     *
     * @param datasourceKey 数据源key，传null使用默认数据源
     * @return RestHighLevelClient实例
     */
    public RestHighLevelClient getHighLevelClient(String datasourceKey) {
        DataSourceClients clients = getClients(datasourceKey);
        return clients.getHighLevelClient();
    }

    /**
     * 获取 RestClient（低级客户端）
     *
     * <p>使用场景：
     * <ul>
     *   <li>需要直接操作HTTP请求</li>
     *   <li>版本检测、健康检查等底层操作</li>
     *   <li>HighLevelClient不支持的特殊API</li>
     * </ul>
     *
     * @param datasourceKey 数据源key，传null使用默认数据源
     * @return RestClient实例
     */
    public RestClient getLowLevelClient(String datasourceKey) {
        return getHighLevelClient(datasourceKey).getLowLevelClient();
    }

    public ClusterInfo getClusterInfo(String datasourceKey) {
        if (datasourceKey == null) {
            return null;
        }
        return clusterInfoMap.get(datasourceKey);
    }

    public Map<String, ElasticsearchRestTemplate> getTemplates() {
        return templatesView;
    }

    /**
     * 根据 indices 解析唯一的数据源（不支持跨数据源）
     */
    public String resolveDataSourceOrThrow(String[] indices) {
        if (indices == null || indices.length == 0) {
            return properties.getDefaultSource();
        }

        Map<String, List<String>> grouped = new LinkedHashMap<>();
        for (String index : indices) {
            if (index == null) {
                continue;
            }
            String ds = routeResolver.resolveDataSource(index);
            grouped.computeIfAbsent(ds, k -> new ArrayList<>()).add(index);
        }

        if (grouped.isEmpty()) {
            return properties.getDefaultSource();
        }

        Set<String> keys = grouped.keySet();
        if (keys.size() == 1) {
            return keys.iterator().next();
        }

        throw new RouteException(ErrorCode.ROUTE_CROSS_DATASOURCE,
                String.format(ErrorMessage.ROUTE_CROSS_DATASOURCE, keys, grouped));
    }

    private DataSourceClients getClients(String datasourceKey) {
        if (datasourceKey == null) {
            datasourceKey = properties.getDefaultSource();
        }
        DataSourceClients clients = clientsMap.get(datasourceKey);
        if (clients == null) {
            throw new RouteException(ErrorCode.ROUTE_DATASOURCE_NOT_FOUND,
                    String.format(ErrorMessage.ROUTE_DATASOURCE_NOT_FOUND,
                            datasourceKey, clientsMap.keySet()));
        }
        return clients;
    }

    private void createAllClients() {
        Map<String, ElasticsearchRestTemplate> templates = new HashMap<>();

        properties.getSources().forEach((key, config) -> {
            DataSourceClients clients = createDataSourceClients(key, config);
            clientsMap.put(key, clients);
            templates.put(key, clients.getTemplate());
        });

        if (clientsMap.isEmpty()) {
            throw new RouteException(ErrorCode.ROUTE_NO_DATASOURCE,
                    ErrorMessage.ROUTE_NO_DATASOURCE);
        }

        String defaultKey = properties.getDefaultSource();
        if (!clientsMap.containsKey(defaultKey)) {
            throw new RouteException(ErrorCode.CONFIG_DEFAULT_SOURCE_NOT_FOUND,
                    String.format(ErrorMessage.CONFIG_DEFAULT_SOURCE_NOT_FOUND,
                            defaultKey, clientsMap.keySet()));
        }

        templatesView = Collections.unmodifiableMap(templates);
        log.info("Created {} Elasticsearch datasource(s)", clientsMap.size());
    }

    private DataSourceClients createDataSourceClients(String key, SimpleElasticsearchRouteProperties.DataSourceConfig config) {
        try {
            List<String> urls = config.getResolvedUrls();
            HttpHost[] hosts = parseUrls(urls);

            RestClientBuilder restClientBuilder = buildRestClientBuilder(key, hosts, config, config.getConnectTimeout(), config.getSocketTimeout());

            RestHighLevelClient client = new RestHighLevelClient(restClientBuilder);
            ElasticsearchRestTemplate template = new ElasticsearchRestTemplate(client);

            log.info("✅ Elasticsearch datasource [{}] initialized successfully - urls: {}", key, urls);
            return new DataSourceClients(client, template);

        } catch (Exception e) {
            log.error("❌ Failed to create Elasticsearch client for datasource [{}]", key, e);
            throw new ConfigurationException(ErrorCode.OTHER_DATASOURCE_INIT_FAILED,
                    String.format(ErrorMessage.OTHER_DATASOURCE_INIT_FAILED, key), e);
        }
    }

    private void initClusterInfoAndDetect() {
        SimpleElasticsearchRouteProperties.VersionDetectConfig detectConfig = properties.getVersionDetect();
        boolean enabled = detectConfig != null && detectConfig.isEnabled();
        boolean failFastOnDetectError = detectConfig != null && detectConfig.isFailFastOnDetectError();
        Integer timeoutMs = detectConfig == null ? null : detectConfig.getTimeoutMs();

        properties.getSources().forEach((key, dsConfig) -> {
            ServerVersion configured = ServerVersion.tryParse(dsConfig.getServerVersion());
            if (configured != null) {
                log.info("[ds={}] server-version configured: {}", key, configured.getRaw());
            }
            clusterInfoMap.put(key, ClusterInfo.initial(key, configured));
        });

        if (!enabled) {
            log.info("Version detect disabled");
            return;
        }

        int threads = Math.min(ConfigConstant.VERSION_DETECT_THREAD_POOL_MAX,
                Math.max(ConfigConstant.VERSION_DETECT_THREAD_POOL_MIN, properties.getSources().size()));
        versionDetectExecutor = Executors.newFixedThreadPool(threads, runnable -> {
            Thread t = new Thread(runnable);
            t.setName(ConfigConstant.VERSION_DETECT_THREAD_NAME);
            t.setDaemon(true);
            return t;
        });

        if (failFastOnDetectError) {
            detectAllSyncForMissingConfigured(timeoutMs);
        }

        properties.getSources().forEach((key, dsConfig) -> {
            if (failFastOnDetectError && !StringUtils.hasText(dsConfig.getServerVersion())) {
                return;
            }
            versionDetectExecutor.submit(() -> detectAndUpdate(key, dsConfig, timeoutMs));
        });
    }

    private void detectAllSyncForMissingConfigured(Integer timeoutMs) {
        properties.getSources().forEach((key, dsConfig) -> {
            if (StringUtils.hasText(dsConfig.getServerVersion())) {
                return;
            }
            try {
                detectAndUpdate(key, dsConfig, timeoutMs);
            } catch (Exception e) {
                throw new VersionException(ErrorCode.VERSION_DETECT_FAILED,
                        String.format(ErrorMessage.VERSION_DETECT_FAILED, key), e);
            }
        });
    }

    private void detectAndUpdate(String datasourceKey,
                                 SimpleElasticsearchRouteProperties.DataSourceConfig dsConfig,
                                 Integer timeoutMs) {
        long now = System.currentTimeMillis();
        try {
            ServerVersion detected = detectServerVersion(datasourceKey, dsConfig, timeoutMs);
            clusterInfoMap.compute(datasourceKey, (k, old) -> {
                ClusterInfo base = old == null ? ClusterInfo.initial(datasourceKey, ServerVersion.tryParse(dsConfig.getServerVersion())) : old;
                return base.withDetected(detected, now);
            });

            ClusterInfo info = clusterInfoMap.get(datasourceKey);
            log.info("[ds={}] detected server-version: {}", datasourceKey, detected.getRaw());
            if (info != null && info.isVersionMismatch()) {
                log.warn("[ds={}] server-version mismatch: configured={}, detected={} (will use configured)",
                        datasourceKey,
                        info.getConfiguredVersion() == null ? null : info.getConfiguredVersion().getRaw(),
                        info.getDetectedVersion() == null ? null : info.getDetectedVersion().getRaw());
            }

        } catch (Exception e) {
            clusterInfoMap.compute(datasourceKey, (k, old) -> {
                ClusterInfo base = old == null ? ClusterInfo.initial(datasourceKey, ServerVersion.tryParse(dsConfig.getServerVersion())) : old;
                return base.withDetectError(e.getMessage(), now);
            });

            if (!StringUtils.hasText(dsConfig.getServerVersion())) {
                log.warn("[ds={}] detect server-version failed, mark as UNKNOWN (suggest configure sources.{}.server-version)",
                        datasourceKey, datasourceKey, e);
            } else {
                log.warn("[ds={}] detect server-version failed (configured={}, will use configured)",
                        datasourceKey, dsConfig.getServerVersion(), e);
            }
        }
    }

    private ServerVersion detectServerVersion(String datasourceKey,
                                              SimpleElasticsearchRouteProperties.DataSourceConfig dsConfig,
                                              Integer timeoutMs) throws IOException {
        RestClient probeClient = null;
        try {
            List<String> urls = dsConfig.getResolvedUrls();
            HttpHost[] hosts = parseUrls(urls);
            int connectTimeout = timeoutMs == null ? dsConfig.getConnectTimeout() : timeoutMs;
            int socketTimeout = timeoutMs == null ? dsConfig.getSocketTimeout() : timeoutMs;

            RestClientBuilder builder = buildRestClientBuilder(datasourceKey, hosts, dsConfig, connectTimeout, socketTimeout);
            probeClient = builder.build();

            Request request = new Request(ElasticsearchApiConstant.HTTP_METHOD_GET,
                    ElasticsearchApiConstant.ENDPOINT_ROOT);
            Response response = probeClient.performRequest(request);
            String body = readEntity(response.getEntity());

            String versionNumber = parseVersionNumber(body);
            if (!StringUtils.hasText(versionNumber)) {
                throw new VersionException(ErrorCode.VERSION_NUMBER_NOT_FOUND,
                        ErrorMessage.VERSION_NUMBER_NOT_FOUND);
            }

            return ServerVersion.parse(versionNumber);

        } finally {
            if (probeClient != null) {
                try {
                    probeClient.close();
                } catch (Exception e) {
                    log.debug("[ds={}] failed to close probe client", datasourceKey, e);
                }
            }
        }
    }

    private String parseVersionNumber(String body) {
        if (body == null) {
            return null;
        }
        Matcher matcher = VERSION_NUMBER_PATTERN.matcher(body);
        if (!matcher.find()) {
            return null;
        }
        return matcher.group(1);
    }

    private String readEntity(HttpEntity entity) throws IOException {
        if (entity == null) {
            return null;
        }
        try (InputStream inputStream = entity.getContent();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int len;
            while ((len = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, len);
            }
            return new String(outputStream.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private RestClientBuilder buildRestClientBuilder(String datasourceKey,
                                                     HttpHost[] hosts,
                                                     SimpleElasticsearchRouteProperties.DataSourceConfig config,
                                                     Integer connectTimeout,
                                                     Integer socketTimeout) {
        RestClientBuilder restClientBuilder = RestClient.builder(hosts);

        restClientBuilder.setHttpClientConfigCallback(httpClientBuilder -> {

            if (config.getKeepAliveStrategy() != null) {
                httpClientBuilder.setKeepAliveStrategy(
                        (response, context) -> TimeUnit.SECONDS.toMillis(config.getKeepAliveStrategy())
                );
            }

            if (config.getMaxConnTotal() != null) {
                httpClientBuilder.setMaxConnTotal(config.getMaxConnTotal());
            }

            if (config.getMaxConnPerRoute() != null) {
                httpClientBuilder.setMaxConnPerRoute(config.getMaxConnPerRoute());
            }

            if (config.isEnableConnectionReuse()) {
                httpClientBuilder.setConnectionReuseStrategy(DefaultConnectionReuseStrategy.INSTANCE);
            }

            if (StringUtils.hasText(config.getUsername()) && StringUtils.hasText(config.getPassword())) {
                BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
                credentialsProvider.setCredentials(
                        AuthScope.ANY,
                        new UsernamePasswordCredentials(config.getUsername(), config.getPassword())
                );
                httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
            }

            if (config.isUseSsl()) {
                try {
                    SSLContext sslContext;
                    if (config.isSkipSslValidation()) {
                        log.warn("⚠️  Datasource [{}] is configured to skip SSL validation - DO NOT use in production!", datasourceKey);
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
                } catch (Exception e) {
                    throw new ConfigurationException(ErrorCode.OTHER_SSL_CONFIG_FAILED,
                            String.format(ErrorMessage.OTHER_SSL_CONFIG_FAILED, datasourceKey), e);
                }
            }

            if (StringUtils.hasText(config.getProxyHost()) && config.getProxyPort() != null) {
                HttpHost proxy = new HttpHost(config.getProxyHost(), config.getProxyPort());
                httpClientBuilder.setProxy(proxy);
            }

            return httpClientBuilder;
        });

        restClientBuilder.setRequestConfigCallback(requestConfigBuilder -> {
            if (connectTimeout != null) {
                requestConfigBuilder.setConnectTimeout(connectTimeout);
            }
            if (socketTimeout != null) {
                requestConfigBuilder.setSocketTimeout(socketTimeout);
            }
            return requestConfigBuilder;
        });

        if (StringUtils.hasText(config.getPathPrefix())) {
            restClientBuilder.setPathPrefix(config.getPathPrefix());
        }

        return restClientBuilder;
    }

    private HttpHost[] parseUrls(List<String> urls) {
        if (CollectionUtils.isEmpty(urls)) {
            throw new ConfigurationException(ErrorCode.OTHER_URL_EMPTY,
                    ErrorMessage.OTHER_URL_EMPTY);
        }

        HttpHost[] httpHosts = new HttpHost[urls.size()];
        for (int i = 0; i < urls.size(); i++) {
            try {
                java.net.URL url = new java.net.URL(urls.get(i));
                String scheme = url.getProtocol();
                String hostname = url.getHost();
                int port = url.getPort();

                if (port == -1) {
                    port = ConfigConstant.PROTOCOL_HTTPS.equals(scheme)
                            ? ConfigConstant.DEFAULT_HTTPS_PORT
                            : ConfigConstant.DEFAULT_HTTP_PORT;
                }

                httpHosts[i] = new HttpHost(hostname, port, scheme);

            } catch (java.net.MalformedURLException e) {
                throw new ConfigurationException(ErrorCode.OTHER_URL_INVALID,
                        String.format(ErrorMessage.OTHER_URL_INVALID, urls.get(i)), e);
            }
        }
        return httpHosts;
    }

    @Getter
    @RequiredArgsConstructor
    private static class DataSourceClients {
        private final RestHighLevelClient highLevelClient;
        private final ElasticsearchRestTemplate template;

        private void close() throws IOException {
            highLevelClient.close();
        }
    }
}
