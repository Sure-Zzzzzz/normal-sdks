package io.github.surezzzzzz.sdk.iam.feign.client.configuration;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.constant.SimpleAkskClientCoreConstant;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.manager.ProfileTokenManager;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

/**
 * IAM Feign AKP Profile 配置；default 保持旧 TokenManager 兼容。
 *
 * @author surezzzzzz
 */
public class IamAkskFeignConfiguration {

    private final String akskProfile;

    @Autowired
    @Qualifier("akskFeignRequestInterceptor")
    private RequestInterceptor defaultInterceptor;

    @Autowired
    private ObjectProvider<ProfileTokenManager> profileTokenManagerProvider;

    public IamAkskFeignConfiguration(Environment environment) {
        this.akskProfile = environment.getProperty(
                "io.github.surezzzzzz.sdk.iam.client.aksk-profile",
                SimpleAkskClientCoreConstant.DEFAULT_PROFILE);
    }

    @Bean
    public RequestInterceptor akskRequestInterceptor() {
        if (!StringUtils.hasText(akskProfile)
                || SimpleAkskClientCoreConstant.DEFAULT_PROFILE.equals(akskProfile)) {
            return defaultInterceptor;
        }
        return new ProfileRequestInterceptor(profileTokenManagerProvider.getObject(), akskProfile);
    }

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
