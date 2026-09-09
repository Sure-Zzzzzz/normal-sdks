package io.github.surezzzzzz.sdk.auth.iam.server.test.cases;

import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.resource.request.CreateResourceVerificationClientRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.resource.response.ResourceVerificationClientSecretResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.trustedapplication.request.CreateTrustedApplicationClientRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.trustedapplication.request.CreateTrustedApplicationRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.user.request.CreateUserRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamApplicationAuthorizationEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamUserEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.event.*;
import io.github.surezzzzzz.sdk.auth.iam.server.exception.SimpleIamServerException;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamApplicationAuthorizationRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamUserRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.service.IamResourceVerificationClientService;
import io.github.surezzzzzz.sdk.auth.iam.server.service.RefreshTokenFamilyService;
import io.github.surezzzzzz.sdk.auth.iam.server.service.TrustedApplicationService;
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
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IAM Token 生命周期事件发布测试（DESIGN.iam-token-event-predefine.md）。
 *
 * <p>覆盖授权码流程颁发事件、受控验证终审事件（成功 / 失败）；
 * 事件仅携带非敏感元数据，token 原文不得进入审计语义字段。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(
        classes = SimpleIamServerTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@Import(IamTokenEventPublishTest.TokenEventCapture.class)
class IamTokenEventPublishTest {

    private static final String PASSWORD = "Admin@1234";
    private static final long AWAIT_TIMEOUT_MS = 15_000;

    private final String suffix = UUID.randomUUID().toString().substring(0, 8);
    private final String username = "token-event-" + suffix;
    private final String applicationCode = "token-event-app-" + suffix;
    private final String oauthClientId = "token-event-client-" + suffix;
    private final String oauthClientSecret = "token-event-secret-" + suffix;
    private final String verificationClientId = "token-event-vc-" + suffix;
    private final String redirectUri = "https://token-event.example.test/callback";
    private final RestTemplate restTemplate = restTemplate();

    @LocalServerPort
    private int port;
    @Autowired
    private UserService userService;
    @Autowired
    private IamUserRepository userRepository;
    @Autowired
    private IamApplicationAuthorizationRepository applicationAuthorizationRepository;
    @Autowired
    private TrustedApplicationService trustedApplicationService;
    @Autowired
    private IamResourceVerificationClientService verificationClientService;
    @Autowired
    private RefreshTokenFamilyService refreshTokenFamilyService;
    @Autowired
    private RegisteredClientRepository registeredClientRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private TokenEventCapture eventCapture;

    private Long userId;
    private Long applicationId;
    private String verificationClientSecret;

    @BeforeEach
    void prepare() throws Exception {
        CreateUserRequest userRequest = new CreateUserRequest();
        userRequest.setUsername(username);
        userRequest.setPassword(PASSWORD);
        userRequest.setDisplayName(username);
        userRequest.setEmail(username + "@example.test");
        userId = userService.createUser(userRequest).getId();

        CreateTrustedApplicationClientRequest client = new CreateTrustedApplicationClientRequest();
        client.setClientId(oauthClientId);
        client.setClientName("Token Event Client");
        client.setClientType("CONFIDENTIAL");
        client.setClientSecret(oauthClientSecret);
        client.setRequireConsent(true);
        client.setRedirectUris(Collections.singletonList(redirectUri));
        client.setScopes(java.util.Arrays.asList("openid", "profile"));
        client.setGrantTypes(Collections.singletonList(AuthorizationGrantType.AUTHORIZATION_CODE.getValue()));
        client.setAuthenticationMethods(Collections.singletonList("client_secret_post"));
        CreateTrustedApplicationRequest application = new CreateTrustedApplicationRequest();
        application.setApplicationCode(applicationCode);
        application.setApplicationName("Token Event Application");
        application.setInitialClient(client);
        applicationId = trustedApplicationService.createApplication(application).getApplication().getId();
        grantApplicationAuthorization();

        CreateResourceVerificationClientRequest verificationRequest = new CreateResourceVerificationClientRequest();
        verificationRequest.setClientId(verificationClientId);
        ResourceVerificationClientSecretResponse verificationResponse =
                verificationClientService.createClient(applicationId, verificationRequest);
        verificationClientSecret = verificationResponse.getClientSecret();
        eventCapture.reset();
    }

    @AfterEach
    void cleanup() {
        RegisteredClient client = registeredClientRepository.findByClientId(oauthClientId);
        if (client != null) {
            jdbcTemplate.update("DELETE FROM oauth2_authorization_consent WHERE registered_client_id = ?", client.getId());
            jdbcTemplate.update("DELETE FROM oauth2_authorization WHERE registered_client_id = ?", client.getId());
            jdbcTemplate.update("DELETE FROM oauth2_registered_client WHERE id = ?", client.getId());
        }
        jdbcTemplate.update("DELETE FROM iam_resource_verification_client WHERE client_id = ?", verificationClientId);
        IamUserEntity user = userRepository.findByUsername(username).orElse(null);
        if (user != null) {
            jdbcTemplate.update("DELETE FROM iam_authorize_context WHERE user_id = ?", user.getId());
            jdbcTemplate.update("DELETE FROM iam_consent WHERE user_id = ?", user.getId());
            jdbcTemplate.update("DELETE FROM iam_session WHERE user_id = ?", user.getId());
            jdbcTemplate.update("DELETE FROM iam_refresh_token_family WHERE user_id = ?", user.getId());
            if (applicationId != null) {
                applicationAuthorizationRepository.findByUserIdAndApplicationId(user.getId(), applicationId)
                        .ifPresent(applicationAuthorizationRepository::delete);
            }
            userService.deleteUser(user.getId());
        }
        if (applicationId != null) {
            trustedApplicationService.deleteApplication(applicationId);
        }
    }

    @Test
    @DisplayName("授权码换发 token 后发布 TokenIssuedEvent（非敏感元数据）")
    void tokenIssuedOnAuthorizationCodeFlow() throws Exception {
        String sessionCookie = loginSessionCookie();
        String accessToken = authorizationCodeToken(sessionCookie);

        TokenIssuedEvent event = awaitEvent(TokenIssuedEvent.class,
                item -> username.equals(item.getUsername()));
        assertNotNull(event);
        assertEquals(oauthClientId, event.getClientId());
        assertEquals(String.valueOf(userId), event.getUserId());
        assertEquals(TokenEventType.ISSUED, event.getEventType());
        assertNotNull(event.getExpiresAt());
        assertTrue(event.getScopes().contains("openid"));
        log.info("TokenIssuedEvent 断言完成：username={}, clientId={}", username, oauthClientId);
    }

    @Test
    @DisplayName("受控验证终审发布 TokenVerifiedEvent：成功 active=true，失败 active=false")
    void verifyPublishesVerifiedEvents() throws Exception {
        String sessionCookie = loginSessionCookie();
        String accessToken = authorizationCodeToken(sessionCookie);

        HttpHeaders headers = basicHeaders();
        HttpStatus successStatus = verifyToken(headers, accessToken);
        assertEquals(HttpStatus.OK, successStatus);

        TokenVerifiedEvent successEvent = awaitEvent(TokenVerifiedEvent.class,
                item -> item.isActive() && accessToken.equals(item.getTokenValue()));
        assertNotNull(successEvent);
        assertEquals(verificationClientId, successEvent.getVerificationClientId());
        assertEquals(username, successEvent.getUsername());
        assertEquals(String.valueOf(userId), successEvent.getUserId());

        HttpStatus failureStatus = verifyToken(headers, "no-such-token-" + suffix);
        assertEquals(HttpStatus.UNAUTHORIZED, failureStatus);

        TokenVerifiedEvent failureEvent = awaitEvent(TokenVerifiedEvent.class,
                item -> !item.isActive() && item.getVerificationClientId() != null);
        assertNotNull(failureEvent);
        assertEquals(verificationClientId, failureEvent.getVerificationClientId());
        log.info("TokenVerifiedEvent 成败断言完成：username={}", username);
    }

    @Test
    @DisplayName("吊销与删除链路：revoke 发布 TokenRevokedEvent，禁用用户发布 TokenRemovedEvent")
    void revokeAndRemovePublishEvents() throws Exception {
        String sessionCookie = loginSessionCookie();
        String accessToken = authorizationCodeToken(sessionCookie);

        MultiValueMap<String, String> revokeForm = new LinkedMultiValueMap<>();
        revokeForm.add(OAuth2ParameterNames.TOKEN, accessToken);
        revokeForm.add(OAuth2ParameterNames.CLIENT_ID, oauthClientId);
        revokeForm.add("client_secret", oauthClientSecret);
        HttpHeaders revokeHeaders = new HttpHeaders();
        revokeHeaders.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        ResponseEntity<Map> revokeResponse = exchange(HttpMethod.POST, "/oauth2/revoke",
                new HttpEntity<>(revokeForm, revokeHeaders), Map.class);
        assertEquals(HttpStatus.OK, revokeResponse.getStatusCode());

        TokenRevokedEvent revokedEvent = awaitEvent(TokenRevokedEvent.class,
                item -> accessToken.equals(item.getTokenValue()));
        assertNotNull(revokedEvent);
        assertEquals(TokenEventCause.OAUTH2_REVOKE, revokedEvent.getCause());
        assertEquals(oauthClientId, revokedEvent.getClientId());

        userService.disableUser(userId);
        TokenRemovedEvent removedEvent = awaitEvent(TokenRemovedEvent.class,
                item -> accessToken.equals(item.getTokenValue()));
        assertNotNull(removedEvent);
        assertEquals(username, removedEvent.getUsername());
        log.info("TokenRevokedEvent / TokenRemovedEvent 断言完成：username={}", username);
    }

    @Test
    @DisplayName("Refresh Token 复用检测发布 RefreshTokenReuseDetectedEvent（不携带 token 原文）")
    void refreshTokenReusePublishesEvent() throws Exception {
        String firstToken = "refresh-reuse-first-" + suffix;
        String secondToken = "refresh-reuse-second-" + suffix;
        String familyId = refreshTokenFamilyService
                .createFamily(userId, username, "session-reuse-" + suffix, firstToken).getId();
        refreshTokenFamilyService.rotate(familyId, secondToken);

        assertThrows(SimpleIamServerException.class,
                () -> refreshTokenFamilyService.detectReuseAndRevoke(firstToken));

        RefreshTokenReuseDetectedEvent event = awaitEvent(RefreshTokenReuseDetectedEvent.class,
                item -> familyId.equals(item.getFamilyId()));
        assertNotNull(event);
        assertEquals(TokenEventType.REUSE_DETECTED, event.getEventType());
        assertEquals(TokenEventCause.REFRESH_TOKEN_REUSE, event.getCause());
        assertEquals(username, event.getUsername());
        assertEquals(String.valueOf(userId), event.getUserId());
        assertNull(event.getTokenValue(), "复用检测事件不得携带攻击者输入的 token 原文");
        log.info("RefreshTokenReuseDetectedEvent 断言完成：familyId={}", familyId);
    }

    // ==================== HTTP helpers ====================

    /**
     * 授权码换 token 前置：给测试用户授有效应用授权（token 定制器按授权快照注入 claim，
     * 授权缺失时 token 端点拒绝颁发）。
     */
    private void grantApplicationAuthorization() {
        java.time.Instant now = java.time.Instant.now();
        IamApplicationAuthorizationEntity authorization = new IamApplicationAuthorizationEntity();
        authorization.setUserId(userId);
        authorization.setApplicationId(applicationId);
        authorization.setAdmitted(SimpleIamServerConstant.STATUS_ACTIVE);
        authorization.setRolesJson("[]");
        authorization.setPagePermissionsJson("[]");
        authorization.setApiPermissionsJson("[]");
        authorization.setAuthorizationVersion(1L);
        authorization.setManifestVersion("test-v1");
        authorization.setManifestDigest("test-digest-" + suffix);
        authorization.setStatus(SimpleIamServerConstant.STATUS_ACTIVE);
        authorization.setCreatedAt(now);
        authorization.setUpdatedAt(now);
        applicationAuthorizationRepository.save(authorization);
    }

    private HttpHeaders basicHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String credentials = verificationClientId + ":" + verificationClientSecret;
        headers.set(HttpHeaders.AUTHORIZATION, "Basic "
                + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8)));
        return headers;
    }

    private HttpStatus verifyToken(HttpHeaders headers, String token) {
        ResponseEntity<Map> response = exchange(HttpMethod.POST, "/iam/resource/tokens/verify",
                new HttpEntity<>("{\"token\":\"" + token + "\"}", headers), Map.class);
        return response.getStatusCode();
    }

    private String authorizationCodeToken(String sessionCookie) {
        String authorizePath = UriComponentsBuilder.fromPath("/oauth2/authorize")
                .queryParam(OAuth2ParameterNames.RESPONSE_TYPE, "code")
                .queryParam(OAuth2ParameterNames.CLIENT_ID, oauthClientId)
                .queryParam(OAuth2ParameterNames.REDIRECT_URI, redirectUri)
                .queryParam(OAuth2ParameterNames.SCOPE, "openid profile")
                .queryParam(OAuth2ParameterNames.STATE, "token-event-state")
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
        consentForm.add(OAuth2ParameterNames.CLIENT_ID, oauthClientId);
        consentForm.add(OAuth2ParameterNames.STATE, consentState);
        consentForm.add(OAuth2ParameterNames.SCOPE, "openid");
        consentForm.add(OAuth2ParameterNames.SCOPE, "profile");
        ResponseEntity<Void> consentResponse = exchange(HttpMethod.POST, "/oauth2/authorize",
                new HttpEntity<>(consentForm, consentHeaders), Void.class);
        assertEquals(HttpStatus.FOUND, consentResponse.getStatusCode());
        String authorizationCode = UriComponentsBuilder.fromUri(
                consentResponse.getHeaders().getLocation()).build().getQueryParams().getFirst("code");
        assertNotNull(authorizationCode);

        MultiValueMap<String, String> tokenForm = new LinkedMultiValueMap<>();
        tokenForm.add(OAuth2ParameterNames.GRANT_TYPE, AuthorizationGrantType.AUTHORIZATION_CODE.getValue());
        tokenForm.add(OAuth2ParameterNames.CLIENT_ID, oauthClientId);
        tokenForm.add("client_secret", oauthClientSecret);
        tokenForm.add(OAuth2ParameterNames.CODE, authorizationCode);
        tokenForm.add(OAuth2ParameterNames.REDIRECT_URI, redirectUri);
        HttpHeaders tokenHeaders = new HttpHeaders();
        tokenHeaders.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        ResponseEntity<Map> tokenResponse = exchange(HttpMethod.POST, "/oauth2/token",
                new HttpEntity<>(tokenForm, tokenHeaders), Map.class);
        assertEquals(HttpStatus.OK, tokenResponse.getStatusCode(), String.valueOf(tokenResponse.getBody()));
        return (String) tokenResponse.getBody().get("access_token");
    }

    private String loginSessionCookie() {
        ResponseEntity<Map> csrfResponse = exchange(HttpMethod.GET, "/iam/web/auth/csrf", null, Map.class);
        assertEquals(HttpStatus.OK, csrfResponse.getStatusCode());
        String cookie = sessionCookie(csrfResponse.getHeaders());
        String csrfToken = (String) csrfResponse.getBody().get("token");

        HttpHeaders loginHeaders = cookieHeaders(cookie);
        loginHeaders.setContentType(MediaType.APPLICATION_JSON);
        loginHeaders.set("X-CSRF-TOKEN", csrfToken);
        ResponseEntity<Map> loginResponse = exchange(HttpMethod.POST, "/iam/web/auth/login",
                new HttpEntity<>("{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}",
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

    private <T extends AbstractTokenEvent> T awaitEvent(Class<T> type, Predicate<T> matcher)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + AWAIT_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            for (AbstractTokenEvent event : eventCapture.events) {
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
     * 测试用事件捕获器（按类型过滤断言，latch 不适用多变体场景）
     */
    @Component
    static class TokenEventCapture {

        final List<AbstractTokenEvent> events = new CopyOnWriteArrayList<>();

        @EventListener
        public void onTokenEvent(AbstractTokenEvent event) {
            log.info("捕获 IAM token 事件：type={}, cause={}", event.getEventType(), event.getCause());
            events.add(event);
        }

        void reset() {
            events.clear();
        }
    }
}
