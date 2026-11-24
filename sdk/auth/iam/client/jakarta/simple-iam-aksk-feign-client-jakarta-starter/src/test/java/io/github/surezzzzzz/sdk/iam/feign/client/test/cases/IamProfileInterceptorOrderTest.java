package io.github.surezzzzzz.sdk.iam.feign.client.test.cases;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.constant.SimpleAkskClientCoreConstant;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.manager.ProfileTokenManager;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.manager.TokenManager;
import io.github.surezzzzzz.sdk.auth.aksk.feign.redis.client.interceptor.AkskFeignRequestInterceptor;
import io.github.surezzzzzz.sdk.iam.feign.client.configuration.IamAkskFeignConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IamProfileInterceptorOrderTest {

    private static final String PROFILE = "iam";

    @Test
    void defaultInterceptorShouldRunEarlyAndReplaceAuthorization() {
        IamAkskFeignConfiguration configuration = configuration("default");
        ReflectionTestUtils.setField(configuration, "defaultTokenManagerProvider", provider(defaultTokenManager()));
        RequestInterceptor interceptor = configuration.akskRequestInterceptor();

        assertEquals(Ordered.HIGHEST_PRECEDENCE, ((Ordered) interceptor).getOrder());
        RequestTemplate template = new RequestTemplate();
        template.header(SimpleAkskClientCoreConstant.HEADER_AUTHORIZATION, "Bearer stale");
        interceptor.apply(template);
        assertAuthorization(template, "default-token");
    }

    @Test
    void namedProfileInterceptorShouldRunLastAndReplaceAuthorization() {
        IamAkskFeignConfiguration configuration = configuration(PROFILE);
        ReflectionTestUtils.setField(configuration, "profileTokenManagerProvider", provider(profileTokenManager()));
        RequestInterceptor interceptor = configuration.akskRequestInterceptor();

        assertEquals(Ordered.LOWEST_PRECEDENCE, ((Ordered) interceptor).getOrder());
        RequestTemplate template = new RequestTemplate();
        template.header(SimpleAkskClientCoreConstant.HEADER_AUTHORIZATION, "Bearer stale");
        interceptor.apply(template);
        assertAuthorization(template, "profile-token");
    }

    @Test
    void iamChildInterceptorShouldMaskInheritedBaseDefault() {
        TokenManager defaultTokenManager = defaultTokenManager();
        GenericApplicationContext parent = new GenericApplicationContext();
        parent.getBeanFactory().registerSingleton("tokenManager", defaultTokenManager);
        parent.getBeanFactory().registerSingleton(
                "akskFeignRequestInterceptor", new AkskFeignRequestInterceptor(defaultTokenManager));
        parent.refresh();

        AnnotationConfigApplicationContext child = new AnnotationConfigApplicationContext();
        child.setParent(parent);
        child.setEnvironment(environment(PROFILE));
        child.getBeanFactory().registerSingleton("tokenManager", defaultTokenManager);
        child.getBeanFactory().registerSingleton("profileTokenManager", profileTokenManager());
        child.register(IamAkskFeignConfiguration.class);
        child.refresh();

        Map<String, RequestInterceptor> interceptors = child.getBeansOfType(RequestInterceptor.class);
        assertEquals(1, interceptors.size(), "IAM 子上下文应遮蔽底座默认拦截器");
        assertTrue(interceptors.containsKey("akskFeignRequestInterceptor"), "IAM 应复用底座 Bean 名完成遮蔽");
        assertEquals(Ordered.LOWEST_PRECEDENCE, ((Ordered) interceptors.get("akskFeignRequestInterceptor")).getOrder());
    }

    private static IamAkskFeignConfiguration configuration(String profile) {
        return new IamAkskFeignConfiguration(environment(profile));
    }

    private static MockEnvironment environment(String profile) {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("io.github.surezzzzzz.sdk.iam.client.aksk-profile", profile);
        return environment;
    }

    private static TokenManager defaultTokenManager() {
        return new TokenManager() {
            @Override
            public String getToken() {
                return "default-token";
            }

            @Override
            public void clearToken() {
            }
        };
    }

    private static ProfileTokenManager profileTokenManager() {
        return new ProfileTokenManager() {
            @Override
            public String getToken() {
                return "default-token";
            }

            @Override
            public void clearToken() {
            }

            @Override
            public String getToken(String profile) {
                return "profile-token";
            }

            @Override
            public void clearToken(String profile) {
            }

            @Override
            public boolean containsProfile(String profile) {
                return PROFILE.equals(profile);
            }
        };
    }

    private static <T> ObjectProvider<T> provider(T value) {
        return new ObjectProvider<T>() {
            @Override
            public T getObject() {
                return value;
            }
        };
    }

    private static void assertAuthorization(RequestTemplate template, String expectedToken) {
        String actual = template.headers().get(SimpleAkskClientCoreConstant.HEADER_AUTHORIZATION).iterator().next();
        assertEquals(String.format(SimpleAkskClientCoreConstant.HEADER_AUTHORIZATION_TEMPLATE, expectedToken), actual);
    }
}
