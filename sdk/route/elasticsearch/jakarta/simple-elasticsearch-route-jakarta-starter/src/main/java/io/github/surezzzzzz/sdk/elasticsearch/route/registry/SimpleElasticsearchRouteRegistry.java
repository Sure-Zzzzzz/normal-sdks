package io.github.surezzzzzz.sdk.elasticsearch.route.registry;

import io.github.surezzzzzz.sdk.elasticsearch.route.configuration.SimpleElasticsearchRouteProperties;
import io.github.surezzzzzz.sdk.elasticsearch.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.elasticsearch.route.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.elasticsearch.route.constant.SimpleElasticsearchRouteConstant;
import io.github.surezzzzzz.sdk.elasticsearch.route.exception.ConfigurationException;
import io.github.surezzzzzz.sdk.elasticsearch.route.exception.RouteException;
import io.github.surezzzzzz.sdk.elasticsearch.route.exception.VersionException;
import io.github.surezzzzzz.sdk.elasticsearch.route.model.ClusterInfo;
import io.github.surezzzzzz.sdk.elasticsearch.route.model.ServerVersion;
import io.github.surezzzzzz.sdk.elasticsearch.route.resolver.RouteResolver;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.impl.NoConnectionReuseStrategy;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.ssl.SSLContextBuilder;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.convert.ElasticsearchConverter;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import javax.net.ssl.SSLContext;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Elasticsearch Jakarta 路由注册表。
 *
 * <p>每个数据源拥有独立的 low-level REST client 与 ELC 模板。通过反射创建 ELC
 * transport，是为了让本 starter 不把 Elastic Java Client 的实现类型暴露到公开 API；
 * 版本仍由调用方引入的 Spring Boot 3 BOM 统一治理。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@RequiredArgsConstructor
public class SimpleElasticsearchRouteRegistry {

    private static final Pattern VERSION_NUMBER_PATTERN =
            Pattern.compile(SimpleElasticsearchRouteConstant.VERSION_NUMBER_PATTERN_REGEX, Pattern.DOTALL);

    private static final String ELC_JSONP_MAPPER = "co.elastic.clients.json.JsonpMapper";
    private static final String ELC_JACKSON_MAPPER = "co.elastic.clients.json.jackson.JacksonJsonpMapper";
    private static final String ELC_TRANSPORT = "co.elastic.clients.transport.ElasticsearchTransport";
    private static final String ELC_REST_TRANSPORT = "co.elastic.clients.transport.rest_client.RestClientTransport";
    private static final String ELC_CLIENT = "co.elastic.clients.elasticsearch.ElasticsearchClient";
    private static final String ELC_TEMPLATE = "org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate";

    private final SimpleElasticsearchRouteProperties properties;
    private final RouteResolver routeResolver;
    private final ElasticsearchConverter elasticsearchConverter;
    private final ApplicationContext applicationContext;
    private final Map<String, DataSourceClients> clientsMap = new ConcurrentHashMap<>();
    private final Map<String, ClusterInfo> clusterInfoMap = new ConcurrentHashMap<>();

    private volatile Map<String, ElasticsearchOperations> templatesView = Collections.emptyMap();

    @PostConstruct
    public void init() {
        try {
            createAllClients();
            initClusterInfoAndDetect();
        } catch (RuntimeException | Error e) {
            // @PostConstruct 失败时 Spring 不会调用 @PreDestroy，已创建的 client 必须在此回收。
            destroy();
            throw e;
        }
    }

    @PreDestroy
    public void destroy() {
        clientsMap.forEach((key, clients) -> {
            try {
                clients.close();
            } catch (IOException e) {
                log.warn("数据源 [{}] 客户端关闭失败", key, e);
            }
        });
        clientsMap.clear();
        clusterInfoMap.clear();
        templatesView = Collections.emptyMap();
    }

    public ElasticsearchOperations getTemplate(String datasourceKey) {
        return getClients(datasourceKey).getTemplate();
    }

