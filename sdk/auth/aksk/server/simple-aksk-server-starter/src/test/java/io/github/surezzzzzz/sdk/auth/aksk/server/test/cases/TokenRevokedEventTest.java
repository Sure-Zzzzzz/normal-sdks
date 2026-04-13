package io.github.surezzzzzz.sdk.auth.aksk.server.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.server.controller.response.ClientInfoResponse;
import io.github.surezzzzzz.sdk.auth.aksk.server.event.TokenRevokedEvent;
import io.github.surezzzzzz.sdk.auth.aksk.server.repository.OAuth2RegisteredClientEntityRepository;
import io.github.surezzzzzz.sdk.auth.aksk.server.service.ClientManagementService;
import io.github.surezzzzzz.sdk.auth.aksk.server.test.SimpleAkskServerTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.context.event.EventListener;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TokenRevokedEvent 发布测试
 *
 * <p>验证调用 /oauth2/revoke 后 TokenRevokedEvent 被正确发布。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(
        classes = SimpleAkskServerTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class TokenRevokedEventTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ClientManagementService clientManagementService;

    @Autowired
    private OAuth2RegisteredClientEntityRepository clientRepository;

    @Autowired
    private TestTokenRevokedEventListener eventListener;

    private String clientId;
    private String clientSecret;

    @BeforeEach
    void setUp() {
        eventListener.reset();
        ClientInfoResponse client = clientManagementService.createPlatformClient("Token Revocation Test Client");
        clientId = client.getClientId();
        clientSecret = client.getClientSecret();
        log.info("创建测试客户端: clientId={}", clientId);
    }

    @AfterEach
    void tearDown() {
        clientRepository.deleteAll();
    }

    @Test
    void testTokenRevokedEventPublishedOnRevoke() throws Exception {
        log.info("========== 测试：撤销 token 后 TokenRevokedEvent 被发布 ==========");

        // 获取 token
        String token = fetchToken(clientId, clientSecret);
        assertNotNull(token, "Token 不应为 null");
        log.info("获取 token 成功，长度: {}", token.length());

        // 撤销 token
        revokeToken(clientId, clientSecret, token);
        log.info("撤销 token 请求已发送");

        // 等待事件
        boolean received = eventListener.latch.await(10, TimeUnit.SECONDS);
        assertTrue(received, "应该收到 TokenRevokedEvent");
        assertEquals(1, eventListener.events.size(), "应该只收到1个事件");

        TokenRevokedEvent event = eventListener.events.get(0);
        assertEquals(token, event.getTokenValue(), "事件中的 token 值应与撤销的 token 一致");
        assertNotNull(event.getExpiresAt(), "事件中的过期时间不应为 null");
        assertTrue(event.getExpiresAt().isAfter(java.time.Instant.now()), "token 过期时间应在当前时间之后");

        log.info("✓ TokenRevokedEvent 发布成功: expiresAt={}", event.getExpiresAt());
    }

    @Test
    void testIntrospectAfterRevoke() throws Exception {
        log.info("========== 测试：撤销 token 后 introspect 返回 active=false ==========");

        // 获取 token
        String token = fetchToken(clientId, clientSecret);
        assertNotNull(token);

        // 撤销前 introspect，应该 active=true
        Map<String, Object> beforeRevoke = introspectToken(clientId, clientSecret, token);
        log.info("撤销前 introspect 响应: {}", beforeRevoke);
        assertEquals(true, beforeRevoke.get("active"), "撤销前 token 应该是 active");

        // 撤销 token
        revokeToken(clientId, clientSecret, token);

        // 等待撤销写库完成
        Thread.sleep(10000);

        // 撤销后 introspect，应该 active=false
        Map<String, Object> afterRevoke = introspectToken(clientId, clientSecret, token);
        log.info("撤销后 introspect 响应: {}", afterRevoke);
        assertEquals(false, afterRevoke.get("active"), "撤销后 token 应该是 inactive");

        log.info("✓ introspect 正确反映了 token 撤销状态");
    }

    private Map<String, Object> introspectToken(String clientId, String clientSecret, String token) {
        String url = "http://localhost:" + port + "/oauth2/introspect";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth(clientId, clientSecret);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("token", token);

        ResponseEntity<Map> response = restTemplate.exchange(
                url, HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        return response.getBody();
    }

    @Test
    void testNoEventPublishedWithoutRevoke() throws Exception {
        log.info("========== 测试：未撤销 token 时不发布 TokenRevokedEvent ==========");

        // 只获取 token，不撤销
        String token = fetchToken(clientId, clientSecret);
        assertNotNull(token);

        // 等待一段时间，确认没有事件
        boolean received = eventListener.latch.await(1, TimeUnit.SECONDS);
        assertFalse(received, "未撤销时不应收到 TokenRevokedEvent");
        assertEquals(0, eventListener.events.size());

        log.info("✓ 未撤销时无事件发布");
    }

    private String fetchToken(String clientId, String clientSecret) {
        String url = "http://localhost:" + port + "/oauth2/token";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth(clientId, clientSecret);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "client_credentials");

        ResponseEntity<Map> response = restTemplate.exchange(
                url, HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        return (String) response.getBody().get("access_token");
    }

    private void revokeToken(String clientId, String clientSecret, String token) {
        String url = "http://localhost:" + port + "/oauth2/revoke";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth(clientId, clientSecret);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("token", token);

        restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(body, headers), Void.class);
    }

    /**
     * 测试用事件监听器
     */
    @Component
    static class TestTokenRevokedEventListener {

        final List<TokenRevokedEvent> events = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        @EventListener
        public void onTokenRevokedEvent(TokenRevokedEvent event) {
            log.info("收到 TokenRevokedEvent: tokenLength={}, expiresAt={}",
                    event.getTokenValue().length(), event.getExpiresAt());
            events.add(event);
            latch.countDown();
        }

        void reset() {
            events.clear();
            latch = new CountDownLatch(1);
        }
    }
}
