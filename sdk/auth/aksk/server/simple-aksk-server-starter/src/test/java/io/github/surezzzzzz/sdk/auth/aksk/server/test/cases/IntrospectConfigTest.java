package io.github.surezzzzzz.sdk.auth.aksk.server.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.server.controller.request.ApplicationAuthorizationRequest;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.response.ClientInfoResponse;
import io.github.surezzzzzz.sdk.auth.aksk.server.entity.AkskApplicationAuthorizationEntity;
import io.github.surezzzzzz.sdk.auth.aksk.server.repository.AkskApplicationAuthorizationRepository;
import io.github.surezzzzzz.sdk.auth.aksk.server.repository.OAuth2AuthorizationEntityRepository;
import io.github.surezzzzzz.sdk.auth.aksk.server.repository.OAuth2RegisteredClientEntityRepository;
import io.github.surezzzzzz.sdk.auth.aksk.server.service.ApplicationAuthorizationManagementService;
import io.github.surezzzzzz.sdk.auth.aksk.server.service.ClientManagementService;
import io.github.surezzzzzz.sdk.auth.aksk.server.support.AkskApplicationAuthorizationJsonCodec;
import io.github.surezzzzzz.sdk.auth.aksk.server.test.SimpleAkskServerTestApplication;
import io.github.surezzzzzz.sdk.auth.aksk.server.test.helper.ApplicationAuthorizationTestHelper;
import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.DataConstraintOperator;
import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.SimpleDataPermissionConstant;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataConstraint;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrant;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrantDocument;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 内省端点认证与当前授权投影测试。
 *
 * @author surezzzzzz
 */
