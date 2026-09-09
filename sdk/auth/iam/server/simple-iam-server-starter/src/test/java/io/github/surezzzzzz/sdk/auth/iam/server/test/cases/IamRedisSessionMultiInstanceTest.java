package io.github.surezzzzzz.sdk.auth.iam.server.test.cases;

import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.user.request.CreateUserRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamRoleEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamUserRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.service.RoleService;
import io.github.surezzzzzz.sdk.auth.iam.server.service.UserService;
import io.github.surezzzzzz.sdk.auth.iam.server.test.SimpleIamServerTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.impl.client.HttpClients;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.boot.web.server.WebServer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.http.*;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.session.data.redis.RedisIndexedSessionRepository;
import org.springframework.session.web.http.SessionRepositoryFilter;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.Map;
import java.util.UUID;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.*;

/**
 * spring-session 多实例互认会话真实双实例测试。
 *
 * <p>核心命题：实例 A 登录建立的会话（Redis），实例 B 凭同一 JSESSIONID cookie 即可识别；
 * B 侧登出后 A 侧会话同步失效。这是去除粘性路由前提的直接验证。</p>
 *
 * <p>实例 B 经 SpringApplication 以随机端口另起，关闭 bootstrap 引导防 A/B 竞争；
 * 两实例共享同一 Redis 数据源与 me（namespace HashTag）。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(
        classes = SimpleIamServerTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class IamRedisSessionMultiInstanceTest {

    private static final String CSRF_PATH = "/iam/web/auth/csrf";
    private static final String LOGIN_PATH = "/iam/web/auth/login";
    private static final String ME_PATH = "/iam/web/auth/me";
    private static final String LOGOUT_PATH = "/iam/web/auth/logout";

    private static ConfigurableApplicationContext contextB;
    private static int portB;
    private final String suffix = UUID.randomUUID().toString().substring(0, 8);
    private final String username = "multi-instance-" + suffix;
    private final RestTemplate restTemplate = restTemplate();
    @LocalServerPort
    private int portA;
    @Autowired
    private UserService userService;

    @Autowired
    private RoleService roleService;

    @Autowired
    private IamUserRepository userRepository;

    @BeforeAll
    static void startInstanceB() {
        contextB = new SpringApplication(SimpleIamServerTestApplication.class)
                .run("--server.port=0",
                        "--io.github.surezzzzzz.sdk.auth.iam.server.bootstrap.enabled=false");
        WebServer webServer = ((WebServerApplicationContext) contextB).getWebServer();
        portB = webServer.getPort();
    }

    @AfterAll
    static void stopInstanceB() {
        if (contextB != null) {
            contextB.close();
        }
    }

    @AfterEach
    void cleanup() {
        userRepository.findByUsername(username).ifPresent(user -> userService.deleteUser(user.getId()));
    }

    @Test
    @DisplayName("A 登录会话在 B 实例识别，B 登出后 A 会话同步失效")
    void sessionEstablishedOnAIsRecognizedOnBAndRevokedGlobally() {
        createUserWithAdminRole();

        ResponseEntity<Map> csrf = exchange(HttpMethod.GET, portA, CSRF_PATH, null, null, Map.class, null);
        assertEquals(200, csrf.getStatusCode().value(), "csrf 端点必须可公开访问");
        String jsessionId = cookieOf(csrf);
        String csrfHeader = (String) csrf.getBody().get("headerName");
        String csrfToken = (String) csrf.getBody().get("token");
        assertNotNull(jsessionId, "csrf 端点必须建立会话");

        ResponseEntity<Map> login = exchange(HttpMethod.POST, portA, LOGIN_PATH, jsessionId,
                headers -> {
                    headers.add(csrfHeader, csrfToken);
                    return headers;
                }, Map.class,
                "{\"username\":\"" + username + "\",\"password\":\"Admin@1234\"}");
        assertEquals(200, login.getStatusCode().value(), "实例 A 登录必须成功");
        jsessionId = cookieOf(login, jsessionId);
        assertNotNull(jsessionId);

        ResponseEntity<String> meOnB = exchange(HttpMethod.GET, portB, ME_PATH, jsessionId, null, String.class, null);
        assertEquals(200, meOnB.getStatusCode().value(), "核心命题：A 建立的会话 B 必须识别");
        assertTrue(meOnB.getBody().contains(username), "B 侧 /me 必须还原登录用户");

        ResponseEntity<String> logoutOnB = exchange(HttpMethod.POST, portB, LOGOUT_PATH, jsessionId,
                headers -> {
                    headers.add(csrfHeader, csrfToken);
                    return headers;
                }, String.class, null);
        assertEquals(204, logoutOnB.getStatusCode().value(), "实例 B 登出必须成功（csrf token 跨实例读回）");

        ResponseEntity<String> meOnA = exchange(HttpMethod.GET, portA, ME_PATH, jsessionId, null, String.class, null);
        assertEquals(401, meOnA.getStatusCode().value(), "B 登出后 A 侧旧 cookie 必须失效");

        log.info("多实例互认会话验证通过：portA={}, portB={}", portA, portB);
    }

    @Test
    @DisplayName("会话 key 落 Redis 且 SessionRepositoryFilter 顺序先于 security 链")
    void sessionStoredInRedisWithFilterOrderGuard() {
        createUserWithAdminRole();

        ResponseEntity<Map> csrf = exchange(HttpMethod.GET, portA, CSRF_PATH, null, null, Map.class, null);
        String jsessionId = cookieOf(csrf);
        assertNotNull(jsessionId, "csrf 端点必须建立会话并回写 cookie");

        RedisIndexedSessionRepository repository = contextB.getBean(RedisIndexedSessionRepository.class);
        org.springframework.session.Session session = repository.findById(sessionIdOf(jsessionId));
        assertNotNull(session, "会话必须落在共享 Redis（B 进程可读）");

        org.springframework.core.annotation.Order order =
                AnnotationUtils.findAnnotation(SessionRepositoryFilter.class, org.springframework.core.annotation.Order.class);
        assertNotNull(order, "SessionRepositoryFilter 必须显式声明顺序");
        assertTrue(order.value() < -100, "SessionRepositoryFilter 必须先于 security 链（-100）执行");

        log.info("会话 Redis 落库与 filter 顺序断言通过");
    }

    private void createUserWithAdminRole() {
        CreateUserRequest request = new CreateUserRequest();
        request.setUsername(username);
        request.setPassword("Admin@1234");
        request.setDisplayName(username);
        Long userId = userService.createUser(request).getId();
        IamRoleEntity adminRole = roleService.getByCode(SimpleIamServerConstant.BUILT_IN_ROLE_IAM_ADMIN);
        roleService.assignRole(userId, adminRole.getId());
    }

    private <T> ResponseEntity<T> exchange(HttpMethod method, int port, String path, String jsessionId,
                                           UnaryOperator<HttpHeaders> headerCustomizer,
                                           Class<T> type, String jsonBody) {
        HttpHeaders headers = new HttpHeaders();
        if (jsessionId != null) {
            headers.add(HttpHeaders.COOKIE, SimpleIamServerConstant.SESSION_COOKIE_NAME + "=" + jsessionId);
        }
        if (headerCustomizer != null) {
            headers = headerCustomizer.apply(headers);
        }
        if (jsonBody != null) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
        return restTemplate.exchange(URI.create("http://localhost:" + port + path), method,
                new HttpEntity<>(jsonBody, headers), type);
    }

    private String cookieOf(ResponseEntity<?> response) {
        return cookieOf(response, null);
    }

    private String cookieOf(ResponseEntity<?> response, String fallback) {
        String cookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        if (cookie != null && cookie.startsWith(SimpleIamServerConstant.SESSION_COOKIE_NAME + "=")) {
            return cookie.split(";", 2)[0].split("=", 2)[1];
        }
        return fallback;
    }

    /**
     * DefaultCookieSerializer 对会话 ID 做 base64 编码，读 Redis 会话前须先还原。
     */
    private String sessionIdOf(String cookieValue) {
        return new String(java.util.Base64.getDecoder().decode(cookieValue),
                java.nio.charset.StandardCharsets.UTF_8);
    }

    private RestTemplate restTemplate() {
        RestTemplate template = new RestTemplate(new HttpComponentsClientHttpRequestFactory(HttpClients.custom()
                .disableRedirectHandling().disableCookieManagement().build()));
        template.setErrorHandler(new ResponseErrorHandler() {
            @Override
            public boolean hasError(org.springframework.http.client.ClientHttpResponse response) {
                return false;
            }

            @Override
            public void handleError(org.springframework.http.client.ClientHttpResponse response) {
            }
        });
        return template;
    }
}