    /**
     * 返回指定数据源的 low-level client，适用于健康检查和 Spring Data 未覆盖的 HTTP API。
     * 调用方不得关闭该 client，其生命周期由注册表管理。
     */
    public RestClient getLowLevelClient(String datasourceKey) {
        return getClients(datasourceKey).getRestClient();
    }

    public ClusterInfo getClusterInfo(String datasourceKey) {
        return datasourceKey == null ? null : clusterInfoMap.get(datasourceKey);
    }

    public Map<String, ElasticsearchOperations> getTemplates() {
        return templatesView;
    }

    /**
     * 根据一组索引判定唯一数据源。跨数据源请求在发送前失败，避免错误聚合结果。
     */
    public String resolveDataSourceOrThrow(String[] indices) {
        return routeResolver.resolveDataSourceOrThrow(indices);
    }

    private DataSourceClients getClients(String datasourceKey) {
        String effectiveKey = datasourceKey == null ? properties.getDefaultSource() : datasourceKey;
        DataSourceClients clients = clientsMap.get(effectiveKey);
        if (clients == null) {
            throw new RouteException(ErrorCode.ROUTE_DATASOURCE_NOT_FOUND,
                    String.format(ErrorMessage.ROUTE_DATASOURCE_NOT_FOUND, effectiveKey, clientsMap.keySet()));
        }
        return clients;
    }

    private void createAllClients() {
        Map<String, ElasticsearchOperations> templates = new LinkedHashMap<>();
        properties.getSources().forEach((key, config) -> {
            DataSourceClients clients = createDataSourceClients(key, config);
            clientsMap.put(key, clients);
            templates.put(key, clients.getTemplate());
        });
        if (clientsMap.isEmpty()) {
            throw new RouteException(ErrorCode.ROUTE_NO_DATASOURCE, ErrorMessage.ROUTE_NO_DATASOURCE);
        }
        if (!clientsMap.containsKey(properties.getDefaultSource())) {
            throw new RouteException(ErrorCode.CONFIG_DEFAULT_SOURCE_NOT_FOUND,
                    String.format(ErrorMessage.CONFIG_DEFAULT_SOURCE_NOT_FOUND,
                            properties.getDefaultSource(), clientsMap.keySet()));
        }
        templatesView = Collections.unmodifiableMap(templates);
    }

    private DataSourceClients createDataSourceClients(String key, SimpleElasticsearchRouteProperties.DataSourceConfig config) {
        List<String> resolvedUrls = config.getResolvedUrls();
        log.debug("创建 Elasticsearch 数据源客户端，datasource=[{}]，endpointCount=[{}]",
                key, resolvedUrls.size());
        RestClient restClient = buildRestClientBuilder(key, parseUrls(resolvedUrls), config,
                config.getConnectTimeout(), config.getSocketTimeout()).build();
        try {
            ElasticsearchOperations template = createElasticsearchOperations(restClient);
            log.debug("Elasticsearch 数据源模板已创建，datasource=[{}]", key);
            return new DataSourceClients(restClient, template);
        } catch (RuntimeException | Error e) {
            try {
                restClient.close();
            } catch (IOException closeException) {
                e.addSuppressed(closeException);
            }
            throw e;
        }
    }

