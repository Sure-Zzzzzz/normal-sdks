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

        // DEBUG 埋点仅记录方法和目标主机，避免 URL Query 进入日志。
        log.debug("[AKSK-RestTemplate] 拦截请求: {} {}", request.getMethod(), request.getURI().getHost());

        String token = tokenManager.getToken();
        if (token == null || token.isEmpty()) {
            log.warn("[AKSK-RestTemplate] TokenManager 未返回可用 Token，本次请求不带 Authorization 头");
        } else {
            boolean overwritten = request.getHeaders().containsKey(SimpleAkskClientCoreConstant.HEADER_AUTHORIZATION);
            request.getHeaders().set(
                    SimpleAkskClientCoreConstant.HEADER_AUTHORIZATION,
                    String.format(SimpleAkskClientCoreConstant.HEADER_AUTHORIZATION_TEMPLATE, token));
            // DEBUG 埋点不记录 Token 值或 URL，只记录最小定位事实。
            log.debug("[AKSK-RestTemplate] 已添加 Authorization 头（token 长度={}, 覆盖已有头={}）",
                    token.length(), overwritten);
        }

        ClientHttpResponse response = execution.execute(request, body);
        // debug 埋点：响应状态与入口日志配对，定位慢请求与失败请求
        log.debug("[AKSK-RestTemplate] 请求完成: 状态码 {}", response.getStatusCode().value());
        return response;
    }
}
