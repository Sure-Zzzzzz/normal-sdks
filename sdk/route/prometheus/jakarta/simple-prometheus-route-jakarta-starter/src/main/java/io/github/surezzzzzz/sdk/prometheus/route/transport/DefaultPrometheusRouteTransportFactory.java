package io.github.surezzzzzz.sdk.prometheus.route.transport;

import io.github.surezzzzzz.sdk.prometheus.route.configuration.SimplePrometheusRouteProperties;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;

import java.net.URI;

/**
 * 默认 target 私有 HttpClient 创建器。
 *
 * @author surezzzzzz
 */
public class DefaultPrometheusRouteTransportFactory implements PrometheusRouteTransportFactory {

    @Override
    public PrometheusRouteTransport create(String targetKey, SimplePrometheusRouteProperties.TargetConfig config) {
        URI baseUri = PrometheusRouteUriFactory.normalizeBaseUri(config.getUrl());
        SimplePrometheusRouteProperties.HttpConfig http = config.getHttp();
        PoolingHttpClientConnectionManager manager = new PoolingHttpClientConnectionManager();
        manager.setValidateAfterInactivity(http.getValidateAfterInactivityMs());
        manager.setMaxTotal(http.getMaxTotal());
        manager.setDefaultMaxPerRoute(http.getMaxPerRoute());
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(http.getConnectTimeoutMs())
                .setSocketTimeout(http.getSocketTimeoutMs())
                .setConnectionRequestTimeout(http.getConnectionRequestTimeoutMs())
                .build();
        CloseableHttpClient client = HttpClients.custom()
                .setConnectionManager(manager)
                .setDefaultRequestConfig(requestConfig)
                .disableAutomaticRetries()
                .disableRedirectHandling()
                .disableCookieManagement()
                .disableContentCompression()
                .build();
        SimplePrometheusRouteProperties.AuthenticationConfig authentication = config.getAuthentication();
        return new PrometheusRouteTargetTransport(baseUri, client, authentication.getType(),
                authentication.getUsername(), authentication.getPassword(), authentication.getToken(),
                http.getMaxResponseBodyBytes());
    }
}
