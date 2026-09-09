package io.github.surezzzzzz.sdk.auth.iam.server.test.cases;

import com.nimbusds.jose.JWEObject;
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
 * JWE Token 模式端到端测试
 *
 * <p>验证 {@code token.format=jwe} 时 token 为 JWE 紧凑序列化（5 段）而非 JWT（3 段）。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(
        classes = SimpleIamServerTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "io.github.surezzzzzz.sdk.auth.iam.server.token.format=jwe"
)
class JweTokenEndToEndTest {

    private static final String TEST_CLIENT_ID = "jwe-test-client-" + UUID.randomUUID().toString().substring(0, 8);
    private static final String TEST_CLIENT_SECRET = "jwe-secret-12345";

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
        RegisteredClient client = registeredClientRepository.findByClientId(TEST_CLIENT_ID);
        if (client != null) {
            jdbcTemplate.update("DELETE FROM oauth2_authorization WHERE registered_client_id = ?", client.getId());
            jdbcTemplate.update("DELETE FROM oauth2_registered_client WHERE id = ?", client.getId());
        }
    }

    @Test
    @DisplayName("JWE 模式下 access_token 应为 5 段紧凑序列化")
    void testJweTokenFormat() throws Exception {
        RegisteredClient testClient = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(TEST_CLIENT_ID)
                .clientSecret(passwordEncoder.encode(TEST_CLIENT_SECRET))
                .clientName("JWE Test Client")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .scope("read")
                .build();
        registeredClientRepository.save(testClient);

        String tokenUrl = "http://localhost:" + port + "/oauth2/token";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth(TEST_CLIENT_ID, TEST_CLIENT_SECRET);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add(OAuth2ParameterNames.GRANT_TYPE, AuthorizationGrantType.CLIENT_CREDENTIALS.getValue());
        body.add(OAuth2ParameterNames.SCOPE, "read");

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(tokenUrl, request, Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());

        String accessToken = (String) response.getBody().get("access_token");
        assertNotNull(accessToken, "access_token 不应为空");

        // JWE 紧凑序列化：header.encrypted_key.iv.ciphertext.tag （5 段）
        // JWT 紧凑序列化：header.payload.signature （3 段）
        int segmentCount = accessToken.split("\\.").length;
        String routeKey = JWEObject.parse(accessToken).getHeader().getKeyID();
        assertEquals(5, segmentCount, "JWE token 应为 5 段，实际 " + segmentCount + " 段");
        assertEquals("iam/test-key-iam-2026", routeKey, "JWE外层kid必须是IAM路由键");

        log.info("JWE token 验证通过，token 长度={}, 段数=5，路由键={}", accessToken.length(), routeKey);
    }
}