@SpringBootTest(
        classes = SimpleAkskServerTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class IntrospectConfigTest {

    private static final String TEST_APPLICATION_CODE = "aksk-server";
    private static final String CURRENT_APPLICATION_CODE = "current-resource";
    private static final String CURRENT_MANIFEST_VERSION = "current-manifest";
    private static final String CURRENT_MANIFEST_DIGEST = "current-manifest-digest";
    private static final String CURRENT_API_PERMISSION = "api.current";

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ClientManagementService clientManagementService;

    @Autowired
    private ApplicationAuthorizationManagementService applicationAuthorizationManagementService;

    @Autowired
    private OAuth2RegisteredClientEntityRepository clientRepository;

    @Autowired
    private OAuth2AuthorizationEntityRepository authorizationEntityRepository;

    @Autowired
    private AkskApplicationAuthorizationRepository applicationAuthorizationRepository;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private String clientId;
    private String clientSecret;
    private String accessToken;

    @BeforeEach
    void setUp() {
        ClientInfoResponse client = clientManagementService.createPlatformClient("Introspect Config Test Client");
        clientId = client.getClientId();
        clientSecret = client.getClientSecret();
        ApplicationAuthorizationTestHelper.grantManagementAuthorization(applicationAuthorizationRepository, client);
        accessToken = fetchToken(clientId, clientSecret);
        assertNotNull(accessToken, "令牌不应为空");
    }

    @AfterEach
    void tearDown() {
        authorizationEntityRepository.deleteAll();
        applicationAuthorizationRepository.deleteAll();
        clientRepository.deleteAll();
        Set<String> keys = redisTemplate.keys("sure-auth-aksk:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Test
    void shouldReturnCurrentApplicationAuthorizationForActiveToken() {
        Map<String, Object> result = introspectWithCredentials(clientId, clientSecret, accessToken);

        assertEquals(Boolean.TRUE, result.get("active"));
        assertEquals(clientId, result.get("client_id"));
        assertNotNull(result.get("scope"));
        Map<String, Object> authorization = applicationAuthorization(result);
        assertEquals(TEST_APPLICATION_CODE, authorization.get("applicationCode"));
        assertEquals(clientId, authorization.get("subjectId"));
        assertEquals("SERVICE", authorization.get("subjectType"));
    }

    @Test
    void shouldRebuildAuthorizationFromCurrentProjection() {
        AkskApplicationAuthorizationEntity authorization = activeAuthorization();
        authorization.setApplicationCode(CURRENT_APPLICATION_CODE);
        authorization.setApiPermissionsJson("[\"" + CURRENT_API_PERMISSION + "\"]");
        authorization.setAuthorizationVersion(2L);
        authorization.setManifestVersion(CURRENT_MANIFEST_VERSION);
        authorization.setManifestDigest(CURRENT_MANIFEST_DIGEST);
        authorization.setUpdatedAt(Instant.now());
        applicationAuthorizationRepository.save(authorization);

        Map<String, Object> result = introspectWithCredentials(clientId, clientSecret, accessToken);
        Map<String, Object> currentAuthorization = applicationAuthorization(result);

        assertEquals(Boolean.TRUE, result.get("active"));
        assertEquals(CURRENT_APPLICATION_CODE, currentAuthorization.get("applicationCode"));
        assertEquals(CURRENT_MANIFEST_VERSION, currentAuthorization.get("manifestVersion"));
        assertEquals(CURRENT_MANIFEST_DIGEST, currentAuthorization.get("manifestDigest"));
        assertEquals(2L, ((Number) currentAuthorization.get("authorizationVersion")).longValue());
        assertEquals(CURRENT_API_PERMISSION, ((java.util.List<?>) currentAuthorization.get("apiPermissions")).get(0));
    }

    /**
     * 验证认证内省必须投影当前已验证的数据授权文档。
     */
    @Test
    void shouldReturnCurrentDataGrantDocumentFromProjection() {
        AkskApplicationAuthorizationEntity authorization = activeAuthorization();
        authorization.setDataGrantDocumentJson(AkskApplicationAuthorizationJsonCodec.writeDataGrantDocument(
                dataGrantDocument()));
        authorization.setUpdatedAt(Instant.now());
        applicationAuthorizationRepository.save(authorization);

        Map<String, Object> result = introspectWithCredentials(clientId, clientSecret, accessToken);
        Map<String, Object> currentAuthorization = applicationAuthorization(result);

        assertEquals(Boolean.TRUE, result.get("active"));
        assertEquals(SimpleDataPermissionConstant.PROTOCOL,
                ((Map<?, ?>) currentAuthorization.get("dataGrantDocument")).get("protocol"));
        assertEquals(1, ((java.util.List<?>) ((Map<?, ?>) currentAuthorization.get("dataGrantDocument"))
                .get("grants")).size());
    }

    @Test
    void shouldReturnInactiveWhenCurrentProjectionIsDisabled() {
        AkskApplicationAuthorizationEntity authorization = activeAuthorization();
        authorization.setEnabled(Boolean.FALSE);
        authorization.setUpdatedAt(Instant.now());
        applicationAuthorizationRepository.save(authorization);

        Map<String, Object> result = introspectWithCredentials(clientId, clientSecret, accessToken);

        assertEquals(Boolean.FALSE, result.get("active"));
        assertFalse(result.containsKey("aksk_authorization"));
    }

    @Test
    void shouldInvalidateExistingTokenWhenReplacingAuthorization() {
        Map<String, Object> tokenABeforeReplace = introspectWithCredentials(clientId, clientSecret, accessToken);
        assertEquals(Boolean.TRUE, tokenABeforeReplace.get("active"));

        applicationAuthorizationManagementService.replaceLocal(clientId,
                authorizationRequest(TEST_APPLICATION_CODE, CURRENT_API_PERMISSION));

        Map<String, Object> tokenAAfterReplace = introspectWithCredentials(clientId, clientSecret, accessToken);
        assertEquals(Boolean.FALSE, tokenAAfterReplace.get("active"),
                "完整替换授权后，既有令牌不得按当前投影获得新权限");
        assertFalse(tokenAAfterReplace.containsKey("aksk_authorization"));

        String tokenB = fetchToken(clientId, clientSecret);
        assertNotNull(tokenB, "完整替换后必须可以签发新令牌");
        Map<String, Object> tokenBAfterReplace = introspectWithCredentials(clientId, clientSecret, tokenB);
        assertEquals(Boolean.TRUE, tokenBAfterReplace.get("active"));
        Map<String, Object> tokenBAuthorization = applicationAuthorization(tokenBAfterReplace);
        assertEquals(CURRENT_API_PERMISSION, ((java.util.List<?>) tokenBAuthorization.get("apiPermissions")).get(0));
        assertEquals(2L, ((Number) tokenBAuthorization.get("authorizationVersion")).longValue());
    }

    @Test
    void shouldKeepRevokedTokenInactiveAfterReAdmission() {
        Map<String, Object> tokenABeforeRevoke = introspectWithCredentials(clientId, clientSecret, accessToken);
        assertEquals(Boolean.TRUE, tokenABeforeRevoke.get("active"));

        applicationAuthorizationManagementService.revokeLocal(clientId);
        Map<String, Object> tokenAAfterRevoke = introspectWithCredentials(clientId, clientSecret, accessToken);
        assertEquals(Boolean.FALSE, tokenAAfterRevoke.get("active"));
        assertFalse(tokenAAfterRevoke.containsKey("aksk_authorization"));

        applicationAuthorizationManagementService.replaceLocal(clientId,
                authorizationRequest(TEST_APPLICATION_CODE, CURRENT_API_PERMISSION));
        String tokenB = fetchToken(clientId, clientSecret);
        assertNotNull(tokenB, "重新准入后必须可以签发新令牌");

        Map<String, Object> tokenAAfterReadmission = introspectWithCredentials(clientId, clientSecret, accessToken);
        Map<String, Object> tokenBAfterReadmission = introspectWithCredentials(clientId, clientSecret, tokenB);
        assertEquals(Boolean.FALSE, tokenAAfterReadmission.get("active"),
                "撤销前签发的令牌不得因重新准入而恢复有效");
        assertFalse(tokenAAfterReadmission.containsKey("aksk_authorization"));
        assertEquals(Boolean.TRUE, tokenBAfterReadmission.get("active"));
        Map<String, Object> tokenBAuthorization = applicationAuthorization(tokenBAfterReadmission);
        assertEquals(TEST_APPLICATION_CODE, tokenBAuthorization.get("applicationCode"));
        assertEquals(3L, ((Number) tokenBAuthorization.get("authorizationVersion")).longValue());
        assertEquals(CURRENT_API_PERMISSION, ((java.util.List<?>) tokenBAuthorization.get("apiPermissions")).get(0));
    }

    @Test
    void shouldRejectIntrospectionWithoutCredentials() {
        ResponseEntity<Map> response = introspectWithoutCredentials(accessToken);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void shouldRejectIntrospectionWithInvalidCredentials() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth("invalid-client", "invalid-secret");
        MultiValueMap<String, String> body = new LinkedMultiValueMap<String, String>();
        body.add("token", accessToken);

        org.springframework.web.client.RestTemplate rawTemplate = restTemplate.getRestTemplate();
        org.springframework.web.client.ResponseErrorHandler originalHandler = rawTemplate.getErrorHandler();
        rawTemplate.setErrorHandler(new org.springframework.web.client.DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(org.springframework.http.client.ClientHttpResponse response) {
                return false;
            }
        });
        try {
            ResponseEntity<Map> response = rawTemplate.exchange(
                    endpoint(), HttpMethod.POST, new HttpEntity<MultiValueMap<String, String>>(body, headers), Map.class);
            assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        } finally {
            rawTemplate.setErrorHandler(originalHandler);
        }
    }

    private DataGrantDocument dataGrantDocument() {
        return new DataGrantDocument(SimpleDataPermissionConstant.PROTOCOL, SimpleDataPermissionConstant.VERSION,
                Collections.singletonList(new DataGrant("order", Collections.singletonList("read"), false,
                        Arrays.asList(new DataConstraint("tenantId", DataConstraintOperator.IN,
                                        Collections.singletonList("tenant-a")),
                                new DataConstraint("departmentId", DataConstraintOperator.IN,
                                        Collections.singletonList("department-a"))))));
    }

    private ApplicationAuthorizationRequest authorizationRequest(String applicationCode, String apiPermission) {
        ApplicationAuthorizationRequest request = new ApplicationAuthorizationRequest();
        request.setApplicationCode(applicationCode);
        request.setAdmitted(Boolean.TRUE);
        request.setRoles(Collections.<String>emptyList());
        request.setPagePermissions(Collections.<String>emptyList());
        request.setApiPermissions(Collections.singletonList(apiPermission));
        request.setDataGrantDocument(null);
        request.setManifestVersion(CURRENT_MANIFEST_VERSION);
        request.setManifestDigest(CURRENT_MANIFEST_DIGEST);
        return request;
    }

    private AkskApplicationAuthorizationEntity activeAuthorization() {
        return applicationAuthorizationRepository.findByClientId(clientId)
                .orElseThrow(() -> new IllegalStateException("测试授权投影不存在"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> applicationAuthorization(Map<String, Object> result) {
        Object authorization = result.get("aksk_authorization");
        assertNotNull(authorization, "active内省必须返回授权快照");
        return (Map<String, Object>) authorization;
    }

    private String fetchToken(String id, String secret) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth(id, secret);
        MultiValueMap<String, String> body = new LinkedMultiValueMap<String, String>();
        body.add("grant_type", "client_credentials");
        ResponseEntity<Map> response = restTemplate.exchange(endpoint("/oauth2/token"), HttpMethod.POST,
                new HttpEntity<MultiValueMap<String, String>>(body, headers), Map.class);
        return response.getStatusCode().is2xxSuccessful()
                ? (String) response.getBody().get("access_token") : null;
    }

    private Map<String, Object> introspectWithCredentials(String id, String secret, String token) {
        ResponseEntity<Map> response = restTemplate.exchange(endpoint(), HttpMethod.POST,
                buildIntrospectRequest(id, secret, token), Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        return response.getBody();
    }

    private ResponseEntity<Map> introspectWithoutCredentials(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        MultiValueMap<String, String> body = new LinkedMultiValueMap<String, String>();
        body.add("token", token);
        return restTemplate.exchange(endpoint(), HttpMethod.POST,
                new HttpEntity<MultiValueMap<String, String>>(body, headers), Map.class);
    }

    private HttpEntity<MultiValueMap<String, String>> buildIntrospectRequest(String id, String secret, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth(id, secret);
        MultiValueMap<String, String> body = new LinkedMultiValueMap<String, String>();
        body.add("token", token);
        return new HttpEntity<MultiValueMap<String, String>>(body, headers);
    }

    private String endpoint() {
        return endpoint("/oauth2/introspect");
    }

    private String endpoint(String path) {
        return "http://localhost:" + port + path;
    }
}
