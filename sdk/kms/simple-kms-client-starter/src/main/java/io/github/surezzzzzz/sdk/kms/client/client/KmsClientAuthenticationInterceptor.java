package io.github.surezzzzzz.sdk.kms.client.client;

import org.springframework.http.client.ClientHttpRequestInterceptor;

/**
 * KMS Client 调用身份拦截器。
 *
 * <p>仅用于注入调用身份；自动配置最多接受一个实现，且不会混入宿主 RestTemplate 的全局拦截器。</p>
 *
 * @author surezzzzzz
 */
public interface KmsClientAuthenticationInterceptor extends ClientHttpRequestInterceptor {
}
