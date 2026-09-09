package io.github.surezzzzzz.sdk.auth.iam.server.test.cases;

import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.trustedapplication.request.CreateTrustedApplicationClientRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.trustedapplication.request.CreateTrustedApplicationRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.trustedapplication.response.TrustedApplicationResponse;
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
 * 首登 / 重置后强制改密防线端到端测试（password.must-change-enforcement 显式重开）。
 *
 * <p>测试基座 yml 为既有 HTTP 测试批量建号场景关闭了防线，本类以 properties
 * 显式重开（生产默认口径）验收完整行为链：登录下发 mustChangePassword 标记 →
 * 非白名单 API 被 403（AUTH_009）拦截 → 自助改密成功解除拦截并踢其他端 →
 * 旧密码失效、策略违规与凭据语义分流、改密后 refresh 族吊销、管理员重置再置标记。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(
        classes = SimpleIamServerTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "io.github.surezzzzzz.sdk.auth.iam.server.password.must-change-enforcement=true"
        }
)
class IamMustChangePasswordEnforcementTest {

    private static final String INITIAL_PASSWORD = "Admin@1234";
    private static final String CHANGED_PASSWORD = "NewPass@1234";
    private static final String FINAL_PASSWORD = "Final@1234";

    private final String suffix = UUID.randomUUID().toString().substring(0, 8);
    private final String username = "must-change-" + suffix;
    private final String applicationCode = "must-change-app-" + suffix;
    private final String clientId = "must-change-client-" + suffix;
    private final String clientSecret = "must-change-secret-" + suffix;
    private final String redirectUri = "https://must-change.example.test/callback";
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
    @DisplayName("首登下发标记，未改密期间非白名单 403、白名单放行，改密后解除")
    void mustChangeFlagBlocksNonWhitelistedApisUntilChanged() {
        createUserWithClient();
        Map<String, Object> firstLogin = loginResponse(INITIAL_PASSWORD);
        assertEquals(200, firstLogin.get("status"), String.valueOf(firstLogin));
        assertEquals(Boolean.TRUE, firstLogin.get("mustChangePassword"),
                "管理员建号的初始密码登录必须下发须改密标记，前端据此跳改密页");
        String sessionCookie = loginSessionCookie(INITIAL_PASSWORD);

        ResponseEntity<Map> branding = exchange(HttpMethod.GET, "/iam/web/branding",
                new HttpEntity<>(cookieHeaders(sessionCookie)), Map.class);
        assertEquals(HttpStatus.FORBIDDEN, branding.getStatusCode(), "匿名可达的 branding 在须改密期间也必须被拦（非白名单即拦）");
        assertEquals("AUTH_009", branding.getBody().get("code"), "拦截响应须携带 AUTH_009 引导前端进改密流程");

        ResponseEntity<Map> me = exchange(HttpMethod.GET, "/iam/web/auth/me",
                new HttpEntity<>(cookieHeaders(sessionCookie)), Map.class);
        assertEquals(HttpStatus.OK, me.getStatusCode(), "me 在白名单内，改密页展示用户名依赖它");

        ResponseEntity<Map> changeResult = putPassword(sessionCookie, INITIAL_PASSWORD, CHANGED_PASSWORD);
        assertEquals(HttpStatus.NO_CONTENT, changeResult.getStatusCode(), String.valueOf(changeResult.getBody()));

        ResponseEntity<Map> brandingAfter = exchange(HttpMethod.GET, "/iam/web/branding",
                new HttpEntity<>(cookieHeaders(sessionCookie)), Map.class);
        assertEquals(HttpStatus.OK, brandingAfter.getStatusCode(), "改密完成后拦截解除");
        log.info("首登防线断言完成：username={}", username);
    }

    @Test
    @DisplayName("改密踢其他端保留当前会话；旧密码失效仅新密码可登录")
    void changePasswordRevokesOtherSessionsKeepsCurrent() {
        createUserWithClient();
        String currentCookie = loginSessionCookie(INITIAL_PASSWORD);
        String otherCookie = loginSessionCookie(INITIAL_PASSWORD);

        ResponseEntity<Map> changeResult = putPassword(currentCookie, INITIAL_PASSWORD, CHANGED_PASSWORD);
        assertEquals(HttpStatus.NO_CONTENT, changeResult.getStatusCode(), String.valueOf(changeResult.getBody()));

        assertEquals(HttpStatus.OK, meStatus(currentCookie), "当前会话（发起改密的一端）必须保留");
        assertEquals(HttpStatus.UNAUTHORIZED, meStatus(otherCookie), "其他端会话必须被吊销");

        assertNull(loginOrNull(INITIAL_PASSWORD), "旧密码不得再登录");
        String freshCookie = loginSessionCookie(CHANGED_PASSWORD);
        assertEquals(HttpStatus.OK, meStatus(freshCookie));
        log.info("改密踢线断言完成：username={}", username);
    }

    @Test
    @DisplayName("旧密码错走凭据失败语义；新密码策略违规回 400")
    void changePasswordErrorSemantics() {
        createUserWithClient();
        String sessionCookie = loginSessionCookie(INITIAL_PASSWORD);

        ResponseEntity<Map> wrongOld = putPassword(sessionCookie, "Wrong@1234", CHANGED_PASSWORD);
        assertEquals(HttpStatus.UNAUTHORIZED, wrongOld.getStatusCode(),
                "旧密码错与登录失败同语义（凭据不对），不得泄露其他信息");
        assertTrue(String.valueOf(wrongOld.getBody().get("message")).contains("剩余"),
                "错误信息应携带剩余尝试次数（与登录锁定口径一致）");

        ResponseEntity<Map> policyViolation = putPassword(sessionCookie, INITIAL_PASSWORD, "short");
        assertEquals(HttpStatus.BAD_REQUEST, policyViolation.getStatusCode(), "策略违规回 400");
        assertNotNull(policyViolation.getBody().get("message"), "策略违规须返回具体原因");
        log.info("改密错误语义断言完成：username={}", username);
    }

