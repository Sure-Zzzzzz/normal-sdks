package io.github.surezzzzzz.sdk.auth.aksk.resttemplate.redis.client.configuration;

import io.github.surezzzzzz.sdk.auth.aksk.client.core.constant.SimpleAkskClientCoreConstant;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.manager.TokenManager;
import io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.configuration.SimpleAkskRedisTokenManagerAutoConfiguration;
import io.github.surezzzzzz.sdk.auth.aksk.resttemplate.redis.client.interceptor.AkskRestTemplateInterceptor;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.util.Timeout;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * Simple AKSK RestTemplate Redis Client Auto Configuration
 * <p>
 * RestTemplate Client 的自动配置类
 * <p>
 * 启用条件：
 * <ul>
 *   <li>io.github.surezzzzzz.sdk.auth.aksk.client.enable=true</li>
 *   <li>存在 RestTemplate 类</li>
 *   <li>存在 TokenManager Bean（来自 Jakarta Redis Token Manager）</li>
 * </ul>
 * <p>
 * 提供的 Bean：
 * <ul>
 *   <li>AkskRestTemplateInterceptor - 拦截器（自动扫描注册）</li>
 *   <li>akskClientRestTemplate - 预配置的 RestTemplate（可选，需配置 resttemplate.enable=true）</li>
 * </ul>
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@AutoConfiguration(after = SimpleAkskRedisTokenManagerAutoConfiguration.class)
@ConditionalOnProperty(prefix = SimpleAkskClientCoreConstant.CONFIG_PREFIX, name = "enable", havingValue = "true")
@ConditionalOnClass({RestTemplate.class, CloseableHttpClient.class})
@ConditionalOnBean(TokenManager.class)
@EnableConfigurationProperties(SimpleAkskRestTemplateProperties.class)
public class SimpleAkskRestTemplateRedisClientAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(AkskRestTemplateInterceptor.class)
    public AkskRestTemplateInterceptor akskRestTemplateInterceptor(TokenManager tokenManager) {
        return new AkskRestTemplateInterceptor(tokenManager);
    }

    /**
     * 创建预配置的 RestTemplate Bean（可选）
     *
     * <p>提供一个已配置 AKSK 拦截器和连接池的 RestTemplate，用户可以直接注入使用。
     *
     * <p>启用条件：
     * <ul>
     *   <li>io.github.surezzzzzz.sdk.auth.aksk.client.resttemplate.enable=true</li>
     *   <li>用户未定义名为 {@code akskClientRestTemplate} 的 Bean</li>
     * </ul>
     *
     * <p>配置示例：
     * <pre>
     * io:
     *   github:
     *     surezzzzzz:
     *       sdk:
     *         auth:
     *           aksk:
     *             client:
     *               resttemplate:
     *                 enable: true
     *                 max-total: 200
     *                 max-per-route: 50
     *                 connect-timeout: 3000
     *                 read-timeout: 10000
     * </pre>
     *
     * <p>使用示例：
     * <pre>{@code
     * @Autowired
     * private RestTemplate akskClientRestTemplate;
     *
     * // 直接使用，无需手动配置拦截器和连接池
     * String result = akskClientRestTemplate.getForObject(url, String.class);
     * }</pre>
     *
     * @param interceptor AKSK RestTemplate 拦截器
     * @param properties  RestTemplate 配置
     * @return 预配置的 RestTemplate
     */
    @Bean
    @ConditionalOnProperty(prefix = "io.github.surezzzzzz.sdk.auth.aksk.client.resttemplate", name = "enable", havingValue = "true")
    @ConditionalOnMissingBean(name = "akskClientRestTemplate")
    public RestTemplate akskClientRestTemplate(
            AkskRestTemplateInterceptor interceptor,
            SimpleAkskRestTemplateProperties properties) {

        // 配置连接池
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(properties.getMaxTotal());
        connectionManager.setDefaultMaxPerRoute(properties.getMaxPerRoute());
        connectionManager.setDefaultConnectionConfig(ConnectionConfig.custom()
                .setConnectTimeout(Timeout.ofMilliseconds(properties.getConnectTimeout()))
                .build());

        // 配置请求参数
        RequestConfig requestConfig = RequestConfig.custom()
                .setResponseTimeout(Timeout.ofMilliseconds(properties.getReadTimeout()))
                .build();

        // 创建 HttpClient
        CloseableHttpClient httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .build();

        // 创建 RestTemplate
        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(httpClient);
        RestTemplate restTemplate = new RestTemplate(factory);

        // 添加 AKSK 拦截器
        restTemplate.getInterceptors().add(interceptor);

        return restTemplate;
    }
}
