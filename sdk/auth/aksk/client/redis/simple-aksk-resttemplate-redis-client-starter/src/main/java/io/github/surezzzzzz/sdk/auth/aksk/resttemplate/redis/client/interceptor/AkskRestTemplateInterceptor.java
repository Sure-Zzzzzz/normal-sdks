package io.github.surezzzzzz.sdk.auth.aksk.resttemplate.redis.client.interceptor;

import io.github.surezzzzzz.sdk.auth.aksk.client.core.constant.SimpleAkskClientCoreConstant;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.manager.TokenManager;
import io.github.surezzzzzz.sdk.auth.aksk.resttemplate.redis.client.annotation.SimpleAkskRestTemplateRedisClientComponent;
import lombok.RequiredArgsConstructor;
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
 * }</pre>
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@Slf4j
@RequiredArgsConstructor
@SimpleAkskRestTemplateRedisClientComponent
public class AkskRestTemplateInterceptor implements ClientHttpRequestInterceptor {

    private final TokenManager tokenManager;

    @Override
    public ClientHttpResponse intercept(
            HttpRequest request,
            byte[] body,
            ClientHttpRequestExecution execution) throws IOException {

        String token = tokenManager.getToken();
        if (token == null || token.isEmpty()) {
            log.warn("No token available, proceeding without Authorization header");
        } else {
            request.getHeaders().set(
                    SimpleAkskClientCoreConstant.HEADER_AUTHORIZATION,
                    String.format(SimpleAkskClientCoreConstant.HEADER_AUTHORIZATION_TEMPLATE, token));
            log.debug("Added Authorization header to request: {}", request.getURI());
        }

        return execution.execute(request, body);
    }
}
