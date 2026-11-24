package io.github.surezzzzzz.sdk.auth.aksk.openapi.feign.client;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.constant.SimpleAkskClientCoreConstant;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.manager.ProfileTokenManager;
import io.github.surezzzzzz.sdk.auth.aksk.openapi.client.constant.SimpleAkskOpenApiClientConstant;
import io.github.surezzzzzz.sdk.auth.aksk.openapi.feign.client.configuration.AkskOpenApiFeignConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.env.MockEnvironment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 管理 OpenAPI Feign 传输件的 AKP Profile 测试
 *
 * <p>验证配置类路由（空值复用全局默认拦截器、命名 Profile 创建独立拦截器）与命名 Profile
 * 的真实 AKSK Server 调用。命名 Profile {@code second} 取本地
 * {@code application-local.yml} 的第二组已准入 AKP。
 *
 * @author surezzzzzz
 */
@SpringBootTest(classes = OpenApiE2eTestApplication.class, properties = {
        "io.github.surezzzzzz.sdk.auth.aksk.openapi.client.aksk-profile=second"
})
class AkskProfileOpenApiFeignTest {

    private static final String SECOND = "second";

    @Autowired
    private AkskOpenApiFeignClient akskOpenApiFeignClient;
    @Autowired
    private ProfileTokenManager profileTokenManager;
    @Autowired
    private RequestInterceptor defaultInterceptor;
    @Autowired
    private ConfigurableApplicationContext applicationContext;

    @Test
    @DisplayName("aksk-profile=second 时经命名 Profile 调用真实 AKSK Server")
    void testNamedProfileFeignCallSucceeds() {
        String secondToken = profileTokenManager.getToken(SECOND);
        String defaultToken = profileTokenManager.getToken();
        assertFalse(secondToken.isEmpty());
        assertFalse(defaultToken.isEmpty());
        // 不用 assertNotEquals：失败时不把 Token 明文打进测试报告
        assertFalse(secondToken.equals(defaultToken), "两个 Profile 使用不同 AKP，Token 不应相同");
        assertNotNull(akskOpenApiFeignClient.getTokenStatistics());
    }

    @Test
    @DisplayName("配置路由：空值复用全局默认拦截器，命名 Profile 创建独立拦截器")
    void testConfigurationRouting() {
        AkskOpenApiFeignConfiguration blank = new AkskOpenApiFeignConfiguration(new MockEnvironment());
        applicationContext.getAutowireCapableBeanFactory().autowireBean(blank);
        RequestInterceptor blankInterceptor = blank.akskRequestInterceptor();

        MockEnvironment namedEnv = new MockEnvironment();
        namedEnv.setProperty(SimpleAkskOpenApiClientConstant.AKSK_PROFILE_CONFIG_KEY, SECOND);
        AkskOpenApiFeignConfiguration named = new AkskOpenApiFeignConfiguration(namedEnv);
        applicationContext.getAutowireCapableBeanFactory().autowireBean(named);
        RequestInterceptor namedInterceptor = named.akskRequestInterceptor();

        assertEquals(Ordered.HIGHEST_PRECEDENCE, ((Ordered) blankInterceptor).getOrder(),
                "未选择 Profile 时默认认证拦截器应先执行");
        assertEquals(Ordered.LOWEST_PRECEDENCE, ((Ordered) namedInterceptor).getOrder(),
                "命名 Profile 认证拦截器应最后执行");
        assertEquals("AuthorizationRequestInterceptor", blankInterceptor.getClass().getSimpleName(),
                "未选择 Profile 时应创建默认 Token 包装拦截器");
        assertEquals("AuthorizationRequestInterceptor", namedInterceptor.getClass().getSimpleName(),
                "命名 Profile 应创建独立拦截器");
        assertNotSame(defaultInterceptor, namedInterceptor);

        RequestTemplate blankTemplate = new RequestTemplate();
        blankTemplate.header(HttpHeaders.AUTHORIZATION, "Bearer stale");
        blankInterceptor.apply(blankTemplate);
        assertEquals(String.format(SimpleAkskClientCoreConstant.HEADER_AUTHORIZATION_TEMPLATE,
                profileTokenManager.getToken()), blankTemplate.headers().get(HttpHeaders.AUTHORIZATION).iterator().next());

        RequestTemplate template = new RequestTemplate();
        namedInterceptor.apply(template);
        List<String> values = template.headers().containsKey(HttpHeaders.AUTHORIZATION)
                ? new ArrayList<>(template.headers().get(HttpHeaders.AUTHORIZATION))
                : Collections.emptyList();
        String expectedNamedHeader = String.format(
                SimpleAkskClientCoreConstant.HEADER_AUTHORIZATION_TEMPLATE,
                profileTokenManager.getToken(SECOND));
        assertEquals(1, values.size(), "已有的 Authorization 头应被替换而不是追加");
        assertEquals(expectedNamedHeader, values.get(0), "命名 Profile 拦截器应携带该 Profile 的 Token");
    }
}
