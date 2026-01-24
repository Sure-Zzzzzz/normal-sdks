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
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OAuth2端到端测试
 * <p>
 * 测试完整的OAuth2流程:创建AKSK → 换取Token → 验证Token → Redis存储
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(
        classes = SimpleAkskServerTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class OAuth2EndToEndTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ClientManagementService clientManagementService;

    @Autowired
    private OAuth2RegisteredClientEntityRepository clientRepository;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 每个测试方法执行后清理数据
     */
    @AfterEach
    void cleanupData() {
        log.info("清理测试数据...");
        clientRepository.deleteAll();

        // 清理Redis中的测试数据
        Set<String> keys = redisTemplate.keys("sure-auth-aksk:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
            log.info("清理Redis测试数据: {} 条", keys.size());
        }

        log.info("测试数据清理完成");
    }

    @Test
    void testCompleteOAuth2Flow() {
        log.info("测试完整OAuth2流程");

        // Step 1: 创建平台级AKSK
        ClientInfoResponse clientInfo = clientManagementService.createPlatformClient("OAuth2 Test Client");

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

        log.info("完整OAuth2流程测试通过");
    }

    @Test
    void testTokenStoredInRedis() {
        log.info("测试Token存储到Redis");

        // Step 1: 创建AKSK并换取Token
        ClientInfoResponse clientInfo = clientManagementService.createPlatformClient("Redis Test Client");

        String tokenUrl = "http://localhost:" + port + "/oauth2/token";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth(clientInfo.getClientId(), clientInfo.getClientSecret());

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "client_credentials");
        body.add("scope", "read write");

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(tokenUrl, request, Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        String accessToken = (String) response.getBody().get("access_token");
        log.info("获取Token成功: {}", accessToken.substring(0, Math.min(30, accessToken.length())) + "...");

        // Step 2: 验证Redis中存储了Token相关数据
        Set<String> keys = redisTemplate.keys("sure-auth-aksk:*");
        assertNotNull(keys);
        assertFalse(keys.isEmpty(), "Redis中应该有Token相关数据");

        log.info("Redis中找到 {} 个相关key", keys.size());
        keys.forEach(key -> log.info("Redis Key: {}", key));

        // 验证至少有authorization相关的key
        boolean hasAuthorizationKey = keys.stream()
                .anyMatch(key -> key.contains("oauth2:authorization"));
        assertTrue(hasAuthorizationKey, "Redis中应该有oauth2:authorization相关的key");

        log.info("Token存储到Redis测试通过");
    }

    @Test
    void testDisabledClientCannotGetToken() {
        log.info("测试禁用AKSK后无法换取Token");

        // Step 1: 创建平台级AKSK
        ClientInfoResponse clientInfo = clientManagementService.createPlatformClient("Disabled Test Client");

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

        log.info("禁用AKSK测试通过");
    }

    @Test
    void testEnabledClientCanGetToken() {
        log.info("测试启用AKSK后可以换取Token");

        // Step 1: 创建平台级AKSK
        ClientInfoResponse clientInfo = clientManagementService.createPlatformClient("Enable Test Client");

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

        log.info("启用AKSK测试通过");
    }
}
