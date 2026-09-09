package io.github.surezzzzzz.sdk.auth.iam.server.test.cases;

import io.github.surezzzzzz.sdk.auth.iam.server.test.SimpleIamServerTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * IAM OAuth2 端到端测试
 *
 * <p>覆盖完整的 token 生命周期：
 * <ol>
 *   <li>创建 RegisteredClient（编程式，密钥经 PasswordEncoder 编码）</li>
 *   <li>client_credentials 换取 access_token</li>
 *   <li>introspect 验证 token</li>
 *   <li>错误密钥返回 401</li>
 * </ol>
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(
        classes = SimpleIamServerTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class OAuth2EndToEndTest {

    private static final String TEST_CLIENT_ID = "e2e-test-client-" + UUID.randomUUID().toString().substring(0, 8);
    private static final String TEST_CLIENT_SECRET = "test-secret-12345";

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private RegisteredClientRepository registeredClientRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanupData() {
        // RegisteredClientRepository 无 remove 方法，用 JdbcTemplate 直接清理
        RegisteredClient client = registeredClientRepository.findByClientId(TEST_CLIENT_ID);
        if (client != null) {
            jdbcTemplate.update("DELETE FROM oauth2_authorization WHERE registered_client_id = ?", client.getId());
            jdbcTemplate.update("DELETE FROM oauth2_registered_client WHERE id = ?", client.getId());
            log.info("清理测试客户端: {}", TEST_CLIENT_ID);
        }
    }

    @Test
    @DisplayName("完整 OAuth2 client_credentials 流程")
    void testClientCredentialsFlow() {
        // Step 1: 注册测试客户端（密钥经 PasswordEncoder 编码）
        RegisteredClient testClient = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(TEST_CLIENT_ID)
                .clientSecret(passwordEncoder.encode(TEST_CLIENT_SECRET))
                .clientName("E2E Test Client")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .scope("read")
                .scope("write")
                .build();
        registeredClientRepository.save(testClient);
        log.info("测试客户端注册成功: {}", TEST_CLIENT_ID);

        // Step 2: 换取 access_token
        String tokenUrl = "http://localhost:" + port + "/oauth2/token";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth(TEST_CLIENT_ID, TEST_CLIENT_SECRET);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add(OAuth2ParameterNames.GRANT_TYPE, AuthorizationGrantType.CLIENT_CREDENTIALS.getValue());
        body.add(OAuth2ParameterNames.SCOPE, "read write");

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(tokenUrl, request, Map.class);

        log.info("Token 响应状态：{}", response.getStatusCode());

        // 第三步：验证令牌响应
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());

        Map<String, Object> tokenBody = response.getBody();
        assertNotNull(tokenBody.get("access_token"), "access_token 不应为空");
        assertEquals("Bearer", tokenBody.get("token_type"), "token_type 应为 Bearer");
        assertNotNull(tokenBody.get("expires_in"), "expires_in 不应为空");
        assertNotNull(tokenBody.get("scope"), "scope 不应为空");

        String accessToken = (String) tokenBody.get("access_token");

        // 第四步：调用自省端点验证令牌有效
        String introspectUrl = "http://localhost:" + port + "/oauth2/introspect";
        HttpHeaders introspectHeaders = new HttpHeaders();
        introspectHeaders.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        introspectHeaders.setBasicAuth(TEST_CLIENT_ID, TEST_CLIENT_SECRET);

        MultiValueMap<String, String> introspectBody = new LinkedMultiValueMap<>();
        introspectBody.add("token", accessToken);

        HttpEntity<MultiValueMap<String, String>> introspectRequest = new HttpEntity<>(introspectBody, introspectHeaders);
        ResponseEntity<Map> introspectResponse = restTemplate.postForEntity(introspectUrl, introspectRequest, Map.class);

        log.info("Introspect 响应状态：{}", introspectResponse.getStatusCode());

        assertEquals(HttpStatus.OK, introspectResponse.getStatusCode());
        Map<String, Object> introspectResult = introspectResponse.getBody();
        assertNotNull(introspectResult);
        assertEquals(Boolean.TRUE, introspectResult.get("active"), "token 应为 active");
        assertEquals(TEST_CLIENT_ID, introspectResult.get("client_id"), "client_id 应匹配");

        log.info("完整 OAuth2 client_credentials 流程测试通过");
    }

    @Test
    @DisplayName("错误密钥应返回 401")
    void testWrongSecretReturns401() {
        // Step 1: 注册测试客户端
        RegisteredClient testClient = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(TEST_CLIENT_ID)
                .clientSecret(passwordEncoder.encode(TEST_CLIENT_SECRET))
                .clientName("E2E Wrong Secret Test Client")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .scope("read")
                .build();
        registeredClientRepository.save(testClient);

        // 第二步：使用错误密钥请求令牌
        String tokenUrl = "http://localhost:" + port + "/oauth2/token";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth(TEST_CLIENT_ID, "wrong-secret");

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add(OAuth2ParameterNames.GRANT_TYPE, AuthorizationGrantType.CLIENT_CREDENTIALS.getValue());
        body.add(OAuth2ParameterNames.SCOPE, "read");

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(tokenUrl, request, Map.class);

        log.info("错误密钥请求 Token 响应: {}", response.getStatusCode());

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode(),
                "错误密钥应返回 401 Unauthorized");
    }
}
