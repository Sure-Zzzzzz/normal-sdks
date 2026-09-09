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
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Slf4j
@SpringBootTest(
        classes = SimpleIamServerTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "io.github.surezzzzzz.sdk.auth.iam.server.token.format=jwe"
)
class JweAuthorizationCodeUserInfoEndToEndTest {

    private final String suffix = UUID.randomUUID().toString().substring(0, 8);
    private final String username = "jwe-authorization-code-" + suffix;
    private final String applicationCode = "jwe-authorization-code-app-" + suffix;
    private final String clientId = "jwe-public-pkce-" + suffix;
    private final String redirectUri = "https://client.example.test/callback";
    private final String codeVerifier = "mF_9.B5f-4.1JqM2OaG6E6p9aFJ4wV3wSx5xT2lV2wP7wR8nY0dG5xQ1p";
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
    @DisplayName("JWE 模式下授权码流程签发的访问令牌应可访问 UserInfo")
    void jweAccessTokenSupportsUserInfo() throws Exception {
        IamUserEntity user = createUser();
        createTrustedApplication(user);

        ResponseEntity<Map> csrfResponse = exchange(HttpMethod.GET, "/iam/web/auth/csrf", null, Map.class);
        assertEquals(HttpStatus.OK, csrfResponse.getStatusCode());
        String sessionCookie = sessionCookie(csrfResponse.getHeaders(), null);
        String csrfToken = (String) csrfResponse.getBody().get("token");

        HttpHeaders loginHeaders = headers(sessionCookie);
        loginHeaders.setContentType(MediaType.APPLICATION_JSON);
        loginHeaders.set("X-CSRF-TOKEN", csrfToken);
        ResponseEntity<Map> loginResponse = exchange(HttpMethod.POST, "/iam/web/auth/login",
                new HttpEntity<>("{\"username\":\"" + username + "\",\"password\":\"Admin@1234\"}", loginHeaders), Map.class);
        assertEquals(HttpStatus.OK, loginResponse.getStatusCode());
        sessionCookie = sessionCookie(loginResponse.getHeaders(), sessionCookie);

        String authorizePath = UriComponentsBuilder.fromPath("/oauth2/authorize")
                .queryParam(OAuth2ParameterNames.RESPONSE_TYPE, "code")
                .queryParam(OAuth2ParameterNames.CLIENT_ID, clientId)
                .queryParam(OAuth2ParameterNames.REDIRECT_URI, redirectUri)
                .queryParam(OAuth2ParameterNames.SCOPE, "openid profile")
                .queryParam(OAuth2ParameterNames.STATE, "jwe-client-state")
                .queryParam("nonce", "jwe-oidc-nonce")
                .queryParam("code_challenge", codeChallenge())
                .queryParam("code_challenge_method", "S256")
                .build()
                .encode()
                .toUriString();
        ResponseEntity<Void> authorizeResponse = exchange(HttpMethod.GET, authorizePath,
                new HttpEntity<>(headers(sessionCookie)), Void.class);
        assertEquals(HttpStatus.FOUND, authorizeResponse.getStatusCode());
        URI consentLocation = authorizeResponse.getHeaders().getLocation();
        assertNotNull(consentLocation);
        String consentState = UriComponentsBuilder.fromUri(consentLocation).build().getQueryParams().getFirst("state");
        assertNotNull(consentState);
        consentState = UriUtils.decode(consentState, StandardCharsets.UTF_8);

        String consentInfoPath = UriComponentsBuilder.fromPath("/iam/web/oauth2/consent-info")
                .queryParam(OAuth2ParameterNames.STATE, consentState)
                .build()
                .encode()
                .toUriString();
        ResponseEntity<Map> consentInfoResponse = exchange(HttpMethod.GET, consentInfoPath,
                new HttpEntity<>(headers(sessionCookie)), Map.class);
        assertEquals(HttpStatus.OK, consentInfoResponse.getStatusCode());

        HttpHeaders consentHeaders = headers(sessionCookie);
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
        tokenForm.add(OAuth2ParameterNames.CODE, authorizationCode);
        tokenForm.add(OAuth2ParameterNames.REDIRECT_URI, redirectUri);
        tokenForm.add("code_verifier", codeVerifier);
        HttpHeaders tokenHeaders = new HttpHeaders();
        tokenHeaders.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        ResponseEntity<Map> tokenResponse = exchange(HttpMethod.POST, "/oauth2/token",
                new HttpEntity<>(tokenForm, tokenHeaders), Map.class);
        assertEquals(HttpStatus.OK, tokenResponse.getStatusCode());
        String accessToken = (String) tokenResponse.getBody().get("access_token");
        assertNotNull(accessToken);
        assertEquals(5, accessToken.split("\\.").length);

        HttpHeaders userInfoHeaders = new HttpHeaders();
        userInfoHeaders.setBearerAuth(accessToken);
        ResponseEntity<Map> userInfoResponse = exchange(HttpMethod.GET, "/userinfo",
                new HttpEntity<>(userInfoHeaders), Map.class);
        assertEquals(HttpStatus.OK, userInfoResponse.getStatusCode());
        assertEquals(String.valueOf(user.getId()), userInfoResponse.getBody().get("sub"));
        assertEquals(username, userInfoResponse.getBody().get("preferred_username"));
        log.info("JWE UserInfo 访问成功：userId={}", user.getId());
    }

    private IamUserEntity createUser() {
        CreateUserRequest request = new CreateUserRequest();
        request.setUsername(username);
        request.setPassword("Admin@1234");
        request.setDisplayName(username);
        return userService.createUser(request);
    }

    private void createTrustedApplication(IamUserEntity user) {
        CreateTrustedApplicationClientRequest client = new CreateTrustedApplicationClientRequest();
        client.setClientId(clientId);
        client.setClientName("JWE Public PKCE E2E Client");
        client.setClientType("PUBLIC");
        client.setRequireConsent(true);
        client.setRedirectUris(java.util.Collections.singletonList(redirectUri));
        client.setScopes(java.util.Arrays.asList("openid", "profile"));
        client.setGrantTypes(java.util.Collections.singletonList("authorization_code"));
        client.setAuthenticationMethods(java.util.Collections.singletonList("none"));

        CreateTrustedApplicationRequest request = new CreateTrustedApplicationRequest();
        request.setApplicationCode(applicationCode);
        request.setApplicationName("JWE Public PKCE E2E Application");
        request.setInitialClient(client);
        TrustedApplicationResponse application = trustedApplicationService.createApplication(request).getApplication();
        applicationId = application.getId();
        applicationAuthorizationRepository.save(createApplicationAuthorization(user.getId(), applicationId));
    }

    private IamApplicationAuthorizationEntity createApplicationAuthorization(Long userId, Long trustedApplicationId) {
        java.time.Instant now = java.time.Instant.now();
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

    private String codeChallenge() throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
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

    private HttpHeaders headers(String sessionCookie) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.COOKIE, sessionCookie);
        return headers;
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
