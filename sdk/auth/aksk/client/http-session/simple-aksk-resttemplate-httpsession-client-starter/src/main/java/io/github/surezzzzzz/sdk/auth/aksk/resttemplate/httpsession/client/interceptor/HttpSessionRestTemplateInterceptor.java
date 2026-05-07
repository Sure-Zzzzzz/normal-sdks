package io.github.surezzzzzz.sdk.auth.aksk.resttemplate.httpsession.client.interceptor;

import io.github.surezzzzzz.sdk.auth.aksk.client.core.constant.SimpleAkskClientCoreConstant;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.manager.TokenManager;
import io.github.surezzzzzz.sdk.auth.aksk.resttemplate.httpsession.client.annotation.HttpSessionRestTemplateComponent;
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
 * <p>自动为 RestTemplate 请求添加 Authorization 头
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@Slf4j
@RequiredArgsConstructor
@HttpSessionRestTemplateComponent
public class HttpSessionRestTemplateInterceptor implements ClientHttpRequestInterceptor {

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
