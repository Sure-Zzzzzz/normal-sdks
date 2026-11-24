package io.github.surezzzzzz.sdk.auth.aksk.feign.redis.client.configuration;

import feign.RequestInterceptor;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.constant.SimpleAkskClientCoreConstant;
import io.github.surezzzzzz.sdk.auth.aksk.feign.redis.client.interceptor.AkskFeignRequestInterceptor;
import io.github.surezzzzzz.sdk.auth.aksk.feign.redis.client.interceptor.AkskProfileFeignRequestInterceptorFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;

/**
 * AKP Profile Feign 配置基类
 *
 * <p>领域子类必须不加 {@code @Configuration}，仅作为 Feign 子上下文配置使用。
 *
 * @author surezzzzzz
 */
public abstract class AkskProfileFeignConfiguration {

    @Autowired
    protected ObjectProvider<AkskFeignRequestInterceptor> defaultInterceptor;

    @Autowired
    protected AkskProfileFeignRequestInterceptorFactory profileInterceptorFactory;

    /**
     * 领域子类返回配置的 AKP Profile；空或 default 使用默认拦截器
     *
     * @return Profile 名称
     */
    protected String akskProfile() {
        return null;
    }

    /**
     * 注册唯一 AKSK 认证拦截器
     *
     * @return RequestInterceptor
     */
    @Bean
    public RequestInterceptor akskRequestInterceptor() {
        String profile = akskProfile();
        if (!StringUtils.hasText(profile) || SimpleAkskClientCoreConstant.DEFAULT_PROFILE.equals(profile)) {
            return defaultInterceptor.getObject();
        }
        return profileInterceptorFactory.create(profile);
    }
}
