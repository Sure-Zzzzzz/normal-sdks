package io.github.surezzzzzz.sdk.auth.iam.server.test.cases;

import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.trustedapplication.request.CreateTrustedApplicationClientRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.trustedapplication.request.CreateTrustedApplicationRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.user.request.CreateUserRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamApplicationAuthorizationEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamUserEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamApplicationAuthorizationRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamUserRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.service.SessionService;
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
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 登出 / 批量吊销联动物理清理 OAuth2 授权测试。
 *
 * <p>refresh token 的安全语义已由绑定会话活性校验兜底（D3）；本测试锁定的是
 * S4 补齐的物理清理——授权行删除（JDBC）+ 授权缓存清除（findById / findByToken
 * 均为 null），登出与批量吊销（禁用 / 删除 / 重置密码共用 revokeAllByUserId）两条路径。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(
        classes = SimpleIamServerTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class SessionAuthorizationRevocationTest {

    private static final String ORIGINAL_PASSWORD = "Admin@1234";

    private final String suffix = UUID.randomUUID().toString().substring(0, 8);
    private final String username = "auth-revocation-" + suffix;
    private final String applicationCode = "auth-revocation-app-" + suffix;
    private final String clientId = "auth-revocation-client-" + suffix;
    private final String clientSecret = "auth-revocation-secret-" + suffix;
    private final String redirectUri = "https://auth-revocation.example.test/callback";
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
    private OAuth2AuthorizationService authorizationService;

    @Autowired
    private SessionService sessionService;

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
        }
    }

    @Test
    @DisplayName("登出后已签发授权被物理清理：授权行删除、缓存清空、refresh 拒绝")
    void logoutPhysicallyRevokesIssuedAuthorization() throws Exception {
        IamUserEntity user = createUser();
        String[] session = loginSessionAndCsrf();
        Map<String, Object> tokens = authorizationCodeTokens(session[0]);

        String accessToken = (String) tokens.get("access_token");
        OAuth2Authorization authorization = authorizationService.findByToken(accessToken, OAuth2TokenType.ACCESS_TOKEN);
        assertNotNull(authorization, "授权行必须先存在");
        assertEquals(1, authorizationRowCount(authorization.getId()), "授权必须先落库");

        ResponseEntity<Void> logout = exchange(HttpMethod.POST, "/iam/web/auth/logout",
                new HttpEntity<>(logoutHeaders(session[0], session[1])), Void.class);
        assertEquals(HttpStatus.NO_CONTENT, logout.getStatusCode(), String.valueOf(logout));

        assertNull(authorizationService.findById(authorization.getId()), "登出后授权缓存必须清除");
        assertNull(authorizationService.findByToken(accessToken, OAuth2TokenType.ACCESS_TOKEN), "access token 不得再命中");
        assertEquals(0, authorizationRowCount(authorization.getId()), "授权行必须物理删除");
        Map<String, Object> refreshed = refreshTokens((String) tokens.get("refresh_token"));
        assertEquals(HttpStatus.BAD_REQUEST.value(), refreshed.get("status"), String.valueOf(refreshed));
        assertEquals("invalid_grant", refreshed.get("error"));
        log.info("登出联动物理清理断言完成：username={}, authorizationId={}", username, authorization.getId());
    }

    @Test
    @DisplayName("revokeAllByUserId 后该用户全部授权被物理清理（禁用/删除/重置密码共用路径）")
    void revokeAllByUserIdPhysicallyRevokesAuthorizations() throws Exception {
        IamUserEntity user = createUser();
        String[] session = loginSessionAndCsrf();
        Map<String, Object> tokens = authorizationCodeTokens(session[0]);

        String accessToken = (String) tokens.get("access_token");
        OAuth2Authorization authorization = authorizationService.findByToken(accessToken, OAuth2TokenType.ACCESS_TOKEN);
        assertNotNull(authorization);
        assertEquals(1, authorizationRowCount(authorization.getId()));

        sessionService.revokeAllByUserId(user.getId());

        assertNull(authorizationService.findById(authorization.getId()), "批量吊销后授权缓存必须清除");
        assertEquals(0, authorizationRowCount(authorization.getId()), "授权行必须物理删除");
        Map<String, Object> refreshed = refreshTokens((String) tokens.get("refresh_token"));
        assertEquals(HttpStatus.BAD_REQUEST.value(), refreshed.get("status"), String.valueOf(refreshed));
        assertEquals("invalid_grant", refreshed.get("error"));
        log.info("批量吊销物理清理断言完成：username={}, authorizationId={}", username, authorization.getId());
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
        client.setClientName("Auth Revocation Confidential Client");
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
        application.setApplicationName("Auth Revocation Application");
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

    /**
     * 登录并返回 [会话 cookie 值, csrf token]（logout 需要复用登录会话内的 csrf token）。
     */
    private String[] loginSessionAndCsrf() {
        ResponseEntity<Map> csrfResponse = exchange(HttpMethod.GET, "/iam/web/auth/csrf", null, Map.class);
        assertEquals(HttpStatus.OK, csrfResponse.getStatusCode());
        String sessionCookie = sessionCookieOf(csrfResponse.getHeaders());
        String csrfToken = (String) csrfResponse.getBody().get("token");

        HttpHeaders loginHeaders = new HttpHeaders();
        loginHeaders.add(HttpHeaders.COOKIE, sessionCookie);
        loginHeaders.setContentType(MediaType.APPLICATION_JSON);
        loginHeaders.set("X-CSRF-TOKEN", csrfToken);
        ResponseEntity<Map> loginResponse = exchange(HttpMethod.POST, "/iam/web/auth/login",
                new HttpEntity<>("{\"username\":\"" + username + "\",\"password\":\"" + ORIGINAL_PASSWORD + "\"}",
                        loginHeaders), Map.class);
        assertEquals(HttpStatus.OK, loginResponse.getStatusCode(), String.valueOf(loginResponse.getBody()));
        String loginCookie = sessionCookieOf(loginResponse.getHeaders());
        return new String[]{loginCookie != null ? loginCookie : sessionCookie, csrfToken};
    }

    private HttpHeaders logoutHeaders(String sessionCookie, String csrfToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, sessionCookie);
        headers.set("X-CSRF-TOKEN", csrfToken);
        return headers;
    }

    /**
     * 首次授权必经 consent 确认页，同意后签发 code 重定向到 callback。
     */
    private Map<String, Object> authorizationCodeTokens(String sessionCookie) {
        HttpHeaders cookieHeaders = new HttpHeaders();
        cookieHeaders.add(HttpHeaders.COOKIE, sessionCookie);
        String authorizePath = UriComponentsBuilder.fromPath("/oauth2/authorize")
                .queryParam("response_type", "code")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("scope", "openid profile")
                .queryParam("state", "revocation-state")
                .build()
                .encode()
                .toUriString();
        ResponseEntity<Void> authorizeResponse = exchange(HttpMethod.GET, authorizePath,
                new HttpEntity<>(cookieHeaders), Void.class);
        assertEquals(HttpStatus.FOUND, authorizeResponse.getStatusCode());
        URI consentLocation = authorizeResponse.getHeaders().getLocation();
        assertNotNull(consentLocation);
        assertEquals("/consent", consentLocation.getPath(), "首次授权必须先经 consent 确认页");
        String consentState = org.springframework.web.util.UriUtils.decode(
                UriComponentsBuilder.fromUri(consentLocation).build().getQueryParams().getFirst("state"),
                java.nio.charset.StandardCharsets.UTF_8);

        HttpHeaders consentHeaders = new HttpHeaders();
        consentHeaders.add(HttpHeaders.COOKIE, sessionCookie);
        consentHeaders.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        MultiValueMap<String, String> consentForm = new LinkedMultiValueMap<>();
        consentForm.add("client_id", clientId);
        consentForm.add("state", consentState);
        consentForm.add("scope", "openid");
        consentForm.add("scope", "profile");
        ResponseEntity<Void> consentResponse = exchange(HttpMethod.POST, "/oauth2/authorize",
                new HttpEntity<>(consentForm, consentHeaders), Void.class);
        assertEquals(HttpStatus.FOUND, consentResponse.getStatusCode());
        URI callbackLocation = consentResponse.getHeaders().getLocation();
        assertNotNull(callbackLocation);
        assertEquals("/callback", callbackLocation.getPath());
        String authorizationCode = UriComponentsBuilder.fromUri(callbackLocation).build()
                .getQueryParams().getFirst("code");
        assertNotNull(authorizationCode);

        MultiValueMap<String, String> tokenForm = new LinkedMultiValueMap<>();
        tokenForm.add("grant_type", AuthorizationGrantType.AUTHORIZATION_CODE.getValue());
        tokenForm.add("client_id", clientId);
        tokenForm.add("client_secret", clientSecret);
        tokenForm.add("code", authorizationCode);
        tokenForm.add("redirect_uri", redirectUri);
        HttpHeaders tokenHeaders = new HttpHeaders();
        tokenHeaders.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        ResponseEntity<Map> tokenResponse = exchange(HttpMethod.POST, "/oauth2/token",
                new HttpEntity<>(tokenForm, tokenHeaders), Map.class);
        assertEquals(HttpStatus.OK, tokenResponse.getStatusCode(), String.valueOf(tokenResponse.getBody()));
        assertTrue(tokenResponse.getBody().containsKey("access_token"));
        assertTrue(tokenResponse.getBody().containsKey("refresh_token"), "authorization_code 流程必须发放 refresh token");
        return tokenResponse.getBody();
    }

    private Map<String, Object> refreshTokens(String refreshToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", AuthorizationGrantType.REFRESH_TOKEN.getValue());
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("refresh_token", refreshToken);
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

    private int authorizationRowCount(String authorizationId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM oauth2_authorization WHERE id = ?", Integer.class, authorizationId);
        return count != null ? count : 0;
    }

    private String sessionCookieOf(HttpHeaders headers) {
        String cookie = headers.getFirst(HttpHeaders.SET_COOKIE);
        if (cookie != null && cookie.startsWith(SimpleIamServerConstant.SESSION_COOKIE_NAME + "=")) {
            return cookie.split(";", 2)[0];
        }
        return null;
    }

    private <T> ResponseEntity<T> exchange(HttpMethod method, String path, HttpEntity<?> request, Class<T> responseType) {
        return restTemplate.exchange(URI.create("http://localhost:" + port + path), method, request, responseType);
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
}