    @Test
    @DisplayName("管理员重置密码再置须改密标记；改密后 refresh 族随授权吊销失效")
    void resetReflagsAndChangeRevokesRefreshFamily() {
        IamUserEntity user = createUserWithClient();

        userService.resetPassword(user.getId(), FINAL_PASSWORD, "must-change-admin");
        IamUserEntity afterReset = userRepository.findByUsername(username).orElseThrow();
        assertTrue(Boolean.TRUE.equals(afterReset.getMustChangePassword()), "管理员重置后必须重新置须改密标记（临时密码）");
        loginSessionCookie(FINAL_PASSWORD);

        String currentCookie = loginSessionCookie(FINAL_PASSWORD);
        ResponseEntity<Map> firstChange = putPassword(currentCookie, FINAL_PASSWORD, CHANGED_PASSWORD);
        assertEquals(HttpStatus.NO_CONTENT, firstChange.getStatusCode(), String.valueOf(firstChange.getBody()));

        Map<String, Object> tokens = authorizationCodeTokens(currentCookie);
        String refreshToken = (String) tokens.get("refresh_token");
        assertNotNull(refreshToken, "改密后重新授权码流程应正常签发 refresh token");

        ResponseEntity<Map> secondChange = putPassword(currentCookie, CHANGED_PASSWORD, FINAL_PASSWORD);
        assertEquals(HttpStatus.NO_CONTENT, secondChange.getStatusCode(), String.valueOf(secondChange.getBody()));

        Map<String, Object> afterChange = refreshTokens(refreshToken);
        assertEquals(400, afterChange.get("status"), String.valueOf(afterChange));
        assertEquals("invalid_grant", afterChange.get("error"), "改密后旧 refresh 必须随族吊销以 invalid_grant 拒绝");
        log.info("重置标记与改密族吊销断言完成：username={}", username);
    }

    private IamUserEntity createUserWithClient() {
        CreateUserRequest request = new CreateUserRequest();
        request.setUsername(username);
        request.setPassword(INITIAL_PASSWORD);
        request.setDisplayName(username);
        request.setEmail(username + "@example.test");
        IamUserEntity user = userService.createUser(request);

        CreateTrustedApplicationClientRequest client = new CreateTrustedApplicationClientRequest();
        client.setClientId(clientId);
        client.setClientName("MustChange Confidential Client");
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
        application.setApplicationName("MustChange Application");
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

    /**
     * 首次登录断言须改密标记下发后返回会话 cookie；与 {@link #loginOrNull(String)} 分离，
     * 供首个用例在登录响应上直接断言 mustChangePassword 字段。
     */
    private Map loginResponse(String password) {
        ResponseEntity<Map> csrfResponse = exchange(HttpMethod.GET, "/iam/web/auth/csrf", null, Map.class);
        String sessionCookie = sessionCookie(csrfResponse.getHeaders());
        HttpHeaders loginHeaders = cookieHeaders(sessionCookie);
        loginHeaders.setContentType(MediaType.APPLICATION_JSON);
        loginHeaders.set("X-CSRF-TOKEN", (String) csrfResponse.getBody().get("token"));
        return mapOf(exchange(HttpMethod.POST, "/iam/web/auth/login",
                new HttpEntity<>("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}",
                        loginHeaders),
                Map.class));
    }

    private Map<String, Object> mapOf(ResponseEntity<Map> response) {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("status", response.getStatusCode().value());
        if (response.getBody() != null) {
            result.putAll(response.getBody());
        }
        return result;
    }

    private ResponseEntity<Map> putPassword(String sessionCookie, String oldPassword, String newPassword) {
        ResponseEntity<Map> csrfResponse = exchange(HttpMethod.GET, "/iam/web/auth/csrf",
                new HttpEntity<>(cookieHeaders(sessionCookie)), Map.class);
        assertEquals(HttpStatus.OK, csrfResponse.getStatusCode());
        HttpHeaders headers = cookieHeaders(sessionCookie);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-CSRF-TOKEN", (String) csrfResponse.getBody().get("token"));
        return exchange(HttpMethod.PUT, "/iam/web/auth/password",
                new HttpEntity<>("{\"oldPassword\":\"" + oldPassword + "\",\"newPassword\":\"" + newPassword + "\"}",
                        headers),
                Map.class);
    }

    private Map<String, Object> authorizationCodeTokens(String sessionCookie) {
        String authorizePath = UriComponentsBuilder.fromPath("/oauth2/authorize")
                .queryParam(OAuth2ParameterNames.RESPONSE_TYPE, "code")
                .queryParam(OAuth2ParameterNames.CLIENT_ID, clientId)
                .queryParam(OAuth2ParameterNames.REDIRECT_URI, redirectUri)
                .queryParam(OAuth2ParameterNames.SCOPE, "openid profile")
                .queryParam(OAuth2ParameterNames.STATE, "must-change-state")
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
        return mapOf(response);
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
