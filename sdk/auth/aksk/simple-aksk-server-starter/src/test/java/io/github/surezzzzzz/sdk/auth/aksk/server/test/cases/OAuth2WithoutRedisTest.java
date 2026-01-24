package io.github.surezzzzzz.sdk.auth.aksk.server.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.server.controller.response.ClientInfoResponse;
import io.github.surezzzzzz.sdk.auth.aksk.server.repository.OAuth2RegisteredClientEntityRepository;
import io.github.surezzzzzz.sdk.auth.aksk.server.service.ClientManagementService;
import io.github.surezzzzzz.sdk.auth.aksk.server.test.SimpleAkskServerTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.TestPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OAuth2测试 - 不使用Redis缓存
 * <p>
 * 测试在Redis被disable的情况下，OAuth2流程是否正常工作
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(
        classes = SimpleAkskServerTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@TestPropertySource(properties = {
        "io.github.surezzzzzz.sdk.auth.aksk.server.redis.enabled=false"
})
class OAuth2WithoutRedisTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ClientManagementService clientManagementService;

    @Autowired
    private OAuth2RegisteredClientEntityRepository clientRepository;

    /**
     * 每个测试方法执行后清理数据
     */
    @AfterEach
    void cleanupData() {
        log.info("清理测试数据...");
        clientRepository.deleteAll();
        log.info("测试数据清理完成");
    }

    @Test
    void testOAuth2FlowWithoutRedis() {
        log.info("测试不使用Redis的OAuth2流程");

        // Step 1: 创建平台级AKSK
        ClientInfoResponse clientInfo = clientManagementService.createPlatformClient("No Redis Test Client");

        log.info("创建AKSK成功 - ClientId: {}, ClientSecret: {}",
                clientInfo.getClientId(), clientInfo.getClientSecret());

        assertNotNull(clientInfo.getClientId());
        assertNotNull(clientInfo.getClientSecret());
        assertTrue(clientInfo.getClientId().startsWith("AKP"));
        assertTrue(clientInfo.getClientSecret().startsWith("SK"));

        // Step 2: 使用AKSK换取Token
        String tokenUrl = "http://localhost:" + port + "/oauth2/token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth(clientInfo.getClientId(), clientInfo.getClientSecret());

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "client_credentials");
        body.add("scope", "read write");

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

        log.info("请求Token - URL: {}, ClientId: {}", tokenUrl, clientInfo.getClientId());

        ResponseEntity<Map> response = restTemplate.postForEntity(tokenUrl, request, Map.class);

        log.info("Token响应 - Status: {}, Body: {}", response.getStatusCode(), response.getBody());

        // Step 3: 验证Token响应
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());

        Map<String, Object> tokenResponse = response.getBody();
        assertNotNull(tokenResponse.get("access_token"));
        assertNotNull(tokenResponse.get("token_type"));
        assertNotNull(tokenResponse.get("expires_in"));

        String accessToken = (String) tokenResponse.get("access_token");
        log.info("获取到Access Token: {}", accessToken.substring(0, Math.min(50, accessToken.length())) + "...");

        log.info("不使用Redis的OAuth2流程测试通过");
    }

    @Test
    void testDisabledClientWithoutRedis() {
        log.info("测试不使用Redis时禁用AKSK后无法换取Token");

        // Step 1: 创建平台级AKSK
        ClientInfoResponse clientInfo = clientManagementService.createPlatformClient("Disabled No Redis Test Client");

        log.info("创建AKSK成功 - ClientId: {}", clientInfo.getClientId());

        // Step 2: 禁用AKSK
        clientManagementService.disableClient(clientInfo.getClientId());
        log.info("已禁用AKSK - ClientId: {}", clientInfo.getClientId());

        // Step 3: 尝试使用禁用的AKSK换取Token
        String tokenUrl = "http://localhost:" + port + "/oauth2/token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth(clientInfo.getClientId(), clientInfo.getClientSecret());

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "client_credentials");
        body.add("scope", "read write");

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

        log.info("尝试使用禁用的AKSK请求Token - ClientId: {}", clientInfo.getClientId());

        // Step 4: 验证请求失败（期望抛出401异常）
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(tokenUrl, request, Map.class);
            fail("禁用的AKSK应该返回401 Unauthorized，但请求成功了: " + response.getStatusCode());
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.info("Token请求失败(HttpClientErrorException) - Status: {}", e.getStatusCode());
            assertEquals(HttpStatus.UNAUTHORIZED, e.getStatusCode(), "禁用的AKSK应该返回401 Unauthorized");
        } catch (org.springframework.web.client.ResourceAccessException e) {
            log.info("Token请求失败(ResourceAccessException) - Cause: {}", e.getCause().getClass().getName());
            // ResourceAccessException的cause可能是HttpRetryException
            if (e.getCause() instanceof java.net.HttpRetryException) {
                java.net.HttpRetryException httpRetryException = (java.net.HttpRetryException) e.getCause();
                int responseCode = httpRetryException.responseCode();
                log.info("HTTP Response Code: {}", responseCode);
                assertEquals(401, responseCode, "禁用的AKSK应该返回401 Unauthorized");
            } else {
                throw e;
            }
        }

        log.info("不使用Redis时禁用AKSK测试通过");
    }

    @Test
    void testEnableClientWithoutRedis() {
        log.info("测试不使用Redis时启用AKSK后可以换取Token");

        // Step 1: 创建平台级AKSK
        ClientInfoResponse clientInfo = clientManagementService.createPlatformClient("Enable No Redis Test Client");

        log.info("创建AKSK成功 - ClientId: {}", clientInfo.getClientId());

        // Step 2: 禁用AKSK
        clientManagementService.disableClient(clientInfo.getClientId());
        log.info("已禁用AKSK - ClientId: {}", clientInfo.getClientId());

        // Step 3: 重新启用AKSK
        clientManagementService.enableClient(clientInfo.getClientId());
        log.info("已重新启用AKSK - ClientId: {}", clientInfo.getClientId());

        // Step 4: 使用启用的AKSK换取Token
        String tokenUrl = "http://localhost:" + port + "/oauth2/token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth(clientInfo.getClientId(), clientInfo.getClientSecret());

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "client_credentials");
        body.add("scope", "read write");

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

        log.info("使用启用的AKSK请求Token - ClientId: {}", clientInfo.getClientId());

        ResponseEntity<Map> response = restTemplate.postForEntity(tokenUrl, request, Map.class);

        log.info("Token响应 - Status: {}, Body: {}", response.getStatusCode(), response.getBody());

        // Step 5: 验证请求成功
        assertEquals(HttpStatus.OK, response.getStatusCode(), "启用的AKSK应该能成功获取Token");
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().get("access_token"));

        log.info("不使用Redis时启用AKSK测试通过");
    }
}
