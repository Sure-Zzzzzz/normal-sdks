package io.github.surezzzzzz.sdk.kms.client.configuration;

import io.github.surezzzzzz.sdk.kms.client.client.KmsClient;
import io.github.surezzzzzz.sdk.kms.client.client.KmsClientAuthenticationInterceptor;
import io.github.surezzzzzz.sdk.kms.client.client.RestTemplateKmsClient;
import io.github.surezzzzzz.sdk.kms.client.constant.SimpleKmsClientConstant;
import io.github.surezzzzzz.sdk.kms.client.exception.KmsClientConfigurationException;
import io.github.surezzzzzz.sdk.kms.client.port.*;
import io.github.surezzzzzz.sdk.kms.client.support.KmsClientUriHelper;
import io.github.surezzzzzz.sdk.kms.client.support.KmsHttpErrorMapper;
import io.github.surezzzzzz.sdk.kms.client.support.KmsHttpExecutor;
import io.github.surezzzzzz.sdk.kms.client.support.KmsJsonCodec;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;

/**
 * Simple KMS Client 自动配置。
 *
 * <p>默认实现始终创建 SDK 专属 JSON 编解码器和 HTTP 传输，不复用宿主的同类 Bean；调用方可通过同类型 Bean 替换每个扩展点。</p>
 *
 * @author surezzzzzz
 */
@Configuration
@EnableConfigurationProperties(SimpleKmsClientProperties.class)
public class SimpleKmsClientAutoConfiguration {

    /**
     * 创建 SDK 专属 JSON 编解码器，避免受宿主 ObjectMapper 配置影响。
     *
     * @return 可由调用方替换的独立编解码器
     */
    @Bean
    @ConditionalOnMissingBean
    public KmsJsonCodec kmsJsonCodec() {
        return new KmsJsonCodec();
    }

