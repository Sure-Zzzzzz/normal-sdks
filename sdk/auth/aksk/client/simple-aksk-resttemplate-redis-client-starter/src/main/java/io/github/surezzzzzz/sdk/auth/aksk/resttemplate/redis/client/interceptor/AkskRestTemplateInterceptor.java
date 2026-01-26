package io.github.surezzzzzz.sdk.auth.aksk.resttemplate.redis.client.interceptor;

import io.github.surezzzzzz.sdk.auth.aksk.client.core.manager.TokenManager;
import io.github.surezzzzzz.sdk.auth.aksk.resttemplate.redis.client.annotation.SimpleAkskRestTemplateRedisClientComponent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

/**
 * AKSK RestTemplate 拦截器
 *
 * <p>自动为 RestTemplate 请求添加 Authorization 头。
 *
 * <p>核心功能：
 * <ul>
 *   <li>自动从 TokenManager 获取 Token</li>
 *   <li>自动添加 Authorization 头（Bearer Token）</li>
 * </ul>
 *
 * <p>使用方式1：注入拦截器，手动配置 RestTemplate
 * <pre>{@code
 * @Autowired
 * private AkskRestTemplateInterceptor interceptor;
 *
 * @Bean
 * public RestTemplate myRestTemplate() {
 *     RestTemplate restTemplate = new RestTemplate();
 *     restTemplate.getInterceptors().add(interceptor);
 *     return restTemplate;
 * }
 * }</pre>
 *
 * <p>使用方式2：直接注入预配置的 RestTemplate
 * <pre>{@code
 * @Autowired
 * private RestTemplate akskClientRestTemplate;
 *
 * // 直接使用，无需手动配置
 * akskClientRestTemplate.getForObject(url, String.class);
 * }</pre>
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@Slf4j
@SimpleAkskRestTemplateRedisClientComponent
public class AkskRestTemplateInterceptor implements ClientHttpRequestInterceptor {

    private final TokenManager tokenManager;

    public AkskRestTemplateInterceptor(TokenManager tokenManager) {
        this.tokenManager = tokenManager;
    }

    @Override
    public ClientHttpResponse intercept(
            HttpRequest request,
            byte[] body,
            ClientHttpRequestExecution execution) throws IOException {

        // 获取 Token
        String token = tokenManager.getToken();
        if (token == null || token.isEmpty()) {
            log.warn("No token available, proceeding without Authorization header");
        } else {
            // 添加 Authorization 头
            request.getHeaders().set("Authorization", "Bearer " + token);
            log.debug("Added Authorization header to request: {}", request.getURI());
        }

        // 执行请求
        return execution.execute(request, body);
    }
}
