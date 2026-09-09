package io.github.surezzzzzz.sdk.auth.iam.server.test.cases;

import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.trustedapplication.request.CreateTrustedApplicationClientRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.trustedapplication.request.CreateTrustedApplicationRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.user.request.CreateUserRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamApplicationAuthorizationEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamUserEntity;
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
import org.springframework.http.*;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 用户生命周期与会话吊销端到端测试（DESIGN.user-lifecycle-session-revocation.md）。
 *
 * <p>覆盖：禁用 / 删除 / 重置密码后 Web 会话立即失效；refresh token 随会话生死；
 * 启用恢复仅通过重新登录建立新会话（吊销不可逆）。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(
        classes = SimpleIamServerTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class IamUserLifecycleSessionRevocationTest {

    private static final String ORIGINAL_PASSWORD = "Admin@1234";
    private static final String RESET_PASSWORD = "NewPass@1234";

    private final String suffix = UUID.randomUUID().toString().substring(0, 8);
    private final String username = "lifecycle-" + suffix;
    private final String applicationCode = "lifecycle-app-" + suffix;
    private final String clientId = "lifecycle-client-" + suffix;
    private final String clientSecret = "lifecycle-secret-" + suffix;
    private final String redirectUri = "https://lifecycle.example.test/callback";
    private final RestTemplate restTemplate = restTemplate();
    private Long applicationId;
    @LocalServerPort
    private int port;
    @Autowired
    private UserService userService;
    @Autowired
    private IamUserRepository userRepository;
    @Autowired
    private TrustedApplicationService trustedApplicationService;
    @Autowired
    private IamApplicationAuthorizationRepository applicationAuthorizationRepository;
    @Autowired
    private RegisteredClientRepository registeredClientRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

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
    @DisplayName("禁用用户后其 Web 会话立即失效，旧密码登录被拒")
    void disableRevokesActiveSessionImmediately() {
        IamUserEntity user = createUser();
        String sessionCookie = loginSessionCookie(ORIGINAL_PASSWORD);

        assertEquals(HttpStatus.OK, meStatus(sessionCookie));

        userService.disableUser(user.getId());

        assertEquals(HttpStatus.UNAUTHORIZED, meStatus(sessionCookie));
        assertNull(loginOrNull(ORIGINAL_PASSWORD), "禁用用户不得再通过登录建立新会话");
        log.info("禁用即下线断言完成：username={}", username);
    }

    @Test
    @DisplayName("删除用户前吊销其全部会话，残留会话立即失效")
    void deleteUserRevokesSessionsBeforeRemoval() {
        createUser();
        String sessionCookie = loginSessionCookie(ORIGINAL_PASSWORD);

        assertEquals(HttpStatus.OK, meStatus(sessionCookie));

        IamUserEntity user = userRepository.findByUsername(username).orElse(null);
        assertNotNull(user);
        userService.deleteUser(user.getId());

        assertEquals(HttpStatus.UNAUTHORIZED, meStatus(sessionCookie));
        assertFalse(userRepository.findByUsername(username).isPresent());
        log.info("删除即下线断言完成：username={}", username);
    }

    @Test
    @DisplayName("refresh token 随会话生死：禁用后 refresh 以 invalid_grant 拒绝，启用后新流程恢复")
    void refreshTokenDiesWithSession() {
        IamUserEntity user = createUser();
        String sessionCookie = loginSessionCookie(ORIGINAL_PASSWORD);
        Map<String, Object> tokens = authorizationCodeTokens(sessionCookie);
        assertNotNull(tokens.get("refresh_token"), "authorization_code 流程必须发放 refresh token");

        Map<String, Object> refreshed = refreshTokens((String) tokens.get("refresh_token"));
        assertEquals(HttpStatus.OK.value(), refreshed.get("status"), String.valueOf(refreshed));
        String currentRefreshToken = (String) refreshed.get("refresh_token");
        assertNotEquals(tokens.get("refresh_token"), currentRefreshToken,
                "refresh grant 必须轮换签发新 refresh token（reuseRefreshTokens=false）");

        userService.disableUser(user.getId());

        Map<String, Object> afterDisable = refreshTokens(currentRefreshToken);
        assertEquals(HttpStatus.BAD_REQUEST.value(), afterDisable.get("status"), String.valueOf(afterDisable));
        assertEquals("invalid_grant", afterDisable.get("error"), "会话吊销后 refresh 必须以 invalid_grant 拒绝");

        userService.enableUser(user.getId());
        String freshCookie = loginSessionCookie(ORIGINAL_PASSWORD);
        Map<String, Object> recovered = authorizationCodeTokens(freshCookie, false);
        Map<String, Object> recoveredRefresh = refreshTokens((String) recovered.get("refresh_token"));
        assertEquals(HttpStatus.OK.value(), recoveredRefresh.get("status"), String.valueOf(recoveredRefresh));
        log.info("refresh 随会话生死断言完成：username={}", username);
    }

    @Test
    @DisplayName("管理员重置密码后目标用户全部会话失效，仅新密码可登录")
    void resetPasswordRevokesSessions() {
        IamUserEntity user = createUser();
        String sessionCookie = loginSessionCookie(ORIGINAL_PASSWORD);

        assertEquals(HttpStatus.OK, meStatus(sessionCookie));

        userService.resetPassword(user.getId(), RESET_PASSWORD, "lifecycle-admin");

        assertEquals(HttpStatus.UNAUTHORIZED, meStatus(sessionCookie));
        assertNull(loginOrNull(ORIGINAL_PASSWORD), "旧密码不得再通过登录");
        assertNotNull(loginSessionCookie(RESET_PASSWORD), "新密码必须可以重新登录");
        log.info("重置密码即下线断言完成：username={}", username);
    }

    @Test
    @DisplayName("禁用期间的吊销不可逆：启用后旧会话不复活，仅重新登录建立新会话")
    void enableUserRecoversByFreshLogin() {
        IamUserEntity user = createUser();
        String oldCookie = loginSessionCookie(ORIGINAL_PASSWORD);

        userService.disableUser(user.getId());
        assertEquals(HttpStatus.UNAUTHORIZED, meStatus(oldCookie));

        userService.enableUser(user.getId());

        assertEquals(HttpStatus.UNAUTHORIZED, meStatus(oldCookie), "吊销的会话不得随启用复活");
        String freshCookie = loginSessionCookie(ORIGINAL_PASSWORD);
        assertEquals(HttpStatus.OK, meStatus(freshCookie));
        log.info("启用恢复断言完成：username={}", username);
    }

    private IamUserEntity createUser() {
        CreateUserRequest request = new CreateUserRequest();
        request.setUsername(username);
        request.setPassword(ORIGINAL_PASSWORD);
        request.setDisplayName(username);
        request.setEmail(username + "@example.test");
        IamUserEntity user = userService.createUser(request);

        CreateTrustedApplicationClientRequest client = new CreateTrustedApplicationClientRequest();
        client.setClientId(clientId);
        client.setClientName("Lifecycle Confidential Client");
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
        application.setApplicationName("Lifecycle Application");
        application.setInitialClient(client);
        applicationId = trustedApplicationService.createApplication(application).getApplication().getId();
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

    private String loginSessionCookie(String password) {
        String cookie = loginOrNull(password);
        assertNotNull(cookie, "登录必须成功并建立会话");
        return cookie;
    }

    private String loginOrNull(String password) {
        ResponseEntity<Map> csrfResponse = exchange(HttpMethod.GET, "/iam/web/auth/csrf", null, Map.class);
        assertEquals(HttpStatus.OK, csrfResponse.getStatusCode());
        String sessionCookie = sessionCookie(csrfResponse.getHeaders());
        String csrfToken = (String) csrfResponse.getBody().get("token");

        HttpHeaders loginHeaders = cookieHeaders(sessionCookie);
        loginHeaders.setContentType(MediaType.APPLICATION_JSON);
        loginHeaders.set("X-CSRF-TOKEN", csrfToken);
        ResponseEntity<Map> loginResponse = exchange(HttpMethod.POST, "/iam/web/auth/login",
                new HttpEntity<>("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}",
                        loginHeaders),
                Map.class);
        if (loginResponse.getStatusCode() != HttpStatus.OK) {
            return null;
        }
        return sessionCookie(loginResponse.getHeaders(), sessionCookie);
    }

    private HttpStatus meStatus(String sessionCookie) {
        ResponseEntity<Map> response = exchange(HttpMethod.GET, "/iam/web/auth/me",
                new HttpEntity<>(cookieHeaders(sessionCookie)), Map.class);
        return response.getStatusCode();
    }

    private Map<String, Object> authorizationCodeTokens(String sessionCookie) {
        return authorizationCodeTokens(sessionCookie, true);
    }

    /**
     * @param expectConsent true = 首次授权，须先经 /consent 确认页；
     *                      false = 同一用户对同一客户端的 consent 记录已存在（不随会话吊销删除），
     *                      authorize 直接签发 code 重定向到 callback。
     */
    private Map<String, Object> authorizationCodeTokens(String sessionCookie, boolean expectConsent) {
        String authorizePath = UriComponentsBuilder.fromPath("/oauth2/authorize")
                .queryParam(OAuth2ParameterNames.RESPONSE_TYPE, "code")
                .queryParam(OAuth2ParameterNames.CLIENT_ID, clientId)
                .queryParam(OAuth2ParameterNames.REDIRECT_URI, redirectUri)
                .queryParam(OAuth2ParameterNames.SCOPE, "openid profile")
                .queryParam(OAuth2ParameterNames.STATE, "lifecycle-state")
                .build()
                .encode()
                .toUriString();
        ResponseEntity<Void> authorizeResponse = exchange(HttpMethod.GET, authorizePath,
                new HttpEntity<>(cookieHeaders(sessionCookie)), Void.class);
        assertEquals(HttpStatus.FOUND, authorizeResponse.getStatusCode());
        URI consentLocation = authorizeResponse.getHeaders().getLocation();
        assertNotNull(consentLocation);
        if (!expectConsent) {
            assertEquals("/callback", consentLocation.getPath());
            return exchangeAuthorizationCode(
                    UriComponentsBuilder.fromUri(consentLocation).build().getQueryParams().getFirst("code"));
        }
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
        URI callbackLocation = consentResponse.getHeaders().getLocation();
        assertNotNull(callbackLocation);
        String authorizationCode = UriComponentsBuilder.fromUri(callbackLocation).build()
                .getQueryParams().getFirst("code");
        assertNotNull(authorizationCode);
        return exchangeAuthorizationCode(authorizationCode);
    }

    private Map<String, Object> exchangeAuthorizationCode(String authorizationCode) {
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
        assertTrue(tokenResponse.getBody().containsKey("access_token"));
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
}
