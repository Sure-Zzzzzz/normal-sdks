package io.github.surezzzzzz.sdk.audit.aksk.server.test.cases;

import io.github.surezzzzzz.sdk.audit.aksk.server.model.ServerTokenAuditRecord;
import io.github.surezzzzzz.sdk.audit.aksk.server.test.ServerTokenAuditListenerTestApplication;
import io.github.surezzzzzz.sdk.audit.aksk.server.test.TestServerTokenAuditHandler;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.response.ClientInfoResponse;
import io.github.surezzzzzz.sdk.auth.aksk.server.event.TokenEventType;
import io.github.surezzzzzz.sdk.auth.aksk.server.repository.OAuth2RegisteredClientEntityRepository;
import io.github.surezzzzzz.sdk.auth.aksk.server.service.ClientManagementService;
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

import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Server Token 审计监听器集成测试
 *
 * <p>启动完整的 aksk-server，现场创建 AKSK，真实调用 /oauth2/token、/oauth2/revoke、
 * /oauth2/introspect，验证 Token 生命周期事件被正确审计。
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@Slf4j
@SpringBootTest(
        classes = ServerTokenAuditListenerTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
public class ServerTokenAuditListenerIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private TestServerTokenAuditHandler testHandler;

    @Autowired
    private ClientManagementService clientManagementService;

    @Autowired
    private OAuth2RegisteredClientEntityRepository clientRepository;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private String clientId;
    private String clientSecret;

    @BeforeEach
    public void setUp() {
        // 现场创建平台级 AKSK
        ClientInfoResponse client = clientManagementService.createPlatformClient("Audit Test Client");
        clientId = client.getClientId();
        clientSecret = client.getClientSecret();
        log.info("Created test client: {}", clientId);
        testHandler.reset(TokenEventType.ISSUED);
    }

    @AfterEach
    public void tearDown() {
        clientRepository.deleteAll();
        Set<String> keys = redisTemplate.keys("sure-auth-aksk:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
        log.info("Test data cleaned up");
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private HttpHeaders basicAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth(clientId, clientSecret);
        return headers;
    }

    /**
     * 获取 token，同时消费掉 ISSUED 审计事件
     */
    private String getTokenAndConsumeIssuedEvent() throws InterruptedException {
        testHandler.reset(TokenEventType.ISSUED);
        MultiValueMap<String, String> body = new LinkedMultiValueMap<String, String>();
        body.add("grant_type", "client_credentials");

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/oauth2/token",
                HttpMethod.POST,
                new HttpEntity<MultiValueMap<String, String>>(body, basicAuthHeaders()),
                String.class
        );
        assertEquals(HttpStatus.OK, response.getStatusCode());

        // 等待 ISSUED 事件消费
        testHandler.latch.await(5, TimeUnit.SECONDS);

        String responseBody = response.getBody();
        assertNotNull(responseBody);
        int start = responseBody.indexOf("\"access_token\":\"") + 16;
        int end = responseBody.indexOf("\"", start);
        return responseBody.substring(start, end);
    }

    @Test
    public void testTokenIssuedAudit() throws InterruptedException {
        log.info("========== 测试：/oauth2/token -> ISSUED 审计事件 ==========");

        MultiValueMap<String, String> body = new LinkedMultiValueMap<String, String>();
        body.add("grant_type", "client_credentials");

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/oauth2/token",
                HttpMethod.POST,
                new HttpEntity<MultiValueMap<String, String>>(body, basicAuthHeaders()),
                String.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());

        boolean received = testHandler.latch.await(5, TimeUnit.SECONDS);
        assertTrue(received, "Should receive ISSUED audit event");

        ServerTokenAuditRecord record = testHandler.records.get(0);
        log.info("ISSUED audit record: {}", record);
        assertEquals(TokenEventType.ISSUED, record.getEventType());
        assertEquals(clientId, record.getClientId());
        assertEquals("platform", record.getClientType());
        assertNotNull(record.getTokenValue());
        assertNotNull(record.getIssuedAt());
        assertNotNull(record.getExpiresAt());
        assertNotNull(record.getEventTime());
        assertNull(record.getActive());
    }

    @Test
    public void testTokenRevokedAudit() throws InterruptedException {
        log.info("========== 测试：/oauth2/revoke -> REVOKED 审计事件 ==========");

        String token = getTokenAndConsumeIssuedEvent();

        // reset，准备捕获 REVOKED 事件
        testHandler.reset(TokenEventType.REVOKED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<String, String>();
        body.add("token", token);

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/oauth2/revoke",
                HttpMethod.POST,
                new HttpEntity<MultiValueMap<String, String>>(body, basicAuthHeaders()),
                String.class
        );
        assertEquals(HttpStatus.OK, response.getStatusCode());

        boolean received = testHandler.latch.await(5, TimeUnit.SECONDS);
        assertTrue(received, "Should receive REVOKED audit event");

        ServerTokenAuditRecord record = testHandler.records.get(0);
        log.info("REVOKED audit record: {}", record);
        assertEquals(TokenEventType.REVOKED, record.getEventType());
        assertEquals(clientId, record.getClientId());
        assertNull(record.getActive());
    }

    @Test
    public void testTokenIntrospectActiveAudit() throws InterruptedException {
        log.info("========== 测试：/oauth2/introspect（有效 token）-> INTROSPECTED active=true ==========");

        String token = getTokenAndConsumeIssuedEvent();
        testHandler.reset(TokenEventType.INTROSPECTED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<String, String>();
        body.add("token", token);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/oauth2/introspect",
                HttpMethod.POST,
                new HttpEntity<MultiValueMap<String, String>>(body, headers),
                String.class
        );
        assertEquals(HttpStatus.OK, response.getStatusCode());
        log.info("Introspect response: {}", response.getBody());

        boolean received = testHandler.latch.await(5, TimeUnit.SECONDS);
        assertTrue(received, "Should receive INTROSPECTED audit event");

        ServerTokenAuditRecord record = testHandler.records.get(0);
        log.info("INTROSPECTED audit record: {}", record);
        assertEquals(TokenEventType.INTROSPECTED, record.getEventType());
        assertEquals(clientId, record.getClientId());
        assertTrue(record.getActive(), "Active token should have active=true");
    }

    @Test
    public void testTokenIntrospectRevokedAudit() throws InterruptedException {
        log.info("========== 测试：introspect 已撤销 token -> INTROSPECTED active=false ==========");

        String token = getTokenAndConsumeIssuedEvent();

        // 撤销 token
        testHandler.reset(TokenEventType.REVOKED);
        MultiValueMap<String, String> revokeBody = new LinkedMultiValueMap<String, String>();
        revokeBody.add("token", token);
        restTemplate.exchange(
                baseUrl() + "/oauth2/revoke",
                HttpMethod.POST,
                new HttpEntity<MultiValueMap<String, String>>(revokeBody, basicAuthHeaders()),
                String.class
        );
        testHandler.latch.await(5, TimeUnit.SECONDS);

        // introspect 已撤销的 token
        testHandler.reset(TokenEventType.INTROSPECTED);
        MultiValueMap<String, String> introspectBody = new LinkedMultiValueMap<String, String>();
        introspectBody.add("token", token);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/oauth2/introspect",
                HttpMethod.POST,
                new HttpEntity<MultiValueMap<String, String>>(introspectBody, headers),
                String.class
        );
        log.info("Introspect revoked token response: {}", response.getBody());

        boolean received = testHandler.latch.await(5, TimeUnit.SECONDS);
        assertTrue(received, "Should receive INTROSPECTED audit event for revoked token");

        ServerTokenAuditRecord record = testHandler.records.get(0);
        log.info("INTROSPECTED (revoked) audit record: {}", record);
        assertEquals(TokenEventType.INTROSPECTED, record.getEventType());
        assertFalse(record.getActive(), "Revoked token should have active=false");
    }
}
