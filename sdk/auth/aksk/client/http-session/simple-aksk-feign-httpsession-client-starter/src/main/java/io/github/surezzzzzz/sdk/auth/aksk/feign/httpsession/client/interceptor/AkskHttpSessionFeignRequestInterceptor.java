package io.github.surezzzzzz.sdk.auth.aksk.feign.httpsession.client.interceptor;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.constant.SimpleAkskClientCoreConstant;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.manager.TokenManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * AKSK Feign Request Interceptor
 *
 * <p>自动为 Feign 请求添加 Authorization 头。
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class AkskHttpSessionFeignRequestInterceptor implements RequestInterceptor {

    private final TokenManager tokenManager;

    @Override
    public void apply(RequestTemplate template) {
        String token = tokenManager.getToken();
        if (token == null || token.isEmpty()) {
            log.warn("No token available, proceeding without Authorization header");
        } else {
            String authorizationValue = String.format(SimpleAkskClientCoreConstant.HEADER_AUTHORIZATION_TEMPLATE, token);
            template.removeHeader(SimpleAkskClientCoreConstant.HEADER_AUTHORIZATION);
            template.header(SimpleAkskClientCoreConstant.HEADER_AUTHORIZATION, authorizationValue);
            log.debug("Added Authorization header to request: {}", template.url());
        }
    }
}
