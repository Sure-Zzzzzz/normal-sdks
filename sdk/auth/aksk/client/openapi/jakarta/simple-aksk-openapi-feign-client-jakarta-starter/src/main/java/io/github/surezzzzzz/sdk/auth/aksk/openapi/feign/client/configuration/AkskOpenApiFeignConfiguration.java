package io.github.surezzzzzz.sdk.auth.aksk.openapi.feign.client.configuration;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.constant.SimpleAkskClientCoreConstant;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.manager.ProfileTokenManager;
import io.github.surezzzzzz.sdk.auth.aksk.openapi.client.constant.SimpleAkskOpenApiClientConstant;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

/**
 * AKSK 管理 OpenAPI Feign AKP Profile 配置（Jakarta 线）
 *
 * <p>仅作为 Feign 子上下文配置使用，不加 {@code @Configuration}。
 * Profile 为空或 default 时复用底座默认拦截器；命名 Profile 时通过
 * client-core 契约 {@link ProfileTokenManager} 创建独立拦截器。</p>
 *
 * @author surezzzzzz
 */
public class AkskOpenApiFeignConfiguration {

    private final String akskProfile;

    @Autowired
    @Qualifier("akskFeignRequestInterceptor")
    private RequestInterceptor defaultInterceptor;

    @Autowired
    private ObjectProvider<ProfileTokenManager> profileTokenManagerProvider;

    public AkskOpenApiFeignConfiguration(Environment environment) {
        this.akskProfile = environment.getProperty(
                SimpleAkskOpenApiClientConstant.AKSK_PROFILE_CONFIG_KEY,
                SimpleAkskOpenApiClientConstant.DEFAULT_PROFILE);
    }

    /**
     * 注册唯一 AKSK 认证拦截器
     *
     * @return RequestInterceptor
     */
    @Bean
    public RequestInterceptor akskRequestInterceptor() {
        if (!StringUtils.hasText(akskProfile) || SimpleAkskClientCoreConstant.DEFAULT_PROFILE.equals(akskProfile)) {
            return defaultInterceptor;
        }
        ProfileTokenManager tokenManager = profileTokenManagerProvider.getObject();
        return new ProfileRequestInterceptor(tokenManager, akskProfile);
    }

    /**
     * 命名 Profile 拦截器
     */
    private static final class ProfileRequestInterceptor implements RequestInterceptor {

        private final ProfileTokenManager tokenManager;
        private final String profile;

        ProfileRequestInterceptor(ProfileTokenManager tokenManager, String profile) {
            this.tokenManager = tokenManager;
            this.profile = profile;
        }

        @Override
        public void apply(RequestTemplate template) {
            String token = tokenManager.getToken(profile);
            if (token != null && !token.isEmpty()) {
                template.removeHeader(SimpleAkskClientCoreConstant.HEADER_AUTHORIZATION);
                template.header(SimpleAkskClientCoreConstant.HEADER_AUTHORIZATION,
                        String.format(SimpleAkskClientCoreConstant.HEADER_AUTHORIZATION_TEMPLATE, token));
            }
        }
    }
}
