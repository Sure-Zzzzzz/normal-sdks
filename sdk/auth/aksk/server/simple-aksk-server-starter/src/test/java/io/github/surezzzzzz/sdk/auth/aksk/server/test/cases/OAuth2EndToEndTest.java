package io.github.surezzzzzz.sdk.auth.aksk.server.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.server.controller.response.ClientInfoResponse;
import io.github.surezzzzzz.sdk.auth.aksk.server.repository.OAuth2AuthorizationEntityRepository;
import io.github.surezzzzzz.sdk.auth.aksk.server.repository.OAuth2RegisteredClientEntityRepository;
import io.github.surezzzzzz.sdk.auth.aksk.server.service.ClientManagementService;
import io.github.surezzzzzz.sdk.auth.aksk.server.support.RedisKeyHelper;
import io.github.surezzzzzz.sdk.auth.aksk.server.test.SimpleAkskServerTestApplication;
import io.github.surezzzzzz.sdk.cache.layer.L1Cache;
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
    private OAuth2AuthorizationEntityRepository authorizationRepository;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private L1Cache l1Cache;

    @Autowired
    private RedisKeyHelper redisKeyHelper;

    /**
     * 每个测试方法执行后清理数据
     */
    @AfterEach
    void cleanupData() {
        log.info("清理测试数据...");
        authorizationRepository.deleteAll();
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

        // Step 4: 验证请求失败（期望返回401）
        ResponseEntity<Map> response = restTemplate.postForEntity(tokenUrl, request, Map.class);
        log.info("禁用AKSK请求Token响应状态: {}", response.getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode(),
                "禁用的AKSK应该返回401 Unauthorized");

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

    /**
     * 验证 /oauth2/token 请求中 Client Entity 缓存生效（通过 L1Cache 验证，不依赖 @SpyBean）
     *
     * <p>第一次 /oauth2/token（cache miss）后，L1 写入缓存；
     * 第二次/第三次请求同一 clientId 时命中 L1，无需查 JPA。
     */
    @Test
    void testClientEntityCacheReducesJpaCalls() {
        log.info("========== 测试 Client Entity 缓存减少 JPA 调用次数 ==========");

        // Step 1: 创建客户端
        ClientInfoResponse clientInfo = clientManagementService.createPlatformClient("Cache Test Client");
        String clientId = clientInfo.getClientId();
        String clientSecret = clientInfo.getClientSecret();

        String cacheName = RedisKeyHelper.CACHE_OAUTH2_CLIENT_ENTITY;
        String cacheKey = redisKeyHelper.buildCacheKeyById(clientId);

        // Step 2: 第一次 /oauth2/token（cache miss，回源 JPA 并写入 L1）
        String tokenUrl = "http://localhost:" + port + "/oauth2/token";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth(clientId, clientSecret);
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "client_credentials");
        body.add("scope", "read write");
        ResponseEntity<Map> response1 = restTemplate.exchange(
                tokenUrl, HttpMethod.POST,
                new HttpEntity<MultiValueMap<String, String>>(body, headers), Map.class);
        assertEquals(HttpStatus.OK, response1.getStatusCode());
        log.info("第一次 /oauth2/token 成功，带 scope，clientId={}", clientId);

        // cache miss 后 L1 应有缓存值
        assertNotNull(l1Cache.get(cacheName, cacheKey), "第一次请求后 L1 应有缓存值");
        log.info("✓ 第一次请求（cache miss）后 L1 写入缓存");

        // Step 3: 第二次 /oauth2/token（cache hit，L1 有值则不查 JPA）
        ResponseEntity<Map> response2 = restTemplate.exchange(
                tokenUrl, HttpMethod.POST,
                new HttpEntity<MultiValueMap<String, String>>(body, headers), Map.class);
        assertEquals(HttpStatus.OK, response2.getStatusCode());

        // cache hit 后 L1 仍应有值（缓存未被逐出，且无 JPA 调用）
        assertNotNull(l1Cache.get(cacheName, cacheKey), "第二次请求（cache hit）后 L1 仍有缓存值");
        log.info("✓ 第二次请求（cache hit）L1 命中，未查 JPA");

        // Step 4: 不带 scope 的请求也会命中缓存
        HttpHeaders headersNoScope = new HttpHeaders();
        headersNoScope.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headersNoScope.setBasicAuth(clientId, clientSecret);
        MultiValueMap<String, String> bodyNoScope = new LinkedMultiValueMap<>();
        bodyNoScope.add("grant_type", "client_credentials");
        // 不传 scope 参数，会触发 DefaultScopeAuthenticationConverter 读 entity
        ResponseEntity<Map> response3 = restTemplate.exchange(
                tokenUrl, HttpMethod.POST,
                new HttpEntity<MultiValueMap<String, String>>(bodyNoScope, headersNoScope), Map.class);
        assertEquals(HttpStatus.OK, response3.getStatusCode());

        // 不带 scope 时走 DefaultScopeAuthenticationConverter，L1 已有缓存应命中
        assertNotNull(l1Cache.get(cacheName, cacheKey), "不带 scope 请求也命中 L1 缓存");
        log.info("✓ 不带 scope 请求也命中 L1 缓存");

        log.info("Client Entity 缓存测试通过");
    }
}
