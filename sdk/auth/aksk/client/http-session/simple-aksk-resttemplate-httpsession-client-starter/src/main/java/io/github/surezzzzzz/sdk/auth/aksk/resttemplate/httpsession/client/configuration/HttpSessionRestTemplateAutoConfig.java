package io.github.surezzzzzz.sdk.auth.aksk.resttemplate.httpsession.client.configuration;

import io.github.surezzzzzz.sdk.auth.aksk.client.core.constant.SimpleAkskClientCoreConstant;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.manager.TokenManager;
import io.github.surezzzzzz.sdk.auth.aksk.resttemplate.httpsession.client.SimpleAkskHttpSessionRestTemplatePackage;
import io.github.surezzzzzz.sdk.auth.aksk.resttemplate.httpsession.client.annotation.HttpSessionRestTemplateComponent;
import io.github.surezzzzzz.sdk.auth.aksk.resttemplate.httpsession.client.interceptor.HttpSessionRestTemplateInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;

/**
 * Simple AKSK RestTemplate HttpSession Client Auto Configuration
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = SimpleAkskClientCoreConstant.CONFIG_PREFIX, name = "enable", havingValue = "true")
@ConditionalOnClass(RestTemplate.class)
@ConditionalOnBean(TokenManager.class)
@EnableConfigurationProperties(HttpSessionRestTemplateProperties.class)
@ComponentScan(
        basePackageClasses = SimpleAkskHttpSessionRestTemplatePackage.class,
        includeFilters = @ComponentScan.Filter(HttpSessionRestTemplateComponent.class),
        useDefaultFilters = false
)
public class HttpSessionRestTemplateAutoConfig {

    @PostConstruct
    public void init() {
        log.info("===== Simple AKSK RestTemplate HttpSession Client 自动配置加载成功 =====");
    }

    @Bean
    @ConditionalOnProperty(prefix = "io.github.surezzzzzz.sdk.auth.aksk.client.resttemplate", name = "enable", havingValue = "true")
    @ConditionalOnMissingBean(name = "akskClientRestTemplate")
    public RestTemplate akskClientRestTemplate(
            HttpSessionRestTemplateInterceptor interceptor,
            HttpSessionRestTemplateProperties properties) {

        log.info("Creating akskClientRestTemplate with connection pool - maxTotal: {}, maxPerRoute: {}, connectTimeout: {}ms, readTimeout: {}ms",
                properties.getMaxTotal(), properties.getMaxPerRoute(),
                properties.getConnectTimeout(), properties.getReadTimeout());

        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(properties.getMaxTotal());
        connectionManager.setDefaultMaxPerRoute(properties.getMaxPerRoute());

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(properties.getConnectTimeout())
                .setSocketTimeout(properties.getReadTimeout())
                .build();

        CloseableHttpClient httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .build();

        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(httpClient);
        RestTemplate restTemplate = new RestTemplate(factory);
        restTemplate.getInterceptors().add(interceptor);

        log.info("akskClientRestTemplate created successfully with AkskHttpSessionRestTemplateInterceptor");
        return restTemplate;
    }
}
