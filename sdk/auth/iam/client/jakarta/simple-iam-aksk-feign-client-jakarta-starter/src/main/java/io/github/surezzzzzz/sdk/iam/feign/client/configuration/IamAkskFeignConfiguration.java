package io.github.surezzzzzz.sdk.iam.feign.client.configuration;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.constant.SimpleAkskClientCoreConstant;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.manager.ProfileTokenManager;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.manager.TokenManager;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.core.Ordered;
import org.springframework.util.StringUtils;
import java.util.function.Supplier;

/**
 * IAM Feign AKP Profile 配置；default 保持旧 TokenManager 兼容。
 *
 * @author surezzzzzz
 */
public class IamAkskFeignConfiguration {

    private final String akskProfile;

    @Autowired
    private ObjectProvider<TokenManager> defaultTokenManagerProvider;

    @Autowired
    private ObjectProvider<ProfileTokenManager> profileTokenManagerProvider;

    public IamAkskFeignConfiguration(Environment environment) {
        this.akskProfile = environment.getProperty(
                "io.github.surezzzzzz.sdk.iam.client.aksk-profile",
                SimpleAkskClientCoreConstant.DEFAULT_PROFILE);
    }

    /**
     * Bean 名复用底座默认拦截器名，显式遮蔽父上下文中的默认拦截器；
     * 避免默认与 Profile 拦截器同时进入 Feign 拦截器集合。
     */
    @Bean("akskFeignRequestInterceptor")
    public RequestInterceptor akskRequestInterceptor() {
        if (!StringUtils.hasText(akskProfile)
                || SimpleAkskClientCoreConstant.DEFAULT_PROFILE.equals(akskProfile)) {
            TokenManager tokenManager = defaultTokenManagerProvider.getObject();
            return new AuthorizationRequestInterceptor(tokenManager::getToken, Ordered.HIGHEST_PRECEDENCE);
        }
        ProfileTokenManager tokenManager = profileTokenManagerProvider.getObject();
        return new AuthorizationRequestInterceptor(
                () -> tokenManager.getToken(akskProfile), Ordered.LOWEST_PRECEDENCE);
    }

    private static final class AuthorizationRequestInterceptor implements RequestInterceptor, Ordered {
        private final Supplier<String> tokenSupplier;
        private final int order;

        AuthorizationRequestInterceptor(Supplier<String> tokenSupplier, int order) {
            this.tokenSupplier = tokenSupplier;
            this.order = order;
        }

        @Override
        public int getOrder() {
            return order;
        }

        @Override
        public void apply(RequestTemplate template) {
            String token = tokenSupplier.get();
            if (token != null && !token.isEmpty()) {
                template.removeHeader(SimpleAkskClientCoreConstant.HEADER_AUTHORIZATION);
                template.header(SimpleAkskClientCoreConstant.HEADER_AUTHORIZATION,
                        String.format(SimpleAkskClientCoreConstant.HEADER_AUTHORIZATION_TEMPLATE, token));
            }
        }
    }
}
