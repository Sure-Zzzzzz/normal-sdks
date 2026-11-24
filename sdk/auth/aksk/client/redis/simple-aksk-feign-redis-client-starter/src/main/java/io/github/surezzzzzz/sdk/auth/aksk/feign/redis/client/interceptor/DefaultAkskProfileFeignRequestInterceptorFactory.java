package io.github.surezzzzzz.sdk.auth.aksk.feign.redis.client.interceptor;

import feign.RequestInterceptor;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.constant.ClientErrorCode;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.constant.ClientErrorMessage;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.exception.ConfigurationException;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.manager.ProfileTokenManager;
import lombok.RequiredArgsConstructor;

/**
 * 默认 AKP Profile Feign 拦截器工厂
 *
 * @author surezzzzzz
 */
@RequiredArgsConstructor
public class DefaultAkskProfileFeignRequestInterceptorFactory
        implements AkskProfileFeignRequestInterceptorFactory {

    private final ProfileTokenManager tokenManager;

    /**
     * 为该 Profile 创建独立的请求拦截器
     */
    @Override
    public RequestInterceptor create(String profile) {
        if (!tokenManager.containsProfile(profile)) {
            throw new ConfigurationException(ClientErrorCode.CONFIG_MISSING,
                    String.format(ClientErrorMessage.CONFIG_MISSING, "AKP Profile: " + profile));
        }
        return new AkskProfileFeignRequestInterceptor(tokenManager, profile);
    }
}
