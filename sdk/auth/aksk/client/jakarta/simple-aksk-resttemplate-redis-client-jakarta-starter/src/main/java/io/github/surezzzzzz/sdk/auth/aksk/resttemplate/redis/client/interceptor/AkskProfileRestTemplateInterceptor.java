package io.github.surezzzzzz.sdk.auth.aksk.resttemplate.redis.client.interceptor;

import io.github.surezzzzzz.sdk.auth.aksk.client.core.constant.ClientErrorCode;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.constant.ClientErrorMessage;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.constant.SimpleAkskClientCoreConstant;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.exception.TokenFetchException;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.manager.ProfileTokenManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.StringUtils;

import java.io.IOException;

/**
 * 命名 AKP Profile 的 RestTemplate 拦截器
 *
 * @author surezzzzzz
 */
@Slf4j
@RequiredArgsConstructor
public class AkskProfileRestTemplateInterceptor implements ClientHttpRequestInterceptor {

    private final ProfileTokenManager tokenManager;
    private final String profile;

    /**
     * 为请求设置该 Profile 的 Authorization 头后继续执行；已有的 Authorization 头会被替换
     */
    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {
        log.debug("[AKSK-RestTemplate] 拦截请求: {} {}, profile: {}",
                request.getMethod(), request.getURI().getHost(), profile);
        String token = tokenManager.getToken(profile);
        if (!StringUtils.hasText(token)) {
            log.warn("[AKSK-RestTemplate] Profile 未返回可用 Token, profile: {}", profile);
            throw new TokenFetchException(
                    ClientErrorCode.HTTP_RESPONSE_INVALID,
                    String.format(ClientErrorMessage.HTTP_RESPONSE_INVALID,
                            SimpleAkskClientCoreConstant.TOKEN_RESPONSE_EMPTY));
        }
        boolean overwritten = request.getHeaders().containsKey(SimpleAkskClientCoreConstant.HEADER_AUTHORIZATION);
        request.getHeaders().set(
                SimpleAkskClientCoreConstant.HEADER_AUTHORIZATION,
                String.format(SimpleAkskClientCoreConstant.HEADER_AUTHORIZATION_TEMPLATE, token));
        log.debug("[AKSK-RestTemplate] 已添加认证头, profile: {}, token长度: {}, 覆盖已有头: {}",
                profile, token.length(), overwritten);
        ClientHttpResponse response = execution.execute(request, body);
        log.debug("[AKSK-RestTemplate] 请求完成: 状态码 {}, profile: {}",
                response.getStatusCode().value(), profile);
        return response;
    }
}
