package io.github.surezzzzzz.sdk.audit.aksk.server.test.cases;

import io.github.surezzzzzz.sdk.audit.aksk.server.model.ServerTokenAuditRecord;
import io.github.surezzzzzz.sdk.audit.aksk.server.test.ServerTokenAuditListenerTestApplication;
import io.github.surezzzzzz.sdk.audit.aksk.server.test.TestServerTokenAuditHandler;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.request.ApplicationAuthorizationRequest;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.response.ClientInfoResponse;
import io.github.surezzzzzz.sdk.auth.aksk.server.event.TokenEventCause;
import io.github.surezzzzzz.sdk.auth.aksk.server.event.TokenEventType;
import io.github.surezzzzzz.sdk.auth.aksk.server.repository.AkskApplicationAuthorizationRepository;
import io.github.surezzzzzz.sdk.auth.aksk.server.repository.OAuth2AuthorizationEntityRepository;
import io.github.surezzzzzz.sdk.auth.aksk.server.repository.OAuth2RegisteredClientEntityRepository;
import io.github.surezzzzzz.sdk.auth.aksk.server.service.ApplicationAuthorizationManagementService;
import io.github.surezzzzzz.sdk.auth.aksk.server.service.ClientManagementService;
import io.github.surezzzzzz.sdk.auth.aksk.server.service.TokenManagementService;
import lombok.extern.slf4j.Slf4j;
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

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Server Token 审计监听器集成测试。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(
        classes = ServerTokenAuditListenerTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class ServerTokenAuditListenerIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private TestServerTokenAuditHandler testHandler;

    @Autowired
    private ClientManagementService clientManagementService;

    @Autowired
    private ApplicationAuthorizationManagementService applicationAuthorizationManagementService;

    @Autowired
    private TokenManagementService tokenManagementService;

    @Autowired
    private AkskApplicationAuthorizationRepository applicationAuthorizationRepository;

    @Autowired
    private OAuth2AuthorizationEntityRepository authorizationEntityRepository;

    @Autowired
    private OAuth2RegisteredClientEntityRepository clientRepository;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private String clientId;
    private String clientSecret;

    @BeforeEach
    void setUp() {
        ClientInfoResponse client = clientManagementService.createPlatformClient("Audit Test Client");
        clientId = client.getClientId();
        clientSecret = client.getClientSecret();
        applicationAuthorizationManagementService.createLocal(clientId, admittedAuthorizationRequest());
        testHandler.reset(TokenEventType.ISSUED);
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
    void shouldAuditIssuedEventWithoutTokenValue() {
        requestToken();

        ServerTokenAuditRecord record = onlyRecord(TokenEventType.ISSUED);
        assertEquals(clientId, record.getClientId());
        assertEquals("platform", record.getClientType());
        assertEquals(TokenEventCause.UNSPECIFIED, record.getCause());
        assertNotNull(record.getIssuedAt());
        assertNotNull(record.getExpiresAt());
        assertNull(record.getTokenValue());
        assertNull(record.getActive());
    }

    @Test
    void shouldAuditOAuthRevocationCauseWithoutTokenValue() {
        String token = requestToken();
        testHandler.reset(TokenEventType.REVOKED);

        ResponseEntity<Void> response = restTemplate.exchange(baseUrl() + "/oauth2/revoke", HttpMethod.POST,
                new HttpEntity<MultiValueMap<String, String>>(tokenRequest(token), basicAuthHeaders()), Void.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        ServerTokenAuditRecord record = onlyRecord(TokenEventType.REVOKED);
        assertEquals(clientId, record.getClientId());
        assertEquals(TokenEventCause.OAUTH2_REVOKE, record.getCause());
        assertNull(record.getTokenValue());
        assertNull(record.getActive());
    }

    @Test
    void shouldAuditActiveIntrospectionWithoutTokenValue() {
        String token = requestToken();
        testHandler.reset(TokenEventType.INTROSPECTED);

        ResponseEntity<Map> response = restTemplate.exchange(baseUrl() + "/oauth2/introspect", HttpMethod.POST,
                new HttpEntity<MultiValueMap<String, String>>(tokenRequest(token), basicAuthHeaders()), Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(Boolean.TRUE, response.getBody().get("active"));
        ServerTokenAuditRecord record = onlyRecord(TokenEventType.INTROSPECTED);
        assertEquals(clientId, record.getClientId());
        assertTrue(record.getActive());
        assertNull(record.getTokenValue());
    }

    @Test
    void shouldAuditInactiveIntrospectionWithoutTokenValue() {
        String token = requestToken();
        testHandler.reset(TokenEventType.REVOKED);
        restTemplate.exchange(baseUrl() + "/oauth2/revoke", HttpMethod.POST,
                new HttpEntity<MultiValueMap<String, String>>(tokenRequest(token), basicAuthHeaders()), Void.class);
        onlyRecord(TokenEventType.REVOKED);

        testHandler.reset(TokenEventType.INTROSPECTED);
        ResponseEntity<Map> response = restTemplate.exchange(baseUrl() + "/oauth2/introspect", HttpMethod.POST,
                new HttpEntity<MultiValueMap<String, String>>(tokenRequest(token), basicAuthHeaders()), Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(Boolean.FALSE, response.getBody().get("active"));
        ServerTokenAuditRecord record = onlyRecord(TokenEventType.INTROSPECTED);
        assertFalse(record.getActive());
        assertNull(record.getTokenValue());
    }

    @Test
    void shouldAuditTokenManagementRevocationCauseWithoutTokenValue() {
        requestToken();
        testHandler.reset(TokenEventType.REVOKED);

        tokenManagementService.revokeAllByClientId(clientId);

        assertRevocationCause(TokenEventCause.TOKEN_MANAGEMENT);
    }

    @Test
    void shouldAuditApplicationAuthorizationReplacementCauseWithoutTokenValue() {
        requestToken();
        testHandler.reset(TokenEventType.REVOKED);

        applicationAuthorizationManagementService.replaceLocal(clientId, admittedAuthorizationRequest());

        assertRevocationCause(TokenEventCause.APPLICATION_AUTHORIZATION_REPLACED);
    }

    @Test
    void shouldAuditApplicationAuthorizationRevocationCauseWithoutTokenValue() {
        requestToken();
        testHandler.reset(TokenEventType.REVOKED);

        applicationAuthorizationManagementService.revokeLocal(clientId);

        assertRevocationCause(TokenEventCause.APPLICATION_AUTHORIZATION_REVOKED);
    }

    @Test
    void shouldAuditClientDeletionCauseWithoutTokenValue() {
        requestToken();
        testHandler.reset(TokenEventType.REVOKED);

        clientManagementService.deleteClient(clientId);

        assertRevocationCause(TokenEventCause.CLIENT_DELETED);
    }

    @Test
    void shouldAuditClientSecretResetCauseWithoutTokenValue() {
        requestToken();
        testHandler.reset(TokenEventType.REVOKED);

        clientManagementService.resetSecret(clientId, true);

        assertRevocationCause(TokenEventCause.CLIENT_SECRET_RESET);
    }

    private ApplicationAuthorizationRequest admittedAuthorizationRequest() {
        ApplicationAuthorizationRequest request = new ApplicationAuthorizationRequest();
        request.setApplicationCode("audit-test");
        request.setAdmitted(Boolean.TRUE);
        request.setRoles(Collections.<String>emptyList());
        request.setPagePermissions(Collections.<String>emptyList());
        request.setApiPermissions(Collections.<String>emptyList());
        request.setDataGrantDocument(null);
        request.setManifestVersion("audit-test-manifest");
        request.setManifestDigest("audit-test-manifest-digest");
        return request;
    }

    private void assertRevocationCause(TokenEventCause cause) {
        log.info("验证撤销原因 {} 已作为脱敏审计记录下发", cause);
        ServerTokenAuditRecord record = onlyRecord(TokenEventType.REVOKED);
        assertEquals(cause, record.getCause());
        assertNull(record.getTokenValue());
        assertNull(record.getActive());
    }

    private ServerTokenAuditRecord onlyRecord(TokenEventType type) {
        assertEquals(1, testHandler.records.size());
        ServerTokenAuditRecord record = testHandler.records.get(0);
        assertEquals(type, record.getEventType());
        return record;
    }

    private String requestToken() {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<String, String>();
        body.add("grant_type", "client_credentials");
        ResponseEntity<Map> response = restTemplate.exchange(baseUrl() + "/oauth2/token", HttpMethod.POST,
                new HttpEntity<MultiValueMap<String, String>>(body, basicAuthHeaders()), Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        Object token = response.getBody().get("access_token");
        assertTrue(token instanceof String);
        return (String) token;
    }

    private MultiValueMap<String, String> tokenRequest(String token) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<String, String>();
        body.add("token", token);
        return body;
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private HttpHeaders basicAuthHeaders() {
        HttpHeaders headers = formHeaders();
        headers.setBasicAuth(clientId, clientSecret);
        return headers;
    }

    private HttpHeaders formHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        return headers;
    }
}
