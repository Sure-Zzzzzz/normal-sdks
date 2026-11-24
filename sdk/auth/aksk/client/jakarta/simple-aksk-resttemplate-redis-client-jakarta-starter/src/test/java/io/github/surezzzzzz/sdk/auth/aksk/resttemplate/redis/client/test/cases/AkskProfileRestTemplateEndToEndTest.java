package io.github.surezzzzzz.sdk.auth.aksk.resttemplate.redis.client.test.cases;

import com.sun.net.httpserver.HttpServer;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.constant.SimpleAkskClientCoreConstant;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.exception.ConfigurationException;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.manager.ProfileTokenManager;
import io.github.surezzzzzz.sdk.auth.aksk.resttemplate.redis.client.support.AkskProfileRestTemplateFactory;
import io.github.surezzzzzz.sdk.auth.aksk.resttemplate.redis.client.support.AkskRestTemplateResolver;
import io.github.surezzzzzz.sdk.auth.aksk.resttemplate.redis.client.test.SimpleAkskRestTemplateRedisClientTestApplication;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 命名 AKP Profile 的 RestTemplate 工厂与解析器端到端测试
 *
 * <p>依赖真实 AKSK Server 换取 Token；请求头由本地桩服务回显捕获，用于确认命名 Profile 的模板
 * 只携带该 Profile 的 Authorization，默认模板仍使用顶层默认 AKP。命名 Profile {@code second}
 * 取本地 {@code application-local.yml} 的第二组已准入 AKP。
 *
 * @author surezzzzzz
 */
@SpringBootTest(classes = SimpleAkskRestTemplateRedisClientTestApplication.class)
class AkskProfileRestTemplateEndToEndTest {

    private static final String SECOND = "second";
    /**
     * 桩服务收到的 Authorization 头值，按请求顺序记录
     */
    private final List<String> capturedAuthorization = new CopyOnWriteArrayList<>();
    @Autowired
    @Qualifier("akskClientRestTemplate")
    private RestTemplate defaultTemplate;
    @Autowired
    private ProfileTokenManager profileTokenManager;
    @Autowired
    private AkskProfileRestTemplateFactory profileFactory;
    @Autowired
    private AkskRestTemplateResolver resolver;
    @Value("${io.github.surezzzzzz.sdk.auth.aksk.client.server-url}")
    private String serverBaseUrl;
    private HttpServer stub;
    private String stubUrl;

    private static String bearer(String token) {
        return String.format(SimpleAkskClientCoreConstant.HEADER_AUTHORIZATION_TEMPLATE, token);
    }

    /**
     * 断言 Token 一致；失败信息不携带 Token 明文
     */
    private static void assertSameToken(String expected, String actual, String message) {
        assertTrue(expected.equals(actual), message);
    }

