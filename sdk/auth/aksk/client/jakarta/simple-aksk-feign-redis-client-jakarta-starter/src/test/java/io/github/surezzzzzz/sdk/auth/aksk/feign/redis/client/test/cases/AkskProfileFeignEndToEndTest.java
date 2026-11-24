package io.github.surezzzzzz.sdk.auth.aksk.feign.redis.client.test.cases;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.constant.SimpleAkskClientCoreConstant;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.exception.ConfigurationException;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.manager.ProfileTokenManager;
import io.github.surezzzzzz.sdk.auth.aksk.feign.redis.client.configuration.AkskProfileFeignConfiguration;
import io.github.surezzzzzz.sdk.auth.aksk.feign.redis.client.interceptor.AkskFeignRequestInterceptor;
import io.github.surezzzzzz.sdk.auth.aksk.feign.redis.client.interceptor.AkskProfileFeignRequestInterceptor;
import io.github.surezzzzzz.sdk.auth.aksk.feign.redis.client.interceptor.AkskProfileFeignRequestInterceptorFactory;
import io.github.surezzzzzz.sdk.auth.aksk.feign.redis.client.test.SimpleAkskFeignRedisClientTestApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 命名 AKP Profile 的 Feign 拦截器工厂与配置类端到端测试
 *
 * <p>依赖真实 AKSK Server 换取 Token。验证工厂创建的拦截器只携带该 Profile 的 Authorization、
 * 全局默认拦截器仍使用顶层默认 AKP、Profile 配置类在空值与命名 Profile 两种情况下的路由，
 * 以及使用命名 Profile 的 Feign 客户端可以调用真实受保护接口。命名 Profile {@code second}
 * 取本地 {@code application-local.yml} 的第二组已准入 AKP。
 *
 * @author surezzzzzz
 */
@SpringBootTest(classes = SimpleAkskFeignRedisClientTestApplication.class)
class AkskProfileFeignEndToEndTest {

    private static final String SECOND = "second";

    @Autowired
    private ProfileTokenManager profileTokenManager;
    @Autowired
    private AkskProfileFeignRequestInterceptorFactory profileInterceptorFactory;
    @Autowired
    private AkskFeignRequestInterceptor defaultInterceptor;
    @Autowired
    private ConfigurableApplicationContext applicationContext;
    @Autowired
    private SecondProfileClient secondProfileClient;

    private static String bearer(String token) {
        return String.format(SimpleAkskClientCoreConstant.HEADER_AUTHORIZATION_TEMPLATE, token);
    }

    private static List<String> authorizationValues(RequestTemplate template) {
        Collection<String> values = template.headers().get(HttpHeaders.AUTHORIZATION);
        return values == null ? Collections.emptyList() : new ArrayList<>(values);
    }

    /**
     * 断言 Token 一致；失败信息不携带 Token 明文
     */
    private static void assertSameToken(String expected, String actual, String message) {
        assertTrue(expected.equals(actual), message);
    }

    @BeforeEach
    void setUp() {
        profileTokenManager.clearToken();
        profileTokenManager.clearToken(SECOND);
    }

    @Test
    @DisplayName("工厂创建的拦截器只带一个 Authorization 头，且是该 Profile 的 Token")
    void testProfileInterceptorSetsSingleHeader() {
        RequestTemplate template = new RequestTemplate();
        template.header(HttpHeaders.AUTHORIZATION, "Bearer stale-token");

        profileInterceptorFactory.create(SECOND).apply(template);

        List<String> values = authorizationValues(template);
        assertEquals(1, values.size(), "已有的 Authorization 头应被替换而不是追加");
        assertSameToken(bearer(profileTokenManager.getToken(SECOND)), values.get(0), "拦截器应携带该 Profile 的 Token");
    }

    @Test
    @DisplayName("全局默认拦截器使用默认 AKP")
    void testDefaultInterceptorUsesDefaultToken() {
        RequestTemplate template = new RequestTemplate();

        defaultInterceptor.apply(template);

        List<String> values = authorizationValues(template);
        assertEquals(1, values.size(), "请求应只有一个 Authorization 头");
        assertSameToken(bearer(profileTokenManager.getToken()), values.get(0), "默认拦截器应携带默认 Profile 的 Token");
    }

    @Test
    @DisplayName("Profile 配置类：空值复用全局默认拦截器，命名 Profile 创建独立拦截器")
    void testProfileConfigurationRouting() {
        BlankProfileFeignConfiguration blank = new BlankProfileFeignConfiguration();
        applicationContext.getAutowireCapableBeanFactory().autowireBean(blank);
        RequestInterceptor blankInterceptor = blank.akskRequestInterceptor();

        SecondProfileFeignConfiguration named = new SecondProfileFeignConfiguration();
        applicationContext.getAutowireCapableBeanFactory().autowireBean(named);
        RequestInterceptor namedInterceptor = named.akskRequestInterceptor();

        assertSame(defaultInterceptor, blankInterceptor, "未选择 Profile 时应复用全局默认拦截器");
        assertTrue(namedInterceptor instanceof AkskProfileFeignRequestInterceptor, "命名 Profile 应创建独立拦截器");
        assertNotSame(defaultInterceptor, namedInterceptor);
        RequestTemplate template = new RequestTemplate();
        namedInterceptor.apply(template);
        assertSameToken(bearer(profileTokenManager.getToken(SECOND)), authorizationValues(template).get(0),
                "命名 Profile 拦截器应携带该 Profile 的 Token");
    }

    @Test
    @DisplayName("两个 Profile 的 Token 不同")
    void testProfilesUseDifferentCredentials() {
        String secondToken = profileTokenManager.getToken(SECOND);
        String defaultToken = profileTokenManager.getToken();

        assertFalse(secondToken.isEmpty());
        assertFalse(defaultToken.isEmpty());
        // 不用 assertNotEquals：失败时不把 Token 明文打进测试报告
        assertFalse(secondToken.equals(defaultToken), "两个 Profile 使用不同 AKP，Token 不应相同");
    }

    @Test
    @DisplayName("使用命名 Profile 的 Feign 客户端能调用真实 AKSK Server 受保护接口")
    void testNamedProfileFeignClientCallsProtectedEndpoint() {
        assertNotNull(secondProfileClient);

        String body = secondProfileClient.tokenStatistics();

        assertNotNull(body);
    }

    @Test
    @DisplayName("未配置的 Profile 在工厂创建时响亮失败")
    void testUnknownProfileFailsLoudlyAtCreate() {
        assertThrows(ConfigurationException.class, () -> profileInterceptorFactory.create("missing-profile"));
    }

    /**
     * 测试用：使用命名 Profile 的 Feign 客户端
     */
    @FeignClient(name = "second-profile-service", url = "${io.github.surezzzzzz.sdk.auth.aksk.client.server-url}",
            configuration = SecondProfileFeignConfiguration.class)
    interface SecondProfileClient {

        @GetMapping("/api/token/statistics")
        String tokenStatistics();
    }

    /**
     * 测试用：选择命名 Profile 的 Feign 配置类
     *
     * <p>不加 {@code @Configuration}，避免成为所有 Feign 客户端的默认配置。
     */
    static class SecondProfileFeignConfiguration extends AkskProfileFeignConfiguration {

        @Override
        protected String akskProfile() {
            return SECOND;
        }
    }

    /**
     * 测试用：未选择 Profile 的 Feign 配置类，表示领域未配置 aksk-profile
     */
    static class BlankProfileFeignConfiguration extends AkskProfileFeignConfiguration {
    }
}
