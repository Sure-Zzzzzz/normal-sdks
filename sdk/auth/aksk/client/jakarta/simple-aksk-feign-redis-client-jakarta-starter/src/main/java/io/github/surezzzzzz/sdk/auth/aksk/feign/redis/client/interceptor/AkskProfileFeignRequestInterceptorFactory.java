package io.github.surezzzzzz.sdk.auth.aksk.feign.redis.client.interceptor;

import feign.RequestInterceptor;

/**
 * AKP Profile Feign 拦截器工厂契约
 *
 * @author surezzzzzz
 */
public interface AkskProfileFeignRequestInterceptorFactory {

    /**
     * 创建命名 Profile 拦截器
     *
     * @param profile Profile 名称
     * @return RequestInterceptor
     */
    RequestInterceptor create(String profile);
}
