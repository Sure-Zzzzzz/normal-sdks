package io.github.surezzzzzz.sdk.auth.iam.server.test.cases;

import io.github.surezzzzzz.sdk.auth.iam.server.configuration.SimpleIamServerProperties;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.trustedapplication.request.CreateTrustedApplicationClientRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.trustedapplication.request.CreateTrustedApplicationRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.trustedapplication.response.TrustedApplicationResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.user.request.CreateUserRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamApplicationAuthorizationEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamUserEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.event.RefreshTokenReuseDetectedEvent;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamApplicationAuthorizationRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamUserRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.service.TrustedApplicationService;
import io.github.surezzzzzz.sdk.auth.iam.server.service.UserService;
import io.github.surezzzzzz.sdk.auth.iam.server.test.SimpleIamServerTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.impl.client.HttpClients;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Refresh Token 轮换 / 重放族吊销 / 登出联动 / TTL 接线端到端测试。
 *
 * <p>批次一主动防线的行为验收：授权码流程签发的 refresh token 一次性使用
 * （{@code reuseRefreshTokens=false}），旧值重放触发族吊销（当前值同时失效）
 * 并发布 {@link RefreshTokenReuseDetectedEvent}；登出随会话联动失效；
 * client tokenSettings 的 TTL 取自 iam.server.token 配置。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(
        classes = SimpleIamServerTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@Import(RefreshTokenRotationEndToEndTest.ReuseEventCapture.class)
class RefreshTokenRotationEndToEndTest {

    private final String suffix = UUID.randomUUID().toString().substring(0, 8);
    private final String username = "refresh-rotation-" + suffix;
    private final String applicationCode = "refresh-rotation-app-" + suffix;
    private final String clientId = "refresh-rotation-client-" + suffix;
    private final String clientSecret = "rotation-secret-12345";
    private final String redirectUri = "https://rotation.example.test/callback";
    private final RestTemplate restTemplate = restTemplate();
    private Long applicationId;
    @LocalServerPort
    private int port;
    @Autowired
    private RegisteredClientRepository registeredClientRepository;
    @Autowired
    private UserService userService;
    @Autowired
    private IamUserRepository userRepository;
    @Autowired
    private TrustedApplicationService trustedApplicationService;
    @Autowired
    private IamApplicationAuthorizationRepository applicationAuthorizationRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private ReuseEventCapture reuseEvents;

    @AfterEach
    void cleanup() {
        IamUserEntity user = userRepository.findByUsername(username).orElse(null);
        RegisteredClient client = registeredClientRepository.findByClientId(clientId);
        if (client != null) {
            jdbcTemplate.update("DELETE FROM oauth2_authorization_consent WHERE registered_client_id = ?", client.getId());
            jdbcTemplate.update("DELETE FROM oauth2_authorization WHERE registered_client_id = ?", client.getId());
            jdbcTemplate.update("DELETE FROM oauth2_registered_client WHERE id = ?", client.getId());
        }
        if (user != null) {
            jdbcTemplate.update("DELETE FROM iam_refresh_token_family WHERE user_id = ?", user.getId());
            jdbcTemplate.update("DELETE FROM iam_authorize_context WHERE user_id = ?", user.getId());
            jdbcTemplate.update("DELETE FROM iam_consent WHERE user_id = ?", user.getId());
            jdbcTemplate.update("DELETE FROM iam_session WHERE user_id = ?", user.getId());
            if (applicationId != null) {
                applicationAuthorizationRepository.findByUserIdAndApplicationId(user.getId(), applicationId)
                        .ifPresent(applicationAuthorizationRepository::delete);
            }
            userService.deleteUser(user.getId());
        }
        if (applicationId != null) {
            trustedApplicationService.deleteApplication(applicationId);
            applicationId = null;
        }
    }

    @Test
    @DisplayName("refresh grant 必须轮换：新值 != 旧值，旧值再用 invalid_grant；TTL 接线自配置")
    void refreshGrantRotatesAndTakesEffect() {
        IamUserEntity user = createUserWithClient();
        Map<String, Object> tokens = authorizationCodeTokens(loginSessionCookie());

        assertNotNull(tokens.get("refresh_token"), "授权码流程必须签发 refresh token");
        assertEquals(3600, ((Number) tokens.get("expires_in")).intValue(),
                "access token TTL 应取 iam.server.token.access-expires-in 配置值（测试 yml=3600）");

        RegisteredClient persisted = registeredClientRepository.findByClientId(clientId);
        assertNotNull(persisted);
        assertEquals(Duration.ofSeconds(3600), persisted.getTokenSettings().getAccessTokenTimeToLive());
        assertEquals(Duration.ofSeconds(604800), persisted.getTokenSettings().getRefreshTokenTimeToLive());
        assertFalse(persisted.getTokenSettings().isReuseRefreshTokens(), "refresh token 必须一次性使用");

        Map<String, Object> refreshed = refreshTokens((String) tokens.get("refresh_token"));
        assertEquals(200, refreshed.get("status"), String.valueOf(refreshed));
        assertNotNull(refreshed.get("refresh_token"), "轮换开启后 refresh 响应必须携带新 refresh token");
        assertNotEquals(tokens.get("refresh_token"), refreshed.get("refresh_token"),
                "refresh grant 必须轮换签发新 refresh token");

        Map<String, Object> replayOld = refreshTokens((String) tokens.get("refresh_token"));
        assertEquals(400, replayOld.get("status"), String.valueOf(replayOld));
        assertEquals("invalid_grant", replayOld.get("error"), "已轮换的旧 refresh token 必须以 invalid_grant 拒绝");
        log.info("轮换断言完成：username={}", username);
    }

    @Test
    @DisplayName("旧值重放触发族吊销：当前 token 同失效并发布重放事件")
    void replayRevokesFamilyAndPublishesEvent() {
        createUserWithClient();
        Map<String, Object> tokens = authorizationCodeTokens(loginSessionCookie());
        String firstRefreshToken = (String) tokens.get("refresh_token");

        Map<String, Object> second = refreshTokens(firstRefreshToken);
        assertEquals(200, second.get("status"), String.valueOf(second));
        String secondRefreshToken = (String) second.get("refresh_token");

        Map<String, Object> third = refreshTokens(secondRefreshToken);
        assertEquals(200, third.get("status"), String.valueOf(third));
        String thirdRefreshToken = (String) third.get("refresh_token");

        int eventsBefore = reuseEvents.events.size();
        Map<String, Object> replay = refreshTokens(secondRefreshToken);
        assertEquals(400, replay.get("status"), String.valueOf(replay));
        assertEquals("invalid_grant", replay.get("error"), "重放已作废 token 必须以 invalid_grant 拒绝");
        assertTrue(reuseEvents.events.size() > eventsBefore,
                "重放检测必须发布 RefreshTokenReuseDetectedEvent");

        Map<String, Object> currentAfterReplay = refreshTokens(thirdRefreshToken);
        assertEquals(400, currentAfterReplay.get("status"), String.valueOf(currentAfterReplay));
        assertEquals("invalid_grant", currentAfterReplay.get("error"),
                "族吊销后当前 token 必须同步失效（失败关闭）");
        log.info("重放族吊销断言完成：username={}", username);
    }

    @Test
    @DisplayName("TTL 默认值锚定：漏配时 access=30 分钟 / refresh=10 小时")
    void tokenTtlDefaultsAnchorUnconfiguredDeployment() {
        assertEquals(1800, SimpleIamServerConstant.DEFAULT_ACCESS_TOKEN_EXPIRES_IN,
                "access TTL 默认拍板值 30 分钟");
        assertEquals(36000, SimpleIamServerConstant.DEFAULT_REFRESH_TOKEN_EXPIRES_IN,
                "refresh TTL 默认拍板值 10 小时");
        SimpleIamServerProperties defaults = new SimpleIamServerProperties();
        assertEquals(SimpleIamServerConstant.DEFAULT_ACCESS_TOKEN_EXPIRES_IN,
                defaults.getToken().getAccessExpiresIn(), "TokenConfig 漏配时锚定常量默认");
        assertEquals(SimpleIamServerConstant.DEFAULT_REFRESH_TOKEN_EXPIRES_IN,
                defaults.getToken().getRefreshExpiresIn(), "TokenConfig 漏配时锚定常量默认");
        log.info("TTL 默认值锚定完成：1800s / 36000s");
    }

    @Test
    @DisplayName("登出后 refresh 随会话联动失效")
    void logoutRevokesRefreshToken() {
        createUserWithClient();
        String sessionCookie = loginSessionCookie();
        Map<String, Object> tokens = authorizationCodeTokens(sessionCookie);

        ResponseEntity<Map> csrfResponse = exchange(HttpMethod.GET, "/iam/web/auth/csrf",
                new HttpEntity<>(cookieHeaders(sessionCookie)), Map.class);
        assertEquals(HttpStatus.OK, csrfResponse.getStatusCode());
        HttpHeaders logoutHeaders = cookieHeaders(sessionCookie);
        logoutHeaders.set("X-CSRF-TOKEN", (String) csrfResponse.getBody().get("token"));
        ResponseEntity<Void> logoutResponse = exchange(HttpMethod.POST, "/iam/web/auth/logout",
                new HttpEntity<>(logoutHeaders), Void.class);
        assertEquals(HttpStatus.NO_CONTENT, logoutResponse.getStatusCode());

        Map<String, Object> afterLogout = refreshTokens((String) tokens.get("refresh_token"));
        assertEquals(400, afterLogout.get("status"), String.valueOf(afterLogout));
        assertEquals("invalid_grant", afterLogout.get("error"), "登出后 refresh 必须以 invalid_grant 拒绝");
        log.info("登出联动断言完成：username={}", username);
    }

    private IamUserEntity createUserWithClient() {
        CreateUserRequest request = new CreateUserRequest();
        request.setUsername(username);
        request.setPassword("Admin@1234");
        request.setDisplayName(username);
        request.setEmail(username + "@example.test");
        IamUserEntity user = userService.createUser(request);

        CreateTrustedApplicationClientRequest client = new CreateTrustedApplicationClientRequest();
        client.setClientId(clientId);
        client.setClientName("Rotation Confidential Client");
        client.setClientType("CONFIDENTIAL");
        client.setClientSecret(clientSecret);
        client.setRequireConsent(true);
        client.setRedirectUris(Collections.singletonList(redirectUri));
        client.setScopes(Arrays.asList("openid", "profile"));
        client.setGrantTypes(Arrays.asList(
                AuthorizationGrantType.AUTHORIZATION_CODE.getValue(),
                AuthorizationGrantType.REFRESH_TOKEN.getValue()));
        client.setAuthenticationMethods(Collections.singletonList("client_secret_post"));

        CreateTrustedApplicationRequest application = new CreateTrustedApplicationRequest();
        application.setApplicationCode(applicationCode);
        application.setApplicationName("Rotation Application");
        application.setInitialClient(client);
        TrustedApplicationResponse created = trustedApplicationService.createApplication(application).getApplication();
        applicationId = created.getId();
        applicationAuthorizationRepository.save(createApplicationAuthorization(user.getId(), applicationId));
        return user;
    }

    private IamApplicationAuthorizationEntity createApplicationAuthorization(Long userId, Long trustedApplicationId) {
        Instant now = Instant.now();
        IamApplicationAuthorizationEntity authorization = new IamApplicationAuthorizationEntity();
        authorization.setUserId(userId);
        authorization.setApplicationId(trustedApplicationId);
        authorization.setAdmitted(SimpleIamServerConstant.STATUS_ACTIVE);
        authorization.setRolesJson("[]");
        authorization.setPagePermissionsJson("[]");
        authorization.setApiPermissionsJson("[]");
        authorization.setAuthorizationVersion(1L);
        authorization.setManifestVersion("test-v1");
        authorization.setManifestDigest("test-digest");
        authorization.setStatus(SimpleIamServerConstant.STATUS_ACTIVE);
        authorization.setCreatedAt(now);
        authorization.setUpdatedAt(now);
        return authorization;
    }

    private String loginSessionCookie() {
        ResponseEntity<Map> csrfResponse = exchange(HttpMethod.GET, "/iam/web/auth/csrf", null, Map.class);
        assertEquals(HttpStatus.OK, csrfResponse.getStatusCode());
        String sessionCookie = sessionCookie(csrfResponse.getHeaders());
        String csrfToken = (String) csrfResponse.getBody().get("token");

        HttpHeaders loginHeaders = cookieHeaders(sessionCookie);
        loginHeaders.setContentType(MediaType.APPLICATION_JSON);
        loginHeaders.set("X-CSRF-TOKEN", csrfToken);
        ResponseEntity<Map> loginResponse = exchange(HttpMethod.POST, "/iam/web/auth/login",
                new HttpEntity<>("{\"username\":\"" + username + "\",\"password\":\"Admin@1234\"}",
                        loginHeaders),
                Map.class);
        assertEquals(HttpStatus.OK, loginResponse.getStatusCode(), String.valueOf(loginResponse.getBody()));
        return sessionCookie(loginResponse.getHeaders(), sessionCookie);
    }

    private Map<String, Object> authorizationCodeTokens(String sessionCookie) {
        String authorizePath = UriComponentsBuilder.fromPath("/oauth2/authorize")
                .queryParam(OAuth2ParameterNames.RESPONSE_TYPE, "code")
                .queryParam(OAuth2ParameterNames.CLIENT_ID, clientId)
                .queryParam(OAuth2ParameterNames.REDIRECT_URI, redirectUri)
                .queryParam(OAuth2ParameterNames.SCOPE, "openid profile")
                .queryParam(OAuth2ParameterNames.STATE, "rotation-state")
                .build()
                .encode()
                .toUriString();
        ResponseEntity<Void> authorizeResponse = exchange(HttpMethod.GET, authorizePath,
                new HttpEntity<>(cookieHeaders(sessionCookie)), Void.class);
        assertEquals(HttpStatus.FOUND, authorizeResponse.getStatusCode());
        URI consentLocation = authorizeResponse.getHeaders().getLocation();
        assertNotNull(consentLocation);
        assertEquals("/consent", consentLocation.getPath());
        String consentState = UriUtils.decode(
                UriComponentsBuilder.fromUri(consentLocation).build().getQueryParams().getFirst("state"),
                StandardCharsets.UTF_8);

        HttpHeaders consentHeaders = cookieHeaders(sessionCookie);
        consentHeaders.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        MultiValueMap<String, String> consentForm = new LinkedMultiValueMap<>();
        consentForm.add(OAuth2ParameterNames.CLIENT_ID, clientId);
        consentForm.add(OAuth2ParameterNames.STATE, consentState);
        consentForm.add(OAuth2ParameterNames.SCOPE, "openid");
        consentForm.add(OAuth2ParameterNames.SCOPE, "profile");
        ResponseEntity<Void> consentResponse = exchange(HttpMethod.POST, "/oauth2/authorize",
                new HttpEntity<>(consentForm, consentHeaders), Void.class);
        assertEquals(HttpStatus.FOUND, consentResponse.getStatusCode());
        String authorizationCode = UriComponentsBuilder.fromUri(consentResponse.getHeaders().getLocation())
                .build().getQueryParams().getFirst("code");
        assertNotNull(authorizationCode);

        MultiValueMap<String, String> tokenForm = new LinkedMultiValueMap<>();
        tokenForm.add(OAuth2ParameterNames.GRANT_TYPE, AuthorizationGrantType.AUTHORIZATION_CODE.getValue());
        tokenForm.add(OAuth2ParameterNames.CLIENT_ID, clientId);
        tokenForm.add("client_secret", clientSecret);
        tokenForm.add(OAuth2ParameterNames.CODE, authorizationCode);
        tokenForm.add(OAuth2ParameterNames.REDIRECT_URI, redirectUri);
        HttpHeaders tokenHeaders = new HttpHeaders();
        tokenHeaders.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        ResponseEntity<Map> tokenResponse = exchange(HttpMethod.POST, "/oauth2/token",
                new HttpEntity<>(tokenForm, tokenHeaders), Map.class);
        assertEquals(HttpStatus.OK, tokenResponse.getStatusCode(), String.valueOf(tokenResponse.getBody()));
        assertTrue(tokenResponse.getBody().containsKey("refresh_token"));
        return tokenResponse.getBody();
    }

    private Map<String, Object> refreshTokens(String refreshToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add(OAuth2ParameterNames.GRANT_TYPE, AuthorizationGrantType.REFRESH_TOKEN.getValue());
        form.add(OAuth2ParameterNames.CLIENT_ID, clientId);
        form.add("client_secret", clientSecret);
        form.add(OAuth2ParameterNames.REFRESH_TOKEN, refreshToken);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        ResponseEntity<Map> response = exchange(HttpMethod.POST, "/oauth2/token",
                new HttpEntity<>(form, headers), Map.class);
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("status", response.getStatusCode().value());
        if (response.getBody() != null) {
            result.putAll(response.getBody());
        }
        return result;
    }

    private RestTemplate restTemplate() {
        RestTemplate template = new RestTemplate(new HttpComponentsClientHttpRequestFactory(HttpClients.custom()
                .disableRedirectHandling()
                .disableCookieManagement()
                .build()));
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
        return sessionCookie(headers, null);
    }

    private String sessionCookie(HttpHeaders headers, String fallback) {
        List<String> cookies = headers.get(HttpHeaders.SET_COOKIE);
        if (cookies != null) {
            for (String cookie : cookies) {
                if (cookie.startsWith("JSESSIONID=")) {
                    return cookie.substring(0, cookie.indexOf(';'));
                }
            }
        }
        assertNotNull(fallback, "响应必须建立 JSESSIONID");
        return fallback;
    }

    /**
     * 测试用重放事件捕获器：token 端点在 Tomcat 工作线程发布事件，
     * ApplicationEvents 按 ThreadLocal 只录注册线程会漏记，故以上下文内监听器直收。
     */
    @Component
    static class ReuseEventCapture {

        final List<RefreshTokenReuseDetectedEvent> events = new CopyOnWriteArrayList<>();

        @EventListener
        public void onReuseDetected(RefreshTokenReuseDetectedEvent event) {
            events.add(event);
        }
    }
}