    /**
     * 创建 Spring Data 5 ELC 模板。模板必须使用 Spring Boot 已配置的 converter，并接收
     * ApplicationContext；否则应用的 Elasticsearch 自定义转换和 EntityCallbacks 都会被
     * 手工模板绕开。
     */
    private ElasticsearchOperations createElasticsearchOperations(RestClient restClient) {
        try {
            Class<?> jsonpMapperClass = Class.forName(ELC_JSONP_MAPPER);
            Object jsonpMapper = Class.forName(ELC_JACKSON_MAPPER).getConstructor().newInstance();
            Class<?> restTransportClass = Class.forName(ELC_REST_TRANSPORT);
            Object transport = restTransportClass
                    .getConstructor(RestClient.class, jsonpMapperClass)
                    .newInstance(restClient, jsonpMapper);
            Class<?> transportClass = Class.forName(ELC_TRANSPORT);
            Class<?> clientClass = Class.forName(ELC_CLIENT);
            Object client = clientClass
                    .getConstructor(transportClass)
                    .newInstance(transport);
            Class<?> templateClass = Class.forName(ELC_TEMPLATE);
            Object template = templateClass
                    .getConstructor(clientClass, ElasticsearchConverter.class)
                    .newInstance(client, elasticsearchConverter);
            if (template instanceof ApplicationContextAware) {
                ((ApplicationContextAware) template).setApplicationContext(applicationContext);
            }
            return (ElasticsearchOperations) template;
        } catch (ReflectiveOperationException e) {
            throw new ConfigurationException(ErrorCode.OTHER_ELC_TEMPLATE_INIT_FAILED,
                    ErrorMessage.OTHER_ELC_TEMPLATE_INIT_FAILED, e);
        }
    }

    private void initClusterInfoAndDetect() {
        SimpleElasticsearchRouteProperties.VersionDetectConfig detectConfig = properties.getVersionDetect();
        boolean enabled = detectConfig != null && detectConfig.isEnabled();
        boolean failFast = detectConfig != null && detectConfig.isFailFastOnDetectError();
        Integer timeoutMs = detectConfig == null ? null : detectConfig.getTimeoutMs();
        properties.getSources().forEach((key, config) ->
                clusterInfoMap.put(key, ClusterInfo.initial(key, ServerVersion.tryParse(config.getServerVersion()))));
        if (!enabled) {
            return;
        }
        // 版本兼容性不能在后台线程里“稍后再发现”。未配置 server-version 时必须完成探测；
        // 已配置版本的连接失败可由 fail-fast-on-detect-error 决定是否阻断，但一旦探测到
        // 不支持版本或与配置不一致，始终拒绝启动。
        properties.getSources().forEach((key, config) -> {
            boolean required = failFast || !StringUtils.hasText(config.getServerVersion());
            detectAndUpdate(key, config, timeoutMs, required);
        });
    }

    private void detectAndUpdate(String datasourceKey, SimpleElasticsearchRouteProperties.DataSourceConfig config,
                                 Integer timeoutMs, boolean failFast) {
        long detectedAt = System.currentTimeMillis();
        try {
            ServerVersion detected = detectServerVersion(datasourceKey, config, timeoutMs);
            validateDetectedVersion(datasourceKey, config, detected);
            clusterInfoMap.compute(datasourceKey, (key, old) -> (old == null
                    ? ClusterInfo.initial(datasourceKey, ServerVersion.tryParse(config.getServerVersion())) : old)
                    .withDetected(detected, detectedAt));
        } catch (VersionException e) {
            if (isCompatibilityFailure(e) || failFast) {
                throw e;
            }
            updateDetectError(datasourceKey, config, detectedAt, e);
        } catch (Exception e) {
            if (failFast) {
                throw new VersionException(ErrorCode.VERSION_DETECT_FAILED,
                        String.format(ErrorMessage.VERSION_DETECT_FAILED, datasourceKey), e);
            }
            updateDetectError(datasourceKey, config, detectedAt, e);
        }
    }

    private void validateDetectedVersion(String datasourceKey,
                                         SimpleElasticsearchRouteProperties.DataSourceConfig config,
                                         ServerVersion detected) {
        if (detected.isBefore(7, 17) || detected.getMajor() > 8) {
            throw new VersionException(ErrorCode.VERSION_UNSUPPORTED,
                    String.format(ErrorMessage.VERSION_UNSUPPORTED, datasourceKey, detected.getRaw()));
        }
        ServerVersion configured = ServerVersion.tryParse(config.getServerVersion());
        if (configured != null && !configured.equals(detected)) {
            throw new VersionException(ErrorCode.VERSION_MISMATCH,
                    String.format(ErrorMessage.VERSION_MISMATCH,
                            datasourceKey, configured.getRaw(), detected.getRaw()));
        }
    }

