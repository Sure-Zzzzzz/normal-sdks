package io.github.surezzzzzz.sdk.auth.iam.server.test.cases;

import io.github.surezzzzzz.sdk.auth.iam.server.configuration.SimpleIamServerProperties;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.user.request.CreateUserRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamUserEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.event.*;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamUserRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.service.UserService;
import io.github.surezzzzzz.sdk.auth.iam.server.test.SimpleIamServerTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.impl.client.HttpClients;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.http.*;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IAM 认证与会话生命周期事件发布测试（DESIGN.iam-audit-event-predefine.md）。
 *
 * <p>覆盖本地密码登录成功 / 失败、失败锁定、登出、重登踢旧会话五条链路；
 * 事件仅携带非敏感元数据（provider / username / ip / userAgent / errorCode / failureCount）。
 *
 * <p>本类关闭登录渐进验证码：锁定用例为裸试循环到失败上限，默认启用下第 3 次起
 * 即被 CAPTCHA_REQUIRED 拦截导致计数不可达；验证码行为由 IamWebAuthCaptchaApiTest 专项覆盖。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(
        classes = SimpleIamServerTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@Import(IamAuthenticationEventPublishTest.IamEventCapture.class)
@TestPropertySource(properties = "io.github.surezzzzzz.sdk.auth.iam.server.captcha.enabled=false")
class IamAuthenticationEventPublishTest {

    private static final String PASSWORD = "Admin@1234";
    private static final long AWAIT_TIMEOUT_MS = 15_000;

    private final String suffix = UUID.randomUUID().toString().substring(0, 8);
    private final String username = "auth-event-" + suffix;
    private final RestTemplate restTemplate = restTemplate();

    @LocalServerPort
    private int port;
    @Autowired
    private UserService userService;
    @Autowired
    private IamUserRepository userRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private SimpleIamServerProperties properties;
    @Autowired
    private IamEventCapture eventCapture;

    private Long userId;

    @BeforeEach
    void prepare() {
        CreateUserRequest userRequest = new CreateUserRequest();
        userRequest.setUsername(username);
        userRequest.setPassword(PASSWORD);
        userRequest.setDisplayName(username);
        userRequest.setEmail(username + "@example.test");
        userId = userService.createUser(userRequest).getId();
        eventCapture.reset();
    }

    @AfterEach
    void cleanup() {
        IamUserEntity user = userRepository.findByUsername(username).orElse(null);
        if (user != null) {
            jdbcTemplate.update("DELETE FROM iam_authorize_context WHERE user_id = ?", user.getId());
            jdbcTemplate.update("DELETE FROM iam_consent WHERE user_id = ?", user.getId());
            jdbcTemplate.update("DELETE FROM iam_session WHERE user_id = ?", user.getId());
            jdbcTemplate.update("DELETE FROM iam_refresh_token_family WHERE user_id = ?", user.getId());
            userService.deleteUser(user.getId());
        }
    }

    @Test
    @DisplayName("本地密码登录成功发布 LOGIN_SUCCEEDED 与 SESSION CREATED")
    void loginSuccessPublishesEvents() throws Exception {
        String sessionCookie = loginSessionCookie(username, PASSWORD);

        AuthenticationEvent authenticationEvent = awaitEvent(AuthenticationEvent.class,
                item -> item.getEventType() == AuthenticationEventType.LOGIN_SUCCEEDED
                        && username.equals(item.getUsername()));
        assertNotNull(authenticationEvent);
        assertEquals(SimpleIamServerConstant.LOGIN_PROVIDER_LOCAL_PASSWORD, authenticationEvent.getProvider());
        assertEquals(userId, authenticationEvent.getUserId());
        assertNotNull(authenticationEvent.getIp());
        assertNull(authenticationEvent.getErrorCode());

        SessionLifecycleEvent sessionEvent = awaitEvent(SessionLifecycleEvent.class,
                item -> item.getEventType() == SessionEventType.CREATED
                        && userId.equals(item.getUserId()));
        assertNotNull(sessionEvent);
        assertEquals(username, sessionEvent.getUsername());
        assertNotNull(sessionEvent.getSessionId());
        log.info("LOGIN_SUCCEEDED / SESSION CREATED 断言完成：username={}", username);
    }

    @Test
    @DisplayName("密码错误发布 LOGIN_FAILED：携带 username 与 errorCode，userId 为空")
    void loginFailurePublishesEvent() throws Exception {
        HttpStatus status = attemptLogin(username, "wrong-password-" + suffix);
        assertEquals(HttpStatus.UNAUTHORIZED, status);

        AuthenticationEvent event = awaitEvent(AuthenticationEvent.class,
                item -> item.getEventType() == AuthenticationEventType.LOGIN_FAILED
                        && username.equals(item.getUsername()));
        assertNotNull(event);
        assertEquals(SimpleIamServerConstant.LOGIN_PROVIDER_LOCAL_PASSWORD, event.getProvider());
        assertNull(event.getUserId());
        assertNotNull(event.getErrorCode());
        assertNotNull(event.getIp());
        log.info("LOGIN_FAILED 断言完成：username={}, errorCode={}", username, event.getErrorCode());
    }

    @Test
    @DisplayName("连续失败达到上限发布 ACCOUNT_LOCKED：携带累计失败次数")
    void lockoutPublishesAccountLocked() throws Exception {
        int maxAttempts = properties.getLogin().getMaxAttempts();
        assertTrue(maxAttempts > 0, "登录失败上限必须已配置");
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            HttpStatus status = attemptLogin(username, "wrong-password-" + suffix);
            assertTrue(status == HttpStatus.UNAUTHORIZED || status == HttpStatus.FORBIDDEN,
                    "失败尝试应被拒绝，实际状态码：" + status);
        }

        AuthenticationEvent event = awaitEvent(AuthenticationEvent.class,
                item -> item.getEventType() == AuthenticationEventType.ACCOUNT_LOCKED
                        && username.equals(item.getUsername()));
        assertNotNull(event);
        assertEquals(SimpleIamServerConstant.LOGIN_PROVIDER_LOCAL_PASSWORD, event.getProvider());
        assertEquals(userId, event.getUserId());
        assertEquals(Integer.valueOf(maxAttempts), event.getFailureCount());
        log.info("ACCOUNT_LOCKED 断言完成：username={}, failureCount={}", username, maxAttempts);
    }

    @Test
    @DisplayName("登出发布 LOGOUT 与 SESSION REVOKED（cause=LOGOUT）")
    void logoutPublishesEvents() throws Exception {
        String sessionCookie = loginSessionCookie(username, PASSWORD);

        // 登录复用请求会话不换 session id，CSRF token 与登录时一致；logout 是写操作须带 token
        ResponseEntity<Map> csrfResponse = exchange(HttpMethod.GET, "/iam/web/auth/csrf",
                new HttpEntity<>(cookieHeaders(sessionCookie)), Map.class);
        assertEquals(HttpStatus.OK, csrfResponse.getStatusCode());
        String csrfToken = (String) csrfResponse.getBody().get("token");

        HttpHeaders logoutHeaders = cookieHeaders(sessionCookie);
        logoutHeaders.setContentType(MediaType.APPLICATION_JSON);
        logoutHeaders.set("X-CSRF-TOKEN", csrfToken);
        ResponseEntity<Map> logoutResponse = exchange(HttpMethod.POST, "/iam/web/auth/logout",
                new HttpEntity<>("{}", logoutHeaders), Map.class);
        assertEquals(HttpStatus.NO_CONTENT, logoutResponse.getStatusCode());

        AuthenticationEvent logoutEvent = awaitEvent(AuthenticationEvent.class,
                item -> item.getEventType() == AuthenticationEventType.LOGOUT
                        && username.equals(item.getUsername()));
        assertNotNull(logoutEvent);
        assertEquals(userId, logoutEvent.getUserId());

        SessionLifecycleEvent revokedEvent = awaitEvent(SessionLifecycleEvent.class,
                item -> item.getEventType() == SessionEventType.REVOKED
                        && userId.equals(item.getUserId())
                        && item.getSessionId() != null);
        assertNotNull(revokedEvent);
        assertEquals(SessionEventCause.LOGOUT, revokedEvent.getCause());
        log.info("LOGOUT / SESSION REVOKED 断言完成：username={}", username);
    }

    @Test
    @DisplayName("同一浏览器会话重登踢旧会话发布 SESSION REVOKED（cause=RELOGIN_KICK）")
    void reloginKicksPreviousSession() throws Exception {
        String firstCookie = loginSessionCookie(username, PASSWORD);

        String secondCookie = loginSessionCookie(username, PASSWORD, firstCookie);

        SessionLifecycleEvent kickEvent = awaitEvent(SessionLifecycleEvent.class,
                item -> item.getEventType() == SessionEventType.REVOKED
                        && userId.equals(item.getUserId())
                        && item.getCause() == SessionEventCause.RELOGIN_KICK);
        assertNotNull(kickEvent);
        assertNotNull(kickEvent.getSessionId());
        log.info("SESSION REVOKED（RELOGIN_KICK）断言完成：username={}, secondCookie={}",
                username, secondCookie != null);
    }

    // ==================== HTTP helpers ====================

    private String loginSessionCookie(String loginUsername, String loginPassword) {
        return loginSessionCookie(loginUsername, loginPassword, null);
    }

    private String loginSessionCookie(String loginUsername, String loginPassword, String existingCookie) {
        ResponseEntity<Map> csrfResponse = exchange(HttpMethod.GET, "/iam/web/auth/csrf",
                existingCookie == null ? null : new HttpEntity<>(cookieHeaders(existingCookie)), Map.class);
        assertEquals(HttpStatus.OK, csrfResponse.getStatusCode());
        String cookie = existingCookie != null ? existingCookie : sessionCookie(csrfResponse.getHeaders());
        String csrfToken = (String) csrfResponse.getBody().get("token");

        HttpHeaders loginHeaders = cookieHeaders(cookie);
        loginHeaders.setContentType(MediaType.APPLICATION_JSON);
        loginHeaders.set("X-CSRF-TOKEN", csrfToken);
        ResponseEntity<Map> loginResponse = exchange(HttpMethod.POST, "/iam/web/auth/login",
                new HttpEntity<>("{\"username\":\"" + loginUsername + "\",\"password\":\"" + loginPassword + "\"}",
                        loginHeaders),
                Map.class);
        assertEquals(HttpStatus.OK, loginResponse.getStatusCode());
        String loginCookie = sessionCookie(loginResponse.getHeaders());
        if (loginCookie == null) {
            // iam 登录复用请求会话（establishSession 不换 session id），响应不重发 cookie，沿用请求 cookie
            loginCookie = cookie;
        }
        assertNotNull(loginCookie, "登录必须成功并建立会话");
        return loginCookie;
    }

    /**
     * 直接发起一次登录请求，仅用于必然失败的尝试（失败计数与锁定）；返回原始状态码。
     */
    private HttpStatus attemptLogin(String loginUsername, String loginPassword) {
        ResponseEntity<Map> csrfResponse = exchange(HttpMethod.GET, "/iam/web/auth/csrf", null, Map.class);
        String cookie = sessionCookie(csrfResponse.getHeaders());
        String csrfToken = (String) csrfResponse.getBody().get("token");

        HttpHeaders loginHeaders = cookieHeaders(cookie);
        loginHeaders.setContentType(MediaType.APPLICATION_JSON);
        loginHeaders.set("X-CSRF-TOKEN", csrfToken);
        ResponseEntity<Map> loginResponse = exchange(HttpMethod.POST, "/iam/web/auth/login",
                new HttpEntity<>("{\"username\":\"" + loginUsername + "\",\"password\":\"" + loginPassword + "\"}",
                        loginHeaders),
                Map.class);
        return loginResponse.getStatusCode();
    }

    private <T> ResponseEntity<T> exchange(HttpMethod method, String path, HttpEntity<?> request, Class<T> responseType) {
        HttpEntity<?> actualRequest = request == null ? HttpEntity.EMPTY : request;
        return restTemplate.exchange(URI.create("http://localhost:" + port + path), method, actualRequest, responseType);
    }

    private HttpHeaders cookieHeaders(String sessionCookie) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.COOKIE, sessionCookie);
        return headers;
    }

    private String sessionCookie(HttpHeaders headers) {
        List<String> cookies = headers.get(HttpHeaders.SET_COOKIE);
        if (cookies != null) {
            for (String cookie : cookies) {
                if (cookie.startsWith("JSESSIONID=")) {
                    return cookie.substring(0, cookie.indexOf(';'));
                }
            }
        }
        return null;
    }

    private <T extends AbstractIamEvent> T awaitEvent(Class<T> type, Predicate<T> matcher)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + AWAIT_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            for (AbstractIamEvent event : eventCapture.events) {
                if (type.isInstance(event) && matcher.test(type.cast(event))) {
                    return type.cast(event);
                }
            }
            Thread.sleep(50);
        }
        return null;
    }

    private RestTemplate restTemplate() {
        RestTemplate template = new RestTemplate(new HttpComponentsClientHttpRequestFactory(
                HttpClients.custom().disableRedirectHandling().disableCookieManagement().build()));
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

    /**
     * 测试用事件捕获器（订阅统一根类型，按事件类型与字段断言过滤）
     */
    @Component
    static class IamEventCapture {

        final List<AbstractIamEvent> events = new CopyOnWriteArrayList<>();

        @EventListener
        public void onIamEvent(AbstractIamEvent event) {
            log.info("捕获 IAM 事件：class={}", event.getClass().getSimpleName());
            events.add(event);
        }

        void reset() {
            events.clear();
        }
    }
}
