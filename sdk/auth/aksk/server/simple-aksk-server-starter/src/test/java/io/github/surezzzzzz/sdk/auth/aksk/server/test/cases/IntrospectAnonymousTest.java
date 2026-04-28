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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Introspect 端点无认证模式测试
 *
 * <p>验证 introspect.require-authentication=false 时，无需凭证即可调用 introspect。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(
        classes = SimpleAkskServerTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "io.github.surezzzzzz.sdk.auth.aksk.server.introspect.require-authentication=false"
        }
)
class IntrospectAnonymousTest {

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
        ClientInfoResponse client = clientManagementService.createPlatformClient("Introspect Anonymous Test Client");
        clientId = client.getClientId();
        clientSecret = client.getClientSecret();
        accessToken = fetchToken(clientId, clientSecret);
        assertNotNull(accessToken);
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
    void testIntrospectWithoutCredentialsSucceeds() {
        log.info("========== 测试：require-authentication=false 时无需凭证即可 introspect ==========");

        // 使用 Apache HttpClient 避免 HttpURLConnection 对 401 的自动重试行为
        org.apache.http.impl.client.CloseableHttpClient httpClient =
                org.apache.http.impl.client.HttpClients.custom()
                        .disableAuthCaching()
                        .disableAutomaticRetries()
                        .build();

        try {
            org.springframework.http.client.HttpComponentsClientHttpRequestFactory factory =
                    new org.springframework.http.client.HttpComponentsClientHttpRequestFactory(httpClient);

            org.springframework.web.client.RestTemplate rawTemplate = new org.springframework.web.client.RestTemplate(factory);
            rawTemplate.setErrorHandler(new org.springframework.web.client.DefaultResponseErrorHandler() {
                @Override
                public boolean hasError(org.springframework.http.client.ClientHttpResponse response) {
                    return false;
                }
            });

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("token", accessToken);

            ResponseEntity<Map> response = rawTemplate.exchange(
                    "http://localhost:" + port + "/oauth2/introspect",
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    Map.class
            );

            log.info("无认证 introspect 响应状态: {}", response.getStatusCode());
            log.info("无认证 introspect 响应: {}", response.getBody());

            assertEquals(HttpStatus.OK, response.getStatusCode(),
                    "require-authentication=false 时无认证 introspect 应返回 200");
            assertEquals(true, response.getBody().get("active"),
                    "有效 token 应该 active=true");

            log.info("✓ 无认证 introspect 成功，active={}", response.getBody().get("active"));
        } finally {
            try { httpClient.close(); } catch (Exception ignored) {}
        }
    }

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
}