    /**
     * 在启用默认 Client 且未替换执行器时创建专属 HTTP 连接池。
     *
     * <p>禁用重定向，避免认证请求或敏感操作被透明转发到非固定 KMS API 地址。</p>
     *
     * @param properties Client 配置
     * @return 专属 HTTP Client
     */
    @Bean(name = SimpleKmsClientConstant.HTTP_CLIENT_BEAN_NAME, destroyMethod = "close")
    @ConditionalOnClass({RestTemplate.class, CloseableHttpClient.class})
    @ConditionalOnProperty(prefix = SimpleKmsClientConstant.CONFIG_PREFIX, name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean({KmsClient.class, KmsHttpExecutor.class})
    public CloseableHttpClient simpleKmsClientHttpClient(SimpleKmsClientProperties properties) {
        validate(properties);
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(properties.getMaxTotal());
        connectionManager.setDefaultMaxPerRoute(properties.getMaxPerRoute());
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(properties.getConnectTimeoutMillis())
                .setConnectionRequestTimeout(properties.getConnectionRequestTimeoutMillis())
                .setSocketTimeout(properties.getReadTimeoutMillis()).build();
        return HttpClients.custom().setConnectionManager(connectionManager).setDefaultRequestConfig(requestConfig)
                .disableRedirectHandling().build();
    }

    /**
     * 创建不继承宿主拦截器的具名专属 RestTemplate。
     *
     * <p>状态码由 {@link KmsHttpExecutor} 统一映射；至多接受一个 KMS 专属认证拦截器，避免认证头来源不确定。</p>
     *
     * @param simpleKmsClientHttpClient 专属 HTTP Client
     * @param authenticationInterceptor 可选的专属认证拦截器
     * @return 专属 RestTemplate
     */
    @Bean(name = SimpleKmsClientConstant.REST_TEMPLATE_BEAN_NAME)
    @ConditionalOnClass({RestTemplate.class, CloseableHttpClient.class})
    @ConditionalOnProperty(prefix = SimpleKmsClientConstant.CONFIG_PREFIX, name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean({KmsClient.class, KmsHttpExecutor.class})
    public RestTemplate simpleKmsClientRestTemplate(
            @Qualifier(SimpleKmsClientConstant.HTTP_CLIENT_BEAN_NAME) CloseableHttpClient simpleKmsClientHttpClient,
            ObjectProvider<KmsClientAuthenticationInterceptor> authenticationInterceptor) {
        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(simpleKmsClientHttpClient);
        RestTemplate restTemplate = new RestTemplate(factory);
        restTemplate.setErrorHandler(new DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(org.springframework.http.client.ClientHttpResponse response) throws IOException {
                return false;
            }
        });
        java.util.List<KmsClientAuthenticationInterceptor> interceptors = authenticationInterceptor.orderedStream()
                .collect(java.util.stream.Collectors.toList());
        if (interceptors.size() > 1) {
            throw new KmsClientConfigurationException(SimpleKmsClientConstant.MESSAGE_INVALID_CONFIGURATION);
        }
        if (!interceptors.isEmpty()) {
            restTemplate.getInterceptors().add(interceptors.get(0));
        }
        return restTemplate;
    }

    /**
     * 创建默认 HTTP 错误映射器。
     *
     * @return 可由调用方替换的状态码映射器
     */
    @Bean
    @ConditionalOnMissingBean
    public KmsHttpErrorMapper kmsHttpErrorMapper() {
        return new KmsHttpErrorMapper();
    }

    /**
     * 创建默认限长请求执行器。
     *
     * @param properties                  Client 配置
     * @param codec                       专属 JSON 编解码器
     * @param errorMapper                 HTTP 错误映射器
     * @param simpleKmsClientRestTemplate 专属 RestTemplate
     * @return 限制请求和响应大小的执行器
     */
    @Bean
    @ConditionalOnClass({RestTemplate.class, CloseableHttpClient.class})
    @ConditionalOnProperty(prefix = SimpleKmsClientConstant.CONFIG_PREFIX, name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean({KmsClient.class, KmsHttpExecutor.class})
    public KmsHttpExecutor kmsHttpExecutor(
            SimpleKmsClientProperties properties,
            KmsJsonCodec codec,
            KmsHttpErrorMapper errorMapper,
            @Qualifier(SimpleKmsClientConstant.REST_TEMPLATE_BEAN_NAME) RestTemplate simpleKmsClientRestTemplate) {
        validate(properties);
        return new KmsHttpExecutor(simpleKmsClientRestTemplate, codec, errorMapper, properties.getMaxRequestBytes(),
                properties.getMaxResponseBytes());
    }

    /**
     * 创建完整 KMS HTTP Client。
     *
     * @param properties Client 配置
     * @param executor   限长请求执行器
     * @return 未被调用方自定义实现替换时的默认 Client
     */
    @Bean
    @ConditionalOnClass({RestTemplate.class, CloseableHttpClient.class})
    @ConditionalOnProperty(prefix = SimpleKmsClientConstant.CONFIG_PREFIX, name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean(KmsClient.class)
    public KmsClient kmsClient(SimpleKmsClientProperties properties, KmsHttpExecutor executor) {
        validate(properties);
        return new RestTemplateKmsClient(KmsClientUriHelper.apiBaseUri(properties.getBaseUrl()), executor);
    }

    /**
     * 为任意 {@link KmsClient} 创建可替换的最小签名端口。
     *
     * @param kmsClient 完整 Client
     * @return 默认签名端口
     */
    @Bean
    @ConditionalOnBean(KmsClient.class)
    @ConditionalOnMissingBean(TenantSignerPort.class)
    public TenantSignerPort tenantSignerPort(KmsClient kmsClient) {
        return new DefaultTenantSignerPort(kmsClient);
    }

    /**
     * 为任意 {@link KmsClient} 创建可替换的最小公钥读取端口。
     *
     * @param kmsClient 完整 Client
     * @return 默认公钥读取端口
     */
    @Bean
    @ConditionalOnBean(KmsClient.class)
    @ConditionalOnMissingBean(TenantPublicKeyPort.class)
    public TenantPublicKeyPort tenantPublicKeyPort(KmsClient kmsClient) {
        return new DefaultTenantPublicKeyPort(kmsClient);
    }

    /**
     * 为任意 {@link KmsClient} 创建可替换的最小 envelope 加解密端口。
     *
     * @param kmsClient 完整 Client
     * @return 默认加解密端口
     */
    @Bean
    @ConditionalOnBean(KmsClient.class)
    @ConditionalOnMissingBean(KeyEncryptionPort.class)
    public KeyEncryptionPort keyEncryptionPort(KmsClient kmsClient) {
        return new DefaultKeyEncryptionPort(kmsClient);
    }

    private void validate(SimpleKmsClientProperties properties) {
        KmsClientUriHelper.apiBaseUri(properties.getBaseUrl());
        if (properties.getMaxTotal() == null || properties.getMaxTotal() < 1
                || properties.getMaxPerRoute() == null || properties.getMaxPerRoute() < 1
                || properties.getConnectTimeoutMillis() == null || properties.getConnectTimeoutMillis() < 1
                || properties.getConnectionRequestTimeoutMillis() == null || properties.getConnectionRequestTimeoutMillis() < 1
                || properties.getReadTimeoutMillis() == null || properties.getReadTimeoutMillis() < 1
                || properties.getMaxRequestBytes() == null || properties.getMaxRequestBytes() < 1
                || properties.getMaxResponseBytes() == null || properties.getMaxResponseBytes() < 1) {
            throw new KmsClientConfigurationException(SimpleKmsClientConstant.MESSAGE_INVALID_CONFIGURATION);
        }
    }
}