    @BeforeEach
    void setUp() throws IOException {
        profileTokenManager.clearToken();
        profileTokenManager.clearToken(SECOND);
        capturedAuthorization.clear();
        stub = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        stub.createContext("/echo", exchange -> {
            List<String> values = exchange.getRequestHeaders().get(HttpHeaders.AUTHORIZATION);
            capturedAuthorization.addAll(values == null ? Collections.emptyList() : values);
            byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        stub.start();
        stubUrl = "http://127.0.0.1:" + stub.getAddress().getPort() + "/echo";
    }

    @AfterEach
    void tearDown() {
        if (stub != null) {
            stub.stop(0);
        }
    }

    @Test
    @DisplayName("命名 Profile 的模板按 Profile 缓存，且不是默认模板")
    void testFactoryCachesTemplatePerProfile() {
        RestTemplate first = profileFactory.create(SECOND);

        assertNotNull(first);
        assertSame(first, profileFactory.create(SECOND));
        assertNotSame(defaultTemplate, first);
    }

    @Test
    @DisplayName("未配置的 Profile 响亮失败")
    void testUnknownProfileFailsLoudly() {
        assertThrows(ConfigurationException.class, () -> profileFactory.create("missing-profile"));
    }

    @Test
    @DisplayName("命名 Profile 的模板只带一个 Authorization 头，且是该 Profile 的 Token")
    void testNamedProfileTemplateSendsItsOwnToken() {
        profileFactory.create(SECOND).getForEntity(stubUrl, String.class);

        assertEquals(1, capturedAuthorization.size(), "请求应只有一个 Authorization 头");
        assertSameToken(bearer(profileTokenManager.getToken(SECOND)), capturedAuthorization.get(0),
                "命名 Profile 的模板应携带该 Profile 的 Token");
    }

    @Test
    @DisplayName("默认模板仍然使用顶层默认 AKP")
    void testDefaultTemplateSendsDefaultToken() {
        defaultTemplate.getForEntity(stubUrl, String.class);

        assertEquals(1, capturedAuthorization.size(), "请求应只有一个 Authorization 头");
        assertSameToken(bearer(profileTokenManager.getToken()), capturedAuthorization.get(0),
                "默认模板应携带默认 Profile 的 Token");
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
    @DisplayName("请求里已有的 Authorization 头被替换，不叠加")
    void testExistingAuthorizationHeaderIsReplaced() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer stale-token");
        HttpEntity<Void> entity = new HttpEntity<Void>(headers);

        profileFactory.create(SECOND).exchange(stubUrl, HttpMethod.GET, entity, String.class);

        assertEquals(1, capturedAuthorization.size(), "已有的 Authorization 头应被替换而不是追加");
        assertSameToken(bearer(profileTokenManager.getToken(SECOND)), capturedAuthorization.get(0),
                "替换后应携带命名 Profile 的 Token");
    }

    @Test
    @DisplayName("命名 Profile 的模板调用真实 AKSK Server 受保护接口返回 200")
    void testNamedProfileTemplateCallsProtectedEndpoint() {
        ResponseEntity<String> response =
                profileFactory.create(SECOND).getForEntity(serverBaseUrl + "/api/token/statistics", String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    @DisplayName("解析器：空或 default 返回默认模板，命名 Profile 返回工厂模板")
    void testResolverRouting() {
        assertSame(defaultTemplate, resolver.resolve(null));
        assertSame(defaultTemplate, resolver.resolve(SimpleAkskClientCoreConstant.DEFAULT_PROFILE));
        assertSame(profileFactory.create(SECOND), resolver.resolve(SECOND));
    }
}

/**
 * 只有命名 AKP Profile、没有默认凭据时的启动与解析行为
 *
 * <p>验证：不配默认凭据且未显式开启默认模板时上下文正常启动、不创建默认模板；
 * 命名 Profile 的工厂与解析器可用；解析 default 响亮失败，不静默发送无凭据请求。
 *
 * @author surezzzzzz
 */
@SpringBootTest(classes = SimpleAkskRestTemplateRedisClientTestApplication.class, properties = {
        "io.github.surezzzzzz.sdk.auth.aksk.client.client-id=",
        "io.github.surezzzzzz.sdk.auth.aksk.client.client-secret=",
        "io.github.surezzzzzz.sdk.auth.aksk.client.resttemplate.enable=false"
})
class AkskProfileOnlyConfigTest {

    private static final String SECOND = "second";

    @Autowired(required = false)
    @Qualifier("akskClientRestTemplate")
    private RestTemplate defaultTemplate;
    @Autowired
    private ProfileTokenManager profileTokenManager;
    @Autowired
    private AkskProfileRestTemplateFactory profileFactory;
    @Autowired
    private AkskRestTemplateResolver resolver;
    @Value("${io.github.surezzzzzz.sdk.auth.aksk.client.server-url}")
    private String serverBaseUrl;

    @Test
    @DisplayName("只配命名 Profile 时不创建默认模板")
    void testNamedProfileWorksWithoutDefaultTemplate() {
        assertNull(defaultTemplate);
        RestTemplate second = profileFactory.create(SECOND);
        assertNotNull(second);
        assertSame(second, resolver.resolve(SECOND));
    }

    @Test
    @DisplayName("未配默认凭据时解析 default 响亮失败，不静默发送无凭据请求")
    void testResolveDefaultFailsLoudly() {
        // ObjectProvider 对缺失 bean 抛 NoSuchBeanDefinitionException，属于响亮失败
        assertThrows(NoSuchBeanDefinitionException.class,
                () -> resolver.resolve(SimpleAkskClientCoreConstant.DEFAULT_PROFILE));
    }

    @Test
    @DisplayName("命名 Profile 的模板调用真实 AKSK Server 受保护接口返回 200")
    void testNamedProfileTemplateCallsProtectedEndpoint() {
        ResponseEntity<String> response = profileFactory.create(SECOND)
                .getForEntity(serverBaseUrl + "/api/token/statistics", String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }
}

/**
 * 显式开启 resttemplate.enable 但默认凭据缺失时启动失败
 *
 * @author surezzzzzz
 */
class AkskProfileExplicitEnableFailureTest {

    @Test
    @DisplayName("显式开启默认模板但默认凭据缺失时启动失败")
    void testExplicitEnableWithoutDefaultCredentialsFails() {
        String[] keys = {
                "io.github.surezzzzzz.sdk.auth.aksk.client.client-id",
                "io.github.surezzzzzz.sdk.auth.aksk.client.client-secret",
                "io.github.surezzzzzz.sdk.auth.aksk.client.resttemplate.enable"
        };
        System.setProperty(keys[0], "");
        System.setProperty(keys[1], "");
        System.setProperty(keys[2], "true");
        try {
            BeanCreationException failure = assertThrows(BeanCreationException.class,
                    () -> new SpringApplicationBuilder(SimpleAkskRestTemplateRedisClientTestApplication.class)
                            .web(WebApplicationType.NONE)
                            .run());
            Throwable cause = failure.getCause();
            boolean found = false;
            while (cause != null) {
                if (cause instanceof ConfigurationException) {
                    found = true;
                    break;
                }
                cause = cause.getCause();
            }
            assertTrue(found, "启动失败原因应是默认凭据缺失的配置异常");
        } finally {
            System.clearProperty(keys[0]);
            System.clearProperty(keys[1]);
            System.clearProperty(keys[2]);
        }
    }
}
