package io.github.surezzzzzz.sdk.auth.iam.server.test.cases;

import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.authorization.request.PutApplicationAuthorizationRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.manifest.request.PutApplicationPermissionManifestRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.trustedapplication.request.CreateTrustedApplicationClientRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.trustedapplication.request.CreateTrustedApplicationRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.trustedapplication.response.TrustedApplicationResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamApplicationAuthorizationEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamResourceVerificationClientEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamSessionEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamUserEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamApplicationAuthorizationRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamResourceVerificationClientRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamSessionRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamUserRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.service.IamApplicationAuthorizationAdminService;
import io.github.surezzzzzz.sdk.auth.iam.server.service.IamApplicationPermissionManifestService;
import io.github.surezzzzzz.sdk.auth.iam.server.service.SessionService;
import io.github.surezzzzzz.sdk.auth.iam.server.service.TrustedApplicationService;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IAM 资源令牌受控验证真实 HTTP 测试。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(
        classes = SimpleIamServerTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class IamResourceTokenVerificationEndToEndTest {

    private final String suffix = UUID.randomUUID().toString().substring(0, 8);
    private final String username = "resource-verification-" + suffix;
    private final String applicationCode = "resource-verification-app-" + suffix;
    private final String clientId = "resource-verification-public-" + suffix;
    private final String verificationClientId = "resource-verification-client-" + suffix;
    private final String verificationClientSecret = UUID.randomUUID().toString();
    private final String accessTokenValue = UUID.randomUUID().toString();
    private final RestTemplate restTemplate = restTemplate();
    private Long applicationId;
    private Long crossApplicationId;
    private IamUserEntity user;
    private IamSessionEntity session;
    private IamResourceVerificationClientEntity verificationClient;
    private OAuth2Authorization authorization;

    @LocalServerPort
    private int port;

    @Autowired
    private IamUserRepository userRepository;

    @Autowired
    private TrustedApplicationService trustedApplicationService;

    @Autowired
    private RegisteredClientRepository registeredClientRepository;

    @Autowired
    private OAuth2AuthorizationService authorizationService;

    @Autowired
    private IamApplicationAuthorizationRepository applicationAuthorizationRepository;

    @Autowired
    private IamResourceVerificationClientRepository verificationClientRepository;

    @Autowired
    private SessionService sessionService;

    @Autowired
    private IamApplicationAuthorizationAdminService authorizationAdminService;

    @Autowired
    private IamApplicationPermissionManifestService manifestService;

    @Autowired
    private IamSessionRepository sessionRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @AfterEach
    void cleanup() {
        if (authorization != null) {
            authorizationService.remove(authorization);
        }
        if (verificationClient != null) {
            verificationClientRepository.deleteById(verificationClient.getId());
        }
        if (session != null) {
            sessionRepository.deleteById(session.getId());
        }
        if (user != null) {
            if (applicationId != null) {
                applicationAuthorizationRepository.findByUserIdAndApplicationId(user.getId(), applicationId)
                        .ifPresent(applicationAuthorizationRepository::delete);
            }
            userRepository.deleteById(user.getId());
        }
        if (applicationId != null) {
            trustedApplicationService.deleteApplication(applicationId);
        }
        if (crossApplicationId != null) {
            trustedApplicationService.deleteApplication(crossApplicationId);
        }
    }

    @Test
    @DisplayName("有效 IAM Access Token 应返回最小双字段授权响应")
    void validTokenReturnsControlledClaims() {
        prepareValidFixture();

        ResponseEntity<Map> response = verify(accessTokenValue, verificationClientId, verificationClientSecret);

        log.info("资源令牌验证成功响应状态：{}", response.getStatusCode());
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().size(), "受控响应只能包含两个顶级字段");
        assertEquals(String.valueOf(user.getId()), response.getBody().get("sub"));
        assertTrue(response.getBody().containsKey("iam_authorization"));
    }

    @Test
    @DisplayName("Basic 认证边界必须拒绝缺失重复畸形和错误凭据")
    void basicAuthenticationFailuresReturnUnauthorized() {
        prepareValidFixture();

        ResponseEntity<Map> missing = verifyWithHeaders(accessTokenValue, new HttpHeaders());
        ResponseEntity<Map> duplicate = verifyWithHeaders(accessTokenValue,
                authorizationHeaders("Basic first", "Basic second"));
        ResponseEntity<Map> nonBasic = verifyWithHeaders(accessTokenValue,
                authorizationHeaders("Bearer anything"));
        ResponseEntity<Map> malformed = verifyWithHeaders(accessTokenValue,
                authorizationHeaders("Basic invalid"));
        ResponseEntity<Map> wrongSecret = verify(accessTokenValue, verificationClientId, UUID.randomUUID().toString());

        log.info("资源验证 Basic 拒绝状态：missing={}, duplicate={}, nonBasic={}, malformed={}, wrongSecret={}",
                missing.getStatusCode(), duplicate.getStatusCode(), nonBasic.getStatusCode(),
                malformed.getStatusCode(), wrongSecret.getStatusCode());
        assertUnauthorized(missing);
        assertUnauthorized(duplicate);
        assertUnauthorized(nonBasic);
        assertUnauthorized(malformed);
        assertUnauthorized(wrongSecret);
    }

    @Test
    @DisplayName("空令牌和不存在令牌不得形成资源认证")
    void missingOrUnknownTokenReturnsUnauthorized() {
        prepareValidFixture();

        ResponseEntity<Map> empty = verify("", verificationClientId, verificationClientSecret);
        ResponseEntity<Map> unknown = verify(UUID.randomUUID().toString(), verificationClientId, verificationClientSecret);

        log.info("资源验证令牌拒绝状态：empty={}, unknown={}",
                empty.getStatusCode(), unknown.getStatusCode());
        assertUnauthorized(empty);
        assertUnauthorized(unknown);
    }

    @Test
    @DisplayName("验证客户端跨应用时不得验证其他应用令牌")
    void crossApplicationVerificationClientIsRejected() {
        prepareValidFixture();
        crossApplicationId = createApplication("resource-verification-cross-app-" + suffix,
                "resource-verification-cross-client-" + suffix);
        verificationClient.setApplicationId(crossApplicationId);
        verificationClientRepository.save(verificationClient);

        ResponseEntity<Map> response = verify(accessTokenValue, verificationClientId, verificationClientSecret);

        log.info("跨应用资源验证响应状态：{}", response.getStatusCode());
        assertUnauthorized(response);
    }

    @Test
    @DisplayName("已撤销 IAM 会话或停用用户不得形成资源认证")
    void revokedSessionOrDisabledUserIsRejected() {
        prepareValidFixture();
        sessionService.revokeSession(session.getId());

        ResponseEntity<Map> revokedSession = verify(accessTokenValue, verificationClientId, verificationClientSecret);
        user.setStatus(SimpleIamServerConstant.STATUS_INACTIVE);
        userRepository.save(user);
        session.setStatus(SimpleIamServerConstant.STATUS_ACTIVE);
        session.setRevokedAt(null);
        sessionRepository.save(session);
        ResponseEntity<Map> disabledUser = verify(accessTokenValue, verificationClientId, verificationClientSecret);

        log.info("资源验证状态拒绝：revokedSession={}, disabledUser={}",
                revokedSession.getStatusCode(), disabledUser.getStatusCode());
        assertUnauthorized(revokedSession);
        assertUnauthorized(disabledUser);
    }

    @Test
    @DisplayName("过期 Access Token 或失效授权投影不得形成资源认证")
    void expiredTokenOrInactiveAuthorizationIsRejected() {
        prepareValidFixture();
        IamApplicationAuthorizationEntity applicationAuthorization = applicationAuthorizationRepository
                .findByUserIdAndApplicationId(user.getId(), applicationId)
                .orElseThrow(() -> new AssertionError("应用授权投影 fixture 必须已建立：userId=" + user.getId()
                        + ", applicationId=" + applicationId));
        applicationAuthorization.setStatus(SimpleIamServerConstant.STATUS_INACTIVE);
        applicationAuthorizationRepository.save(applicationAuthorization);
        ResponseEntity<Map> inactiveAuthorizationResponse = verify(accessTokenValue,
                verificationClientId, verificationClientSecret);
        applicationAuthorization.setStatus(SimpleIamServerConstant.STATUS_ACTIVE);
        applicationAuthorizationRepository.save(applicationAuthorization);

        String expiredTokenValue = UUID.randomUUID().toString();
        Instant expiresAt = Instant.now().minusSeconds(1L);
        OAuth2AccessToken expiredToken = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,
                expiredTokenValue, expiresAt.minusSeconds(1L), expiresAt, Collections.singleton("openid"));
        authorization = OAuth2Authorization.from(authorization).token(expiredToken).build();
        authorizationService.save(authorization);
        ResponseEntity<Map> expiredTokenResponse = verify(expiredTokenValue,
                verificationClientId, verificationClientSecret);

        log.info("资源验证时效拒绝：inactiveAuthorization={}, expiredToken={}",
                inactiveAuthorizationResponse.getStatusCode(), expiredTokenResponse.getStatusCode());
        assertUnauthorized(inactiveAuthorizationResponse);
        assertUnauthorized(expiredTokenResponse);
    }

    @Test
    @DisplayName("管理面撤销后同一令牌验证立即拒绝，重激活后验证恢复")
    void adminRevocationTakesEffectImmediately() {
        prepareValidFixture();

        ResponseEntity<Map> before = verify(accessTokenValue, verificationClientId, verificationClientSecret);
        assertEquals(HttpStatus.OK, before.getStatusCode(), "撤销前验证必须成功");

        authorizationAdminService.revokeAuthorization(user.getId(), applicationId);
        ResponseEntity<Map> revoked = verify(accessTokenValue, verificationClientId, verificationClientSecret);

        PutApplicationAuthorizationRequest reactivate = new PutApplicationAuthorizationRequest();
        reactivate.setAdmitted(Boolean.TRUE);
        reactivate.setRoles(Collections.emptyList());
        reactivate.setPagePermissions(Collections.emptyList());
        reactivate.setApiPermissions(Collections.singletonList("resource.read"));
        reactivate.setDataGrantDocument(null);
        authorizationAdminService.putAuthorization(user.getId(), applicationId, reactivate);
        ResponseEntity<Map> reactivated = verify(accessTokenValue, verificationClientId, verificationClientSecret);

        log.info("管理面撤销即时性：revoked={}, reactivated={}",
                revoked.getStatusCode(), reactivated.getStatusCode());
        assertUnauthorized(revoked);
        assertEquals(HttpStatus.OK, reactivated.getStatusCode(), "重激活后同一令牌验证必须恢复");
    }

    private void prepareValidFixture() {
        user = createUser();
        applicationId = createApplication(applicationCode, clientId);
        createApplicationAuthorization();
        session = sessionService.createSession(user.getId(), username, clientId, null, null);
        verificationClient = createVerificationClient(applicationId);
        authorization = createAuthorization();
        authorizationService.save(authorization);
    }

    private IamUserEntity createUser() {
        Instant now = Instant.now();
        IamUserEntity entity = new IamUserEntity();
        entity.setUsername(username);
        entity.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
        entity.setDisplayName(username);
        entity.setStatus(SimpleIamServerConstant.STATUS_ACTIVE);
        entity.setFailedLoginCount(SimpleIamServerConstant.DEFAULT_FAILED_LOGIN_COUNT);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return userRepository.save(entity);
    }

    private Long createApplication(String code, String oauthClientId) {
        CreateTrustedApplicationClientRequest client = new CreateTrustedApplicationClientRequest();
        client.setClientId(oauthClientId);
        client.setClientName("Resource Verification Public Client");
        client.setClientType("PUBLIC");
        client.setRequireConsent(false);
        client.setRedirectUris(Collections.singletonList("https://resource.example.test/callback"));
        client.setScopes(Arrays.asList("openid", "profile"));
        client.setGrantTypes(Collections.singletonList(AuthorizationGrantType.AUTHORIZATION_CODE.getValue()));
        client.setAuthenticationMethods(Collections.singletonList("none"));

        CreateTrustedApplicationRequest request = new CreateTrustedApplicationRequest();
        request.setApplicationCode(code);
        request.setApplicationName("Resource Verification Application");
        request.setInitialClient(client);
        TrustedApplicationResponse application = trustedApplicationService.createApplication(request).getApplication();
        PutApplicationPermissionManifestRequest manifest = new PutApplicationPermissionManifestRequest();
        manifest.setRoles(Collections.emptyList());
        manifest.setPagePermissions(Collections.emptyList());
        manifest.setApiPermissions(Collections.singletonList("resource.read"));
        manifest.setDataResources(Collections.emptyList());
        manifestService.putManifest(application.getId(), manifest);
        return application.getId();
    }

    private void createApplicationAuthorization() {
        Instant now = Instant.now();
        IamApplicationAuthorizationEntity applicationAuthorization = new IamApplicationAuthorizationEntity();
        applicationAuthorization.setUserId(user.getId());
        applicationAuthorization.setApplicationId(applicationId);
        applicationAuthorization.setAdmitted(SimpleIamServerConstant.STATUS_ACTIVE);
        applicationAuthorization.setRolesJson("[]");
        applicationAuthorization.setPagePermissionsJson("[]");
        applicationAuthorization.setApiPermissionsJson("[\"resource.read\"]");
        applicationAuthorization.setAuthorizationVersion(1L);
        applicationAuthorization.setManifestVersion("resource-verification-v1");
        applicationAuthorization.setManifestDigest("resource-verification");
        applicationAuthorization.setStatus(SimpleIamServerConstant.STATUS_ACTIVE);
        applicationAuthorization.setCreatedAt(now);
        applicationAuthorization.setUpdatedAt(now);
        applicationAuthorizationRepository.save(applicationAuthorization);
    }

    private IamResourceVerificationClientEntity createVerificationClient(Long targetApplicationId) {
        Instant now = Instant.now();
        IamResourceVerificationClientEntity client = new IamResourceVerificationClientEntity();
        client.setId(UUID.randomUUID().toString());
        client.setApplicationId(targetApplicationId);
        client.setClientId(verificationClientId);
        client.setClientSecretHash(passwordEncoder.encode(verificationClientSecret));
        client.setStatus(SimpleIamServerConstant.STATUS_ACTIVE);
        client.setCreatedAt(now);
        client.setUpdatedAt(now);
        return verificationClientRepository.save(client);
    }

    private OAuth2Authorization createAuthorization() {
        RegisteredClient client = registeredClientRepository.findByClientId(clientId);
        assertNotNull(client, "测试 OAuth Client 必须已创建");
        Instant issuedAt = Instant.now();
        OAuth2AccessToken accessToken = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,
                accessTokenValue, issuedAt, issuedAt.plusSeconds(SimpleIamServerConstant.DEFAULT_ACCESS_TOKEN_EXPIRES_IN),
                Collections.singleton("openid"));
        return OAuth2Authorization.withRegisteredClient(client)
                .principalName(username)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .attribute(SimpleIamServerConstant.AUTHORIZATION_ATTRIBUTE_IAM_SESSION_ID, session.getId())
                .token(accessToken)
                .build();
    }

    private ResponseEntity<Map> verify(String token, String clientId, String clientSecret) {
        String basicValue = clientId + ":" + clientSecret;
        String basicAuthorization = "Basic " + Base64.getEncoder().encodeToString(
                basicValue.getBytes(StandardCharsets.UTF_8));
        return verifyWithHeaders(token, authorizationHeaders(basicAuthorization));
    }

    private ResponseEntity<Map> verifyWithHeaders(String token, HttpHeaders headers) {
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(URI.create("http://localhost:" + port + "/iam/resource/tokens/verify"),
                HttpMethod.POST, new HttpEntity<>(Collections.singletonMap("token", token), headers), Map.class);
    }

    private HttpHeaders authorizationHeaders(String... values) {
        HttpHeaders headers = new HttpHeaders();
        for (String value : values) {
            headers.add(HttpHeaders.AUTHORIZATION, value);
        }
        return headers;
    }

    private void assertUnauthorized(ResponseEntity<?> response) {
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("Basic realm=\"iam-resource-verification\"",
                response.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE));
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
