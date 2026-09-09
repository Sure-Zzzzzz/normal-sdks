package io.github.surezzzzzz.sdk.audit.iam.server.test.cases;

import io.github.surezzzzzz.sdk.audit.iam.server.model.ServerIamAuditEventFamily;
import io.github.surezzzzzz.sdk.audit.iam.server.model.ServerIamAuditRecord;
import io.github.surezzzzzz.sdk.audit.iam.server.test.SimpleIamServerAuditListenerTestApplication;
import io.github.surezzzzzz.sdk.audit.iam.server.test.TestServerIamAuditHandler;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.user.request.CreateUserRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamUserEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.event.*;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamUserRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.impl.client.HttpClients;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IAM 审计监听器集成测试。
 *
 * <p>真实 IAM server 环境验证四族事件归一化为统一审计记录：登录链路（controller 无事务，
 * fallbackExecution 消费）与管理面 service（事务提交后消费）两条路径全部覆盖；
 * 统一记录不携带 Token 原文（结构上无该字段）。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(
        classes = SimpleIamServerAuditListenerTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class SimpleIamServerAuditListenerIntegrationTest {

    private static final String PASSWORD = "Admin@1234";
    private static final long AWAIT_TIMEOUT_MS = 15_000;

    private final String suffix = UUID.randomUUID().toString().substring(0, 8);
    private final String username = "audit-it-" + suffix;
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
    private TestServerIamAuditHandler testHandler;

    private Long userId;

    @BeforeEach
    void setUp() {
        testHandler.reset();
    }

    @AfterEach
    void tearDown() {
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
    @DisplayName("登录成功发布 LOGIN_SUCCEEDED 与 SESSION CREATED 统一审计记录")
    void shouldAuditLoginSucceededAndSessionCreated() throws Exception {
        userId = createUser();

        login(username, PASSWORD);

        ServerIamAuditRecord authenticationRecord = awaitRecord(ServerIamAuditEventFamily.AUTHENTICATION,
                AuthenticationEventType.LOGIN_SUCCEEDED.getCode());
        assertEquals(ServerIamAuditEventFamily.AUTHENTICATION, authenticationRecord.getFamily());
        assertEquals(SimpleIamServerConstant.LOGIN_PROVIDER_LOCAL_PASSWORD, authenticationRecord.getProvider());
        assertEquals(username, authenticationRecord.getUsername());
        assertEquals(String.valueOf(userId), authenticationRecord.getUserId());
        assertNotNull(authenticationRecord.getIp());
        assertNull(authenticationRecord.getErrorCode());

        ServerIamAuditRecord sessionRecord = awaitRecord(ServerIamAuditEventFamily.SESSION,
                SessionEventType.CREATED.getCode());
        assertEquals(username, sessionRecord.getUsername());
        assertEquals(String.valueOf(userId), sessionRecord.getUserId());
        assertNotNull(sessionRecord.getSessionId());
        assertEquals(SessionEventCause.UNSPECIFIED.getCode(), sessionRecord.getCause());
        log.info("登录链路审计断言完成：username={}", username);
    }

    @Test
    @DisplayName("登录失败发布 LOGIN_FAILED：errorCode 非空且 userId 为空")
    void shouldAuditLoginFailed() throws Exception {
        userId = createUser();

        HttpStatus status = attemptLogin(username, "wrong-password-" + suffix);
        assertEquals(HttpStatus.UNAUTHORIZED, status);

        ServerIamAuditRecord record = awaitRecord(ServerIamAuditEventFamily.AUTHENTICATION,
                AuthenticationEventType.LOGIN_FAILED.getCode());
        assertEquals(username, record.getUsername());
        assertNull(record.getUserId(), "登录失败且无法定位用户时 userId 必须为空");
        assertNotNull(record.getErrorCode());
        assertNotNull(record.getIp());
        log.info("登录失败审计断言完成：username={}, errorCode={}", username, record.getErrorCode());
    }

    @Test
    @DisplayName("事务内管理操作提交后发布 ADMIN_ACTION 统一审计记录（AFTER_COMMIT 消费）")
    void shouldAuditAdminActionAfterCommit() throws Exception {
        testHandler.reset(ServerIamAuditEventFamily.ADMIN_ACTION, AdminActionType.CREATED.getCode());

        Long createdUserId = createUser();

        ServerIamAuditRecord record = awaitRecord(ServerIamAuditEventFamily.ADMIN_ACTION,
                AdminActionType.CREATED.getCode());
        assertEquals(AdminSubjectType.USER.getCode(), record.getSubjectType());
        assertEquals(String.valueOf(createdUserId), record.getSubjectId());
        assertEquals(username, record.getSubjectName());
        assertNull(record.getOperator(), "service 直调无认证上下文，operator 必须为空");
        assertNotNull(record.getEventTime());
        log.info("管理面审计断言完成：subjectId={}, subjectType={}", record.getSubjectId(), record.getSubjectType());
    }

    // ==================== helpers ====================

    private Long createUser() {
        CreateUserRequest request = new CreateUserRequest();
        request.setUsername(username);
        request.setPassword(PASSWORD);
        request.setDisplayName(username);
        request.setEmail(username + "@example.test");
        return userService.createUser(request).getId();
    }

    private void login(String loginUsername, String loginPassword) {
        ResponseEntity<Map> csrfResponse = exchange(HttpMethod.GET, "/iam/web/auth/csrf", null, Map.class);
        assertEquals(HttpStatus.OK, csrfResponse.getStatusCode());
        String cookie = sessionCookie(csrfResponse.getHeaders());
        String csrfToken = (String) csrfResponse.getBody().get("token");

        HttpHeaders loginHeaders = new HttpHeaders();
        headers(cookie, loginHeaders);
        loginHeaders.set("X-CSRF-TOKEN", csrfToken);
        ResponseEntity<Map> loginResponse = exchange(HttpMethod.POST, "/iam/web/auth/login",
                new HttpEntity<>("{\"username\":\"" + loginUsername + "\",\"password\":\"" + loginPassword + "\"}",
                        loginHeaders), Map.class);
        assertEquals(HttpStatus.OK, loginResponse.getStatusCode());
    }

    private HttpStatus attemptLogin(String loginUsername, String loginPassword) {
        ResponseEntity<Map> csrfResponse = exchange(HttpMethod.GET, "/iam/web/auth/csrf", null, Map.class);
        String cookie = sessionCookie(csrfResponse.getHeaders());
        String csrfToken = (String) csrfResponse.getBody().get("token");

        HttpHeaders loginHeaders = new HttpHeaders();
        headers(cookie, loginHeaders);
        loginHeaders.set("X-CSRF-TOKEN", csrfToken);
        ResponseEntity<Map> loginResponse = exchange(HttpMethod.POST, "/iam/web/auth/login",
                new HttpEntity<>("{\"username\":\"" + loginUsername + "\",\"password\":\"" + loginPassword + "\"}",
                        loginHeaders), Map.class);
        return loginResponse.getStatusCode();
    }

    private void headers(String cookie, HttpHeaders target) {
        target.set(HttpHeaders.COOKIE, cookie);
        target.setContentType(MediaType.APPLICATION_JSON);
    }

    private <T> ResponseEntity<T> exchange(HttpMethod method, String path, HttpEntity<?> request, Class<T> type) {
        HttpEntity<?> actualRequest = request == null ? HttpEntity.EMPTY : request;
        return restTemplate.exchange(URI.create("http://localhost:" + port + path), method, actualRequest, type);
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
        throw new IllegalStateException("响应未携带 JSESSIONID 会话");
    }

    private ServerIamAuditRecord awaitRecord(ServerIamAuditEventFamily family, String eventType)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + AWAIT_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            for (ServerIamAuditRecord record : testHandler.records) {
                if (record.getFamily() == family && eventType.equals(record.getEventType())) {
                    return record;
                }
            }
            Thread.sleep(50);
        }
        assertTrue(false, "超时未收到审计记录：family=" + family + ", eventType=" + eventType);
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
}
