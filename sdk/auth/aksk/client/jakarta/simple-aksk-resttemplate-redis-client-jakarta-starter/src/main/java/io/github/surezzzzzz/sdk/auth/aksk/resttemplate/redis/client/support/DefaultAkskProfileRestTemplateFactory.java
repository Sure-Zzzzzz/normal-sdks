package io.github.surezzzzzz.sdk.auth.aksk.resttemplate.redis.client.support;

import io.github.surezzzzzz.sdk.auth.aksk.client.core.constant.ClientErrorCode;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.constant.ClientErrorMessage;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.exception.ConfigurationException;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.manager.ProfileTokenManager;
import io.github.surezzzzzz.sdk.auth.aksk.resttemplate.redis.client.configuration.SimpleAkskRestTemplateProperties;
import io.github.surezzzzzz.sdk.auth.aksk.resttemplate.redis.client.interceptor.AkskProfileRestTemplateInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.util.Timeout;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 默认 AKP Profile RestTemplate 工厂
 *
 * <p>共享连接池；同名 Profile 复用同一个 RestTemplate。
 *
 * @author surezzzzzz
 */
@Slf4j
public class DefaultAkskProfileRestTemplateFactory implements AkskProfileRestTemplateFactory {

    private final ProfileTokenManager tokenManager;
    private final SimpleAkskRestTemplateProperties properties;
    private final ClientHttpRequestFactory requestFactory;
    private final Map<String, RestTemplate> templates = new ConcurrentHashMap<>();

    public DefaultAkskProfileRestTemplateFactory(ProfileTokenManager tokenManager,
                                                 SimpleAkskRestTemplateProperties properties) {
        this.tokenManager = tokenManager;
        this.properties = properties;
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(properties.getMaxTotal());
        connectionManager.setDefaultMaxPerRoute(properties.getMaxPerRoute());
        connectionManager.setDefaultConnectionConfig(ConnectionConfig.custom()
                .setConnectTimeout(Timeout.ofMilliseconds(properties.getConnectTimeout()))
                .build());
        CloseableHttpClient httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setConnectTimeout(Timeout.ofMilliseconds(properties.getConnectTimeout()))
                        .setResponseTimeout(Timeout.ofMilliseconds(properties.getReadTimeout()))
                        .build())
                .build();
        this.requestFactory = new HttpComponentsClientHttpRequestFactory(httpClient);
    }

    /**
     * 创建或复用该 Profile 的 RestTemplate；Profile 未配置时抛出配置异常
     */
    @Override
    public RestTemplate create(String profile) {
        if (!tokenManager.containsProfile(profile)) {
            throw new ConfigurationException(
                    ClientErrorCode.CONFIG_MISSING,
                    String.format(ClientErrorMessage.CONFIG_MISSING, "AKP Profile: " + profile));
        }
        return templates.computeIfAbsent(profile, name -> {
            RestTemplate template = new RestTemplate(requestFactory);
            template.getInterceptors().add(new AkskProfileRestTemplateInterceptor(tokenManager, name));
            log.debug("创建 AKP Profile RestTemplate, profile: {}", name);
            return template;
        });
    }
}
