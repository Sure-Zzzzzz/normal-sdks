package io.github.surezzzzzz.sdk.auth.iam.adapter.login.oidc.test.cases;

import com.jayway.jsonpath.JsonPath;
import io.github.surezzzzzz.sdk.auth.iam.adapter.login.oidc.constant.SimpleIamOidcAdapterConstant;
import io.github.surezzzzzz.sdk.auth.iam.adapter.login.oidc.test.SimpleIamOidcAdapterTestApplication;
import io.github.surezzzzzz.sdk.auth.iam.adapter.login.oidc.test.support.KeycloakLoginFormSupport;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamUserEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamUserRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.RedisTokenRepository;
import io.github.surezzzzzz.sdk.redis.route.template.RedisRouteTemplate;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OIDC 跨实例分布式登录端到端测试（真实 Keycloak + MySQL + Redis 双实例）
 *
 * <p>核心命题：实例 A 发起 authorize 写入的 pendingState 落共享 Redis，
 * 实例 B 收 callback 消费该 state 完成登录——去除粘性路由后 OIDC 浏览器
 * 登录在任意实例组合下成立。防重放 state 经 spring-session 共享会话传递，
 * 由 B 侧读回（会话跨实例互认的同一机制）。</p>
 *
 * <p>实例 B 经 SpringApplication 以随机端口另起，关闭 bootstrap 引导防 A/B
 * 竞争（先例：{@code IamRedisSessionMultiInstanceTest}）。两实例为同 JVM 双
 * 上下文，但 pendingState 若未落 Redis 则两实例的 store 互不可见——本测试
 * 通过即为 Redis 分支跨实例生效的实证，并前置断言装配形态排除 memory 分支
 * 意外通过。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(
        classes = SimpleIamOidcAdapterTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class IamOidcCrossInstanceStateTest {

    private static final String OIDC_FIXTURE_USERNAME = "e2e-user";
    private static final String AUTHORIZE_PATH = "/iam/web/auth/authorize/oidc";
    private static final String CALLBACK_PATH = "/iam/web/auth/callback/oidc";
    private static final String ME_PATH = "/iam/web/auth/me";
    private static final int SESSION_COOKIE_NAME_LENGTH =
            SimpleIamServerConstant.SESSION_COOKIE_NAME.length() + 1;
    private static ConfigurableApplicationContext contextB;
    private static int portB;
    @LocalServerPort
    private int portA;
    @Value("${io.github.surezzzzzz.sdk.auth.iam.adapter.login.oidc.test.oidc.user-password}")
    private String oidcUserPassword;
    @Autowired
    private IamUserRepository userRepository;
    @Autowired
    private RedisTokenRepository redisTokenRepository;
    @Autowired
    private RedisRouteTemplate redisRouteTemplate;

    @BeforeAll
    static void startInstanceB() {
        contextB = new SpringApplication(SimpleIamOidcAdapterTestApplication.class)
                .run("--server.port=0",
                        "--io.github.surezzzzzz.sdk.auth.iam.server.bootstrap.enabled=false");
        portB = ((org.springframework.boot.web.server.WebServer) (
                (org.springframework.boot.web.context.WebServerApplicationContext) contextB)
                .getWebServer()).getPort();
    }

    @AfterAll
    static void stopInstanceB() {
        if (contextB != null) {
            contextB.close();
        }
    }

    private static Response get(String url, String jsessionId) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(10000);
        if (jsessionId != null) {
            connection.setRequestProperty("Cookie",
                    SimpleIamServerConstant.SESSION_COOKIE_NAME + "=" + jsessionId);
        }
        int status = connection.getResponseCode();
        String location = connection.getHeaderField("Location");
        String sessionCookie = sessionCookieOf(connection);
        String body = status < 400 ? readBody(connection) : "";
        return new Response(status, location, sessionCookie, body);
    }

    private static String sessionCookieOf(HttpURLConnection connection) {
        for (Map.Entry<String, List<String>> entry : connection.getHeaderFields().entrySet()) {
            if (!"Set-Cookie".equalsIgnoreCase(entry.getKey())) {
                continue;
            }
            for (String value : entry.getValue()) {
                if (value.startsWith(SimpleIamServerConstant.SESSION_COOKIE_NAME + "=")) {
                    return value.split(";", 2)[0].substring(SESSION_COOKIE_NAME_LENGTH);
                }
            }
        }
        return null;
    }

    private static String readBody(HttpURLConnection connection) throws Exception {
        try (InputStream input = connection.getInputStream()) {
            if (input == null) {
                return "";
            }
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int read;
            while ((read = input.read(chunk)) >= 0) {
                buffer.write(chunk, 0, read);
            }
            return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    @AfterEach
    void cleanup() {
        Optional<IamUserEntity> provisioned = userRepository.findByUsername(OIDC_FIXTURE_USERNAME);
        provisioned.ifPresent(userRepository::delete);
        redisTokenRepository.deleteExternalLoginFailure("oidc", "oidc");
        redisTokenRepository.deleteLoginFailure(OIDC_FIXTURE_USERNAME);
    }

    @Test
    @DisplayName("A 实例 authorize 落 Redis state，B 实例 callback 消费完成登录（跨实例 OIDC）")
    void authorizeOnACompletesLoginOnBViaSharedPendingState() throws Exception {
        Response authorize = get("http://localhost:" + portA + AUTHORIZE_PATH, null);
        assertEquals(200, authorize.status, "A 实例 authorize 必须成功：" + authorize.body);
        assertNotNull(authorize.sessionCookie, "authorize 应建立 spring-session 会话并下发 JSESSIONID");
        String authorizeUrl = JsonPath.read(authorize.body, "$.authorizeUrl");
        String state = authorizeUrl.substring(
                authorizeUrl.lastIndexOf("state=") + "state=".length());
        state = state.contains("&") ? state.substring(0, state.indexOf('&')) : state;

        // 装配形态实证：pendingState 必须落共享 Redis（内存分支下两实例 store 互不可见，
        // B 消费必失败）——直接读 Redis key 比 bean 断言更硬核
        String pendingKey = SimpleIamOidcAdapterConstant.PENDING_KEY_PREFIX + state;
        assertNotNull(redisRouteTemplate.stringTemplateByKey(pendingKey)
                        .opsForValue().get(pendingKey),
                "authorize 后 pendingState 应落 Redis 共享存储：" + pendingKey);

        Map<String, String> callback = KeycloakLoginFormSupport.completeLogin(
                authorizeUrl, OIDC_FIXTURE_USERNAME, oidcUserPassword);
        assertEquals(state, callback.get("state"), "回传 state 应与 A 实例授权时一致");

        Response callbackOnB = get("http://localhost:" + portB + CALLBACK_PATH
                        + "?code=" + callback.get("code") + "&state=" + callback.get("state"),
                authorize.sessionCookie);
        assertEquals(302, callbackOnB.status, "B 实例 callback 应 302 跳应用首页，实际=" + callbackOnB.status);
        // 真实 Tomcat 下 RedirectView 生成绝对 URL（MockMvc 的 redirectedUrl 相对形态不适用）
        assertTrue(callbackOnB.location.endsWith("/app/"),
                "登录成功应跳应用首页（B 消费了 A 写入的共享 state），实际=" + callbackOnB.location);

        Response meOnB = get("http://localhost:" + portB + ME_PATH, authorize.sessionCookie);
        assertEquals(200, meOnB.status, "B 实例 /me 应识别 A 会话上的 OIDC 登录");
        assertTrue(meOnB.body.contains(OIDC_FIXTURE_USERNAME), "B 侧 /me 必须还原登录用户：" + meOnB.body);

        Response meOnA = get("http://localhost:" + portA + ME_PATH, authorize.sessionCookie);
        assertEquals(200, meOnA.status, "A 实例应与 B 共享同一登录会话");
        assertTrue(meOnA.body.contains(OIDC_FIXTURE_USERNAME));

        Optional<IamUserEntity> saved = userRepository.findByUsername(OIDC_FIXTURE_USERNAME);
        assertTrue(saved.isPresent(), "跨实例 OIDC 首登应 JIT 开号");
        assertEquals("oidc", saved.get().getIdentitySource());
        assertNotNull(saved.get().getExternalId(), "externalId 应取自 ID token sub");

        log.info("OIDC 跨实例登录验证通过：authorize@{} → callback@{}", portA, portB);
    }

    private static final class Response {

        final int status;
        final String location;
        final String sessionCookie;
        final String body;

        Response(int status, String location, String sessionCookie, String body) {
            this.status = status;
            this.location = location;
            this.sessionCookie = sessionCookie;
            this.body = body;
        }
    }
}
