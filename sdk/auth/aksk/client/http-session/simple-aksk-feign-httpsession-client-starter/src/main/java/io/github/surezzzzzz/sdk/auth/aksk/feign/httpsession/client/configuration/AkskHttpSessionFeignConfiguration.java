package io.github.surezzzzzz.sdk.auth.aksk.feign.httpsession.client.configuration;

import feign.RequestInterceptor;
import io.github.surezzzzzz.sdk.auth.aksk.feign.httpsession.client.interceptor.AkskHttpSessionFeignRequestInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;

/**
 * AKSK Feign Configuration
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
public class AkskHttpSessionFeignConfiguration {

    @Autowired
    private AkskHttpSessionFeignRequestInterceptor akskHttpSessionFeignRequestInterceptor;

    @Bean
    public RequestInterceptor akskRequestInterceptor() {
        return akskHttpSessionFeignRequestInterceptor;
    }
}
