package io.github.surezzzzzz.sdk.crm.client.feign.configuration;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.constant.SimpleAkskClientCoreConstant;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.manager.ProfileTokenManager;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.manager.TokenManager;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

import java.util.function.Supplier;

/**
 * CRM Feign AKP Profile 认证配置。
 *
 * <p>子上下文使用底座同名 Bean 遮蔽父上下文默认拦截器；默认拦截器顺序最高，
 * named Profile 拦截器顺序最低，最终 Authorization 固定由当前选择的 AKP 写入。</p>
 */
public class CrmAkskFeignConfiguration {

    private final String akskProfile;

    @Autowired
    private ObjectProvider<TokenManager> defaultTokenManagerProvider;

    @Autowired
    private ObjectProvider<ProfileTokenManager> profileTokenManagerProvider;

    public CrmAkskFeignConfiguration(Environment environment) {
        this.akskProfile = environment.getProperty(
                "io.github.surezzzzzz.sdk.crm.client.aksk-profile",
                SimpleAkskClientCoreConstant.DEFAULT_PROFILE);
    }

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