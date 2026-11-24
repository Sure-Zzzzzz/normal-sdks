package io.github.surezzzzzz.sdk.auth.aksk.feign.redis.client.interceptor;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.constant.SimpleAkskClientCoreConstant;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.manager.ProfileTokenManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

/**
 * 命名 AKP Profile 的 Feign 拦截器
 *
 * @author surezzzzzz
 */
@Slf4j
@RequiredArgsConstructor
public class AkskProfileFeignRequestInterceptor implements RequestInterceptor {

    private final ProfileTokenManager tokenManager;
    private final String profile;

    /**
     * 为 Feign 请求设置该 Profile 的 Authorization 头；已有的 Authorization 头会被替换
     */
    @Override
    public void apply(RequestTemplate template) {
        log.debug("[AKSK-Feign] 拦截请求: {}, profile: {}", template.method(), profile);
        String token = tokenManager.getToken(profile);
        if (!StringUtils.hasText(token)) {
            log.warn("[AKSK-Feign] Profile 未返回可用 Token, profile: {}", profile);
            return;
        }
        boolean overwritten = template.headers().containsKey(SimpleAkskClientCoreConstant.HEADER_AUTHORIZATION);
        template.removeHeader(SimpleAkskClientCoreConstant.HEADER_AUTHORIZATION);
        template.header(
                SimpleAkskClientCoreConstant.HEADER_AUTHORIZATION,
                String.format(SimpleAkskClientCoreConstant.HEADER_AUTHORIZATION_TEMPLATE, token));
        log.debug("[AKSK-Feign] 已添加认证头, profile: {}, token长度: {}, 覆盖已有头: {}",
                profile, token.length(), overwritten);
    }
}
