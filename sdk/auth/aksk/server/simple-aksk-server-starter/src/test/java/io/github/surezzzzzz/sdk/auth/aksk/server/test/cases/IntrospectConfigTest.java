package io.github.surezzzzzz.sdk.auth.aksk.server.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.server.controller.response.ClientInfoResponse;
import io.github.surezzzzzz.sdk.auth.aksk.server.repository.OAuth2AuthorizationEntityRepository;
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
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Introspect 端点认证配置测试
 *
 * <p>验证 introspect.require-authentication 配置的两种场景：
 * <ul>
 *   <li>true（默认）：需要 Basic Auth，无认证返回 401</li>
 *   <li>false：无需认证，匿名可查</li>
 * </ul>
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(
        classes = SimpleAkskServerTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class IntrospectConfigTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ClientManagementService clientManagementService;

    @Autowired
    private OAuth2RegisteredClientEntityRepository clientRepository;

    @Autowired
    private OAuth2AuthorizationEntityRepository authorizationEntityRepository;

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
        accessToken = fetchToken(clientId, clientSecret);
        assertNotNull(accessToken, "Token 不应为 null");
        log.info("测试准备完成: clientId={}", clientId);
    }

    @AfterEach
    void tearDown() {
        authorizationEntityRepository.deleteAll();
        clientRepository.deleteAll();

        Set<String> keys = redisTemplate.keys("sure-auth-aksk:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Test
    void testIntrospectWithValidCredentials() {
        log.info("========== 测试：携带有效凭证调用 introspect ==========");

        Map<String, Object> result = introspectWithCredentials(clientId, clientSecret, accessToken);
        log.info("introspect 响应: {}", result);

        assertEquals(true, result.get("active"), "有效 token 应该 active=true");
        assertEquals(clientId, result.get("client_id"), "client_id 应该匹配");
        assertNotNull(result.get("scope"), "scope 不应为 null");
        log.info("✓ 携带有效凭证 introspect 成功");
    }

    @Test
    void testIntrospectWithoutCredentialsReturns401() {
        log.info("========== 测试：不携带凭证调用 introspect 应返回 401（默认 require-authentication=true）==========");

        // 默认配置 require-authentication=true，无认证应返回 401
        ResponseEntity<Map> response = introspectWithoutCredentials(accessToken);
        log.info("无认证 introspect 响应状态: {}", response.getStatusCode());

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode(),
                "默认配置下无认证 introspect 应返回 401");
        log.info("✓ 无认证 introspect 正确返回 401");
    }

    @Test
    void testIntrospectWithInvalidCredentialsReturns401() {
        log.info("========== 测试：携带无效凭证调用 introspect 应返回 401 ==========");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth("invalid-client", "invalid-secret");

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("token", accessToken);

        // 使用 getForEntity 方式，避免 TestRestTemplate 对 4xx 的重试行为
        org.springframework.web.client.RestTemplate rawTemplate = restTemplate.getRestTemplate();
        org.springframework.web.client.ResponseErrorHandler originalHandler = rawTemplate.getErrorHandler();
        rawTemplate.setErrorHandler(new org.springframework.web.client.DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(org.springframework.http.client.ClientHttpResponse response) {
                return false; // 不抛异常，让测试自己判断状态码
            }
        });

        try {
            ResponseEntity<Map> response = rawTemplate.exchange(
                    "http://localhost:" + port + "/oauth2/introspect",
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    Map.class
            );
            log.info("无效凭证 introspect 响应状态: {}", response.getStatusCode());
            assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode(),
                    "无效凭证 introspect 应返回 401");
        } finally {
            rawTemplate.setErrorHandler(originalHandler);
        }

        log.info("✓ 无效凭证 introspect 正确返回 401");
    }

    // ==================== 辅助方法 ====================

    private String fetchToken(String clientId, String clientSecret) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth(clientId, clientSecret);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "client_credentials");

        ResponseEntity<Map> response = restTemplate.exchange(
                "http://localhost:" + port + "/oauth2/token",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                Map.class
        );
        return response.getStatusCode().is2xxSuccessful()
                ? (String) response.getBody().get("access_token")
                : null;
    }

    private Map<String, Object> introspectWithCredentials(String clientId, String clientSecret, String token) {
        ResponseEntity<Map> response = restTemplate.exchange(
                "http://localhost:" + port + "/oauth2/introspect",
                HttpMethod.POST,
                buildIntrospectRequest(clientId, clientSecret, token),
                Map.class
        );
        assertEquals(HttpStatus.OK, response.getStatusCode());
        return response.getBody();
    }

    private ResponseEntity<Map> introspectWithoutCredentials(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("token", token);

        return restTemplate.exchange(
                "http://localhost:" + port + "/oauth2/introspect",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                Map.class
        );
    }

    private HttpEntity<MultiValueMap<String, String>> buildIntrospectRequest(
            String clientId, String clientSecret, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth(clientId, clientSecret);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("token", token);

        return new HttpEntity<>(body, headers);
    }
}