    private boolean isCompatibilityFailure(VersionException exception) {
        return ErrorCode.VERSION_UNSUPPORTED.equals(exception.getErrorCode())
                || ErrorCode.VERSION_MISMATCH.equals(exception.getErrorCode());
    }

    private void updateDetectError(String datasourceKey,
                                   SimpleElasticsearchRouteProperties.DataSourceConfig config,
                                   long detectedAt,
                                   Exception exception) {
        clusterInfoMap.compute(datasourceKey, (key, old) -> (old == null
                ? ClusterInfo.initial(datasourceKey, ServerVersion.tryParse(config.getServerVersion())) : old)
                .withDetectError(exception.getMessage(), detectedAt));
        log.warn("数据源 [{}] 服务端版本探测失败，将使用已配置 server-version [{}]",
                datasourceKey, config.getServerVersion(), exception);
    }

    private ServerVersion detectServerVersion(String datasourceKey, SimpleElasticsearchRouteProperties.DataSourceConfig config,
                                              Integer timeoutMs) throws IOException {
        RestClient probeClient = null;
        try {
            int connectTimeout = timeoutMs == null ? config.getConnectTimeout() : timeoutMs;
            int socketTimeout = timeoutMs == null ? config.getSocketTimeout() : timeoutMs;
            log.debug("开始探测 Elasticsearch 服务端版本，datasource=[{}]，connectTimeoutMs=[{}]，socketTimeoutMs=[{}]",
                    datasourceKey, connectTimeout, socketTimeout);
            probeClient = buildRestClientBuilder(datasourceKey, parseUrls(config.getResolvedUrls()), config,
                    connectTimeout, socketTimeout).build();
            Response response = probeClient.performRequest(new Request(
                    SimpleElasticsearchRouteConstant.HTTP_METHOD_GET, SimpleElasticsearchRouteConstant.ENDPOINT_ROOT));
            String version = parseVersionNumber(readEntity(response.getEntity()));
            if (!StringUtils.hasText(version)) {
                throw new VersionException(ErrorCode.VERSION_NUMBER_NOT_FOUND, ErrorMessage.VERSION_NUMBER_NOT_FOUND);
            }
            log.debug("Elasticsearch 服务端版本探测完成，datasource=[{}]，version=[{}]", datasourceKey, version);
            return ServerVersion.parse(version);
        } finally {
            if (probeClient != null) {
                probeClient.close();
            }
        }
    }

