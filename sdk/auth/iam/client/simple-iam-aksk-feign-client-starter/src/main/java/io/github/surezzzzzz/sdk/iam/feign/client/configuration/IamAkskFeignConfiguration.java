package io.github.surezzzzzz.sdk.iam.feign.client.configuration;

import feign.FeignException;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import feign.codec.ErrorDecoder;
import feign.Response;
import feign.RetryableException;
import feign.Retryer;
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

    /**
     * IAM 401 表示已携带的 Token 被判定不可用；先清理当前 Profile 缓存，
     * 再允许 Feign 重放一次。重放后仍 401 时还原 IAM 原始 401 响应。
     */
    @Bean
    public ErrorDecoder iamProfileTokenErrorDecoder() {
        return (methodKey, response) -> {
            if (response.status() != 401) {
                return new ErrorDecoder.Default().decode(methodKey, response);
            }
            clearRejectedToken();
            return new IamUnauthorizedRetryableException(methodKey, response);
        };
    }

    @Bean
    public Retryer iamProfileTokenRetryer() {
        return new IamUnauthorizedRetryer();
    }

    private void clearRejectedToken() {
        if (!StringUtils.hasText(akskProfile)
                || SimpleAkskClientCoreConstant.DEFAULT_PROFILE.equals(akskProfile)) {
            defaultTokenManagerProvider.getObject().clearToken();
            return;
        }
        profileTokenManagerProvider.getObject().clearToken(akskProfile);
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

    private static final class IamUnauthorizedRetryableException extends RetryableException {
        private final Response response;

        IamUnauthorizedRetryableException(String methodKey, Response response) {
            super(401, "IAM token rejected; profile cache invalidated",
                    response.request().httpMethod(), null, new java.util.Date(), response.request());
            this.response = response;
        }
    }

    private static final class IamUnauthorizedRetryer implements Retryer {
        private int attempts;

        @Override
        public void continueOrPropagate(RetryableException exception) {
            if (!(exception instanceof IamUnauthorizedRetryableException)) {
                throw exception;
            }
            if (attempts++ > 0) {
                throw FeignException.errorStatus(
                        "IAM Profile Feign", ((IamUnauthorizedRetryableException) exception).response);
            }
        }

        @Override
        public Retryer clone() {
            return new IamUnauthorizedRetryer();
        }
    }
}