    private String parseVersionNumber(String body) {
        if (body == null) {
            return null;
        }
        Matcher matcher = VERSION_NUMBER_PATTERN.matcher(body);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String readEntity(HttpEntity entity) throws IOException {
        if (entity == null) {
            return null;
        }
        try (InputStream input = entity.getContent(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int length;
            while ((length = input.read(buffer)) != -1) {
                output.write(buffer, 0, length);
            }
            return new String(output.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private RestClientBuilder buildRestClientBuilder(String datasourceKey, HttpHost[] hosts,
                                                     SimpleElasticsearchRouteProperties.DataSourceConfig config,
                                                     Integer connectTimeout, Integer socketTimeout) {
        RestClientBuilder builder = RestClient.builder(hosts);
        builder.setHttpClientConfigCallback(httpClientBuilder -> {
            if (config.getKeepAliveStrategy() != null) {
                httpClientBuilder.setKeepAliveStrategy((response, context) ->
                        TimeUnit.SECONDS.toMillis(config.getKeepAliveStrategy()));
            }
            if (config.getMaxConnTotal() != null) {
                httpClientBuilder.setMaxConnTotal(config.getMaxConnTotal());
            }
            if (config.getMaxConnPerRoute() != null) {
                httpClientBuilder.setMaxConnPerRoute(config.getMaxConnPerRoute());
            }
            httpClientBuilder.setConnectionReuseStrategy(config.isEnableConnectionReuse()
                    ? DefaultConnectionReuseStrategy.INSTANCE
                    : NoConnectionReuseStrategy.INSTANCE);
            if (StringUtils.hasText(config.getUsername()) && StringUtils.hasText(config.getPassword())) {
                BasicCredentialsProvider provider = new BasicCredentialsProvider();
                provider.setCredentials(AuthScope.ANY,
                        new UsernamePasswordCredentials(config.getUsername(), config.getPassword()));
                httpClientBuilder.setDefaultCredentialsProvider(provider);
            }
            if (config.isUseSsl() || containsHttpsHost(hosts)) {
                configureSsl(datasourceKey, config, httpClientBuilder);
            }
            if (StringUtils.hasText(config.getProxyHost()) && config.getProxyPort() != null) {
                httpClientBuilder.setProxy(new HttpHost(config.getProxyHost(), config.getProxyPort()));
            }
            return httpClientBuilder;
        });
        builder.setRequestConfigCallback(requestBuilder -> {
            if (connectTimeout != null) {
                requestBuilder.setConnectTimeout(connectTimeout);
            }
            if (socketTimeout != null) {
                requestBuilder.setSocketTimeout(socketTimeout);
            }
            return requestBuilder;
        });
        if (StringUtils.hasText(config.getPathPrefix())) {
            builder.setPathPrefix(config.getPathPrefix());
        }
        return builder;
    }

    private boolean containsHttpsHost(HttpHost[] hosts) {
        for (HttpHost host : hosts) {
            if (host != null && SimpleElasticsearchRouteConstant.PROTOCOL_HTTPS.equals(host.getSchemeName())) {
                return true;
            }
        }
        return false;
    }

    private void configureSsl(String datasourceKey, SimpleElasticsearchRouteProperties.DataSourceConfig config,
                              org.apache.http.impl.nio.client.HttpAsyncClientBuilder httpClientBuilder) {
        try {
            if (config.isSkipSslValidation()) {
                log.warn("数据源 [{}] 已配置跳过 SSL 校验，请勿在生产环境使用", datasourceKey);
                httpClientBuilder.setSSLContext(SSLContextBuilder.create()
                        .loadTrustMaterial((chain, authType) -> true).build());
                httpClientBuilder.setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE);
                return;
            }
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, null, null);
            httpClientBuilder.setSSLContext(sslContext);
        } catch (Exception e) {
            throw new ConfigurationException(ErrorCode.OTHER_SSL_CONFIG_FAILED,
                    String.format(ErrorMessage.OTHER_SSL_CONFIG_FAILED, datasourceKey), e);
        }
    }

    private HttpHost[] parseUrls(List<String> urls) {
        if (CollectionUtils.isEmpty(urls)) {
            throw new ConfigurationException(ErrorCode.OTHER_URL_EMPTY, ErrorMessage.OTHER_URL_EMPTY);
        }
        HttpHost[] hosts = new HttpHost[urls.size()];
        for (int index = 0; index < urls.size(); index++) {
            try {
                java.net.URL url = new java.net.URL(urls.get(index));
                int port = url.getPort() == -1
                        ? (SimpleElasticsearchRouteConstant.PROTOCOL_HTTPS.equals(url.getProtocol())
                        ? SimpleElasticsearchRouteConstant.DEFAULT_HTTPS_PORT
                        : SimpleElasticsearchRouteConstant.DEFAULT_HTTP_PORT)
                        : url.getPort();
                hosts[index] = new HttpHost(url.getHost(), port, url.getProtocol());
            } catch (java.net.MalformedURLException e) {
                throw new ConfigurationException(ErrorCode.OTHER_URL_INVALID,
                        String.format(ErrorMessage.OTHER_URL_INVALID, urls.get(index)), e);
            }
        }
        return hosts;
    }

    @Getter
    @RequiredArgsConstructor
    private static class DataSourceClients {
        private final RestClient restClient;
        private final ElasticsearchOperations template;

        private void close() throws IOException {
            restClient.close();
        }
    }
}
