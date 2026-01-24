package io.github.surezzzzzz.sdk.auth.aksk.server.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.server.controller.response.ClientInfoResponse;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.response.PageResponse;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.response.TokenInfoResponse;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.response.TokenStatisticsResponse;
import io.github.surezzzzzz.sdk.auth.aksk.server.repository.OAuth2AuthorizationRepository;
import io.github.surezzzzzz.sdk.auth.aksk.server.repository.OAuth2RegisteredClientEntityRepository;
import io.github.surezzzzzz.sdk.auth.aksk.server.service.ClientManagementService;
import io.github.surezzzzzz.sdk.auth.aksk.server.test.SimpleAkskServerTestApplication;
import io.github.surezzzzzz.sdk.auth.aksk.server.test.helper.JwtTokenTestHelper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Token管理集成测试
 * <p>
 * 测试Token管理REST API的完整流程：
 * - 创建AKSK → 换取Token → 查询Token → 删除Token
 * - 测试过滤、搜索、分页功能
 * - 测试统计信息
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(
        classes = SimpleAkskServerTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class TokenManagementIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ClientManagementService clientManagementService;

    @Autowired
    private OAuth2RegisteredClientEntityRepository clientRepository;

    @Autowired
    private OAuth2AuthorizationRepository authorizationRepository;

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    private String jwtToken;
    private String bootstrapClientId;
    private String bootstrapClientSecret;
    private String testClientId1;
    private String testClientSecret1;
    private String testClientId2;
    private String testClientSecret2;
    private String tokenId1;
    private String tokenId2;

    /**
     * 每个测试方法执行前准备测试数据
     */
    @BeforeEach
    void setupTestData() {
        log.info("准备测试数据...");

        // 使用Service创建bootstrap client（不走API，避免循环依赖）
        ClientInfoResponse bootstrapClient = clientManagementService.createPlatformClient("Bootstrap Test Client");
        bootstrapClientId = bootstrapClient.getClientId();
        bootstrapClientSecret = bootstrapClient.getClientSecret();

        log.info("Bootstrap client创建成功: {}", bootstrapClientId);

        // 获取JWT token
        jwtToken = JwtTokenTestHelper.getTokenByClientCredentials(
                restTemplate, port, bootstrapClientId, bootstrapClientSecret
        );

        log.info("JWT token已获取");

        // 创建测试用的Client
        ClientInfoResponse client1 = clientManagementService.createPlatformClient(
                "Integration Test Client 1",
                Arrays.asList("read", "write")
        );
        testClientId1 = client1.getClientId();
        testClientSecret1 = client1.getClientSecret();

        ClientInfoResponse client2 = clientManagementService.createUserClient(
                "integrationUser",
                "Integration User",
                "Integration Test Client 2",
                Arrays.asList("read")
        );
        testClientId2 = client2.getClientId();
        testClientSecret2 = client2.getClientSecret();

        log.info("创建测试Client: {}, {}", testClientId1, testClientId2);

        // 为每个Client换取Token
        obtainToken(testClientId1, testClientSecret1);
        obtainToken(testClientId2, testClientSecret2);

        // 稍等一下确保Token已保存
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            // ignore
        }

        log.info("测试数据准备完成");
    }

    /**
     * 每个测试方法执行后清理数据
     */
    @AfterEach
    void cleanupData() {
        log.info("清理测试数据...");

        try {
            // 清理所有授权记录
            authorizationRepository.deleteAll();
        } catch (Exception e) {
            log.warn("清理授权记录失败: {}", e.getMessage());
        }

        try {
            // 清理测试Client
            if (testClientId1 != null) {
                clientManagementService.deleteClient(testClientId1);
            }
            if (testClientId2 != null) {
                clientManagementService.deleteClient(testClientId2);
            }
        } catch (Exception e) {
            log.warn("清理测试Client失败: {}", e.getMessage());
        }

        // 清理Redis中的测试数据
        if (redisTemplate != null) {
            try {
                Set<String> keys = redisTemplate.keys("sure-auth-aksk:*");
                if (keys != null && !keys.isEmpty()) {
                    redisTemplate.delete(keys);
                    log.info("清理Redis测试数据: {} 条", keys.size());
                }
            } catch (Exception e) {
                log.warn("清理Redis失败: {}", e.getMessage());
            }
        }

        log.info("测试数据清理完成");
    }

    @Test
    void testQueryTokensAPI() {
        log.info("测试查询Token列表API");

        // When
        String url = String.format("http://localhost:%d/api/token?page=1&size=10", port);
        HttpEntity<Void> listRequest = JwtTokenTestHelper.createAuthEntity(jwtToken);
        ResponseEntity<PageResponse<TokenInfoResponse>> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                listRequest,
                new ParameterizedTypeReference<PageResponse<TokenInfoResponse>>() {
                }
        );

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().getData());
        assertTrue(response.getBody().getData().size() >= 2, "应至少有2个Token");
        assertTrue(response.getBody().getTotal() >= 2, "总数应至少为2");

        log.info("查询到 {} 个Token，总数 {}", response.getBody().getData().size(), response.getBody().getTotal());
        log.info("查询Token列表API测试通过");
    }

    @Test
    void testQueryTokensWithFiltersAPI() {
        log.info("测试带过滤条件的Token查询API");

        // When - 按Client ID过滤
        String url = String.format("http://localhost:%d/api/token?clientId=%s&page=1&size=10",
                port, testClientId1);
        HttpEntity<Void> filterRequest = JwtTokenTestHelper.createAuthEntity(jwtToken);
        ResponseEntity<PageResponse<TokenInfoResponse>> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                filterRequest,
                new ParameterizedTypeReference<PageResponse<TokenInfoResponse>>() {
                }
        );

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().getData());
        assertEquals(1, response.getBody().getData().size(), "应只有1个Token匹配");
        assertEquals(testClientId1, response.getBody().getData().get(0).getClientId());

        log.info("按Client ID过滤API测试通过");
    }

    @Test
    void testQueryTokensByClientTypeAPI() {
        log.info("测试按Client类型查询Token API");

        // When - 查询平台级Token (clientType=1)
        String url = String.format("http://localhost:%d/api/token?clientType=1&page=1&size=10", port);
        HttpEntity<Void> typeQueryRequest = JwtTokenTestHelper.createAuthEntity(jwtToken);
        ResponseEntity<PageResponse<TokenInfoResponse>> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                typeQueryRequest,
                new ParameterizedTypeReference<PageResponse<TokenInfoResponse>>() {
                }
        );

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().getData());
        assertTrue(response.getBody().getData().size() >= 1, "应至少有1个平台级Token");
        assertTrue(response.getBody().getData().stream().allMatch(t -> t.getClientType() == 1));

        log.info("查询到 {} 个平台级Token", response.getBody().getData().size());
        log.info("按Client类型查询API测试通过");
    }

    @Test
    void testSearchTokensAPI() {
        log.info("测试搜索Token API");

        // When
        String url = String.format("http://localhost:%d/api/token?search=Integration Test Client&page=1&size=10",
                port);
        HttpEntity<Void> searchRequest = JwtTokenTestHelper.createAuthEntity(jwtToken);
        ResponseEntity<PageResponse<TokenInfoResponse>> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                searchRequest,
                new ParameterizedTypeReference<PageResponse<TokenInfoResponse>>() {
                }
        );

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().getData());
        assertTrue(response.getBody().getData().size() >= 1, "应至少找到1个匹配的Token");

        log.info("搜索到 {} 个Token", response.getBody().getData().size());
        log.info("搜索Token API测试通过");
    }

    @Test
    void testGetTokenByIdAPI() {
        log.info("测试按ID查询Token详情API");

        // Given - 先查询所有Token获取一个ID
        String listUrl = String.format("http://localhost:%d/api/token?page=1&size=1", port);
        HttpEntity<Void> listEntity = JwtTokenTestHelper.createAuthEntity(jwtToken);
        ResponseEntity<PageResponse<TokenInfoResponse>> listResponse = restTemplate.exchange(
                listUrl,
                HttpMethod.GET,
                listEntity,
                new ParameterizedTypeReference<PageResponse<TokenInfoResponse>>() {
                }
        );

        assertTrue(listResponse.getBody().getData().size() > 0);
        String tokenId = listResponse.getBody().getData().get(0).getId();

        // When
        String url = String.format("http://localhost:%d/api/token/%s", port, tokenId);
        HttpEntity<Void> getRequest = JwtTokenTestHelper.createAuthEntity(jwtToken);
        ResponseEntity<TokenInfoResponse> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                getRequest,
                TokenInfoResponse.class
        );

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(tokenId, response.getBody().getId());

        log.info("按ID查询Token详情API测试通过");
    }

    @Test
    void testGetTokenByIdNotFoundAPI() {
        log.info("测试查询不存在的Token API");

        // When
        String url = String.format("http://localhost:%d/api/token/non-existent-token-id", port);
        HttpEntity<Void> notFoundRequest = JwtTokenTestHelper.createAuthEntity(jwtToken);
        ResponseEntity<TokenInfoResponse> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                notFoundRequest,
                TokenInfoResponse.class
        );

        // Then
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());

        log.info("查询不存在Token API测试通过");
    }

    @Test
    void testDeleteTokenAPI() {
        log.info("测试删除Token API");

        // Given - 先查询所有Token获取一个ID
        String listUrl = String.format("http://localhost:%d/api/token?page=1&size=1", port);
        HttpEntity<Void> listEntity = JwtTokenTestHelper.createAuthEntity(jwtToken);
        ResponseEntity<PageResponse<TokenInfoResponse>> listResponse = restTemplate.exchange(
                listUrl,
                HttpMethod.GET,
                listEntity,
                new ParameterizedTypeReference<PageResponse<TokenInfoResponse>>() {
                }
        );

        assertTrue(listResponse.getBody().getData().size() > 0);
        String tokenId = listResponse.getBody().getData().get(0).getId();
        log.info("测试删除Token ID: {}", tokenId);

        // When
        String deleteUrl = String.format("http://localhost:%d/api/token/%s", port, tokenId);
        HttpEntity<Void> deleteEntity = JwtTokenTestHelper.createAuthEntity(jwtToken);
        ResponseEntity<Void> deleteResponse = restTemplate.exchange(
                deleteUrl,
                HttpMethod.DELETE,
                deleteEntity,
                Void.class
        );

        // Then
        assertEquals(HttpStatus.OK, deleteResponse.getStatusCode());

        // Verify it's deleted
        String getUrl = String.format("http://localhost:%d/api/token/%s", port, tokenId);
        HttpEntity<Void> getEntity = JwtTokenTestHelper.createAuthEntity(jwtToken);
        ResponseEntity<TokenInfoResponse> getResponse = restTemplate.exchange(
                getUrl,
                HttpMethod.GET,
                getEntity,
                TokenInfoResponse.class
        );
        assertEquals(HttpStatus.NOT_FOUND, getResponse.getStatusCode());

        log.info("删除Token API测试通过");
    }

    @Test
    void testGetStatisticsAPI() {
        log.info("测试获取Token统计信息API");

        // When
        String url = String.format("http://localhost:%d/api/token/statistics", port);
        HttpEntity<Void> statsRequest = JwtTokenTestHelper.createAuthEntity(jwtToken);
        ResponseEntity<TokenStatisticsResponse> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                statsRequest,
                TokenStatisticsResponse.class
        );

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());

        TokenStatisticsResponse stats = response.getBody();
        assertTrue(stats.getTotalCount() >= 2, "总数应至少为2");
        assertTrue(stats.getActiveCount() >= 2, "有效Token数应至少为2");
        assertTrue(stats.getMysqlCount() >= 2, "MySQL Token数应至少为2");

        log.info("统计信息: 总数={}, 有效={}, MySQL={}, Redis={}, Both={}",
                stats.getTotalCount(),
                stats.getActiveCount(),
                stats.getMysqlCount(),
                stats.getRedisCount(),
                stats.getBothCount());

        log.info("获取统计信息API测试通过");
    }

    @Test
    void testDeleteExpiredTokensAPI() {
        log.info("测试清理过期Token API");

        // When
        String url = String.format("http://localhost:%d/api/token/expired", port);
        HttpEntity<Void> cleanupRequest = JwtTokenTestHelper.createAuthEntity(jwtToken);
        ResponseEntity<Map> response = restTemplate.exchange(
                url,
                HttpMethod.DELETE,
                cleanupRequest,
                Map.class
        );

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().containsKey("deletedCount"));
        assertTrue(response.getBody().containsKey("message"));

        int deletedCount = (Integer) response.getBody().get("deletedCount");
        log.info("清理了 {} 个过期Token", deletedCount);

        log.info("清理过期Token API测试通过");
    }

    @Test
    void testPaginationAPI() {
        log.info("测试分页API");

        // When - 第1页
        String url1 = String.format("http://localhost:%d/api/token?page=1&size=1", port);
        HttpEntity<Void> page1Request = JwtTokenTestHelper.createAuthEntity(jwtToken);
        ResponseEntity<PageResponse<TokenInfoResponse>> response1 = restTemplate.exchange(
                url1,
                HttpMethod.GET,
                page1Request,
                new ParameterizedTypeReference<PageResponse<TokenInfoResponse>>() {
                }
        );

        // Then
        assertEquals(HttpStatus.OK, response1.getStatusCode());
        assertNotNull(response1.getBody());
        assertNotNull(response1.getBody().getData());
        assertEquals(1, response1.getBody().getData().size());

        // When - 第2页
        String url2 = String.format("http://localhost:%d/api/token?page=2&size=1", port);
        HttpEntity<Void> page2Request = JwtTokenTestHelper.createAuthEntity(jwtToken);
        ResponseEntity<PageResponse<TokenInfoResponse>> response2 = restTemplate.exchange(
                url2,
                HttpMethod.GET,
                page2Request,
                new ParameterizedTypeReference<PageResponse<TokenInfoResponse>>() {
                }
        );

        // Then
        assertEquals(HttpStatus.OK, response2.getStatusCode());
        assertNotNull(response2.getBody());
        assertNotNull(response2.getBody().getData());
        assertTrue(response2.getBody().getData().size() >= 1);

        // 验证两页数据不同
        if (response2.getBody().getData().size() > 0) {
            assertNotEquals(response1.getBody().getData().get(0).getId(),
                    response2.getBody().getData().get(0).getId());
        }

        log.info("分页API测试通过");
    }

    @Test
    void testCompleteTokenLifecycleAPI() {
        log.info("测试完整Token生命周期API");

        // Step 1: 创建新Client
        ClientInfoResponse newClient = clientManagementService.createPlatformClient(
                "Lifecycle Test Client",
                Arrays.asList("read", "write")
        );
        String clientId = newClient.getClientId();
        String clientSecret = newClient.getClientSecret();
        log.info("创建测试Client: {}", clientId);

        // Step 2: 换取Token
        String accessToken = obtainToken(clientId, clientSecret);
        assertNotNull(accessToken, "Token不应为null");
        log.info("换取Token成功");

        // 等待Token保存
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            // ignore
        }

        // Step 3: 查询Token列表，验证新Token存在
        String listUrl = String.format("http://localhost:%d/api/token?clientId=%s", port, clientId);
        HttpEntity<Void> listEntity = JwtTokenTestHelper.createAuthEntity(jwtToken);
        ResponseEntity<PageResponse<TokenInfoResponse>> listResponse = restTemplate.exchange(
                listUrl,
                HttpMethod.GET,
                listEntity,
                new ParameterizedTypeReference<PageResponse<TokenInfoResponse>>() {
                }
        );
        assertEquals(HttpStatus.OK, listResponse.getStatusCode());
        assertNotNull(listResponse.getBody());
        assertNotNull(listResponse.getBody().getData());
        assertTrue(listResponse.getBody().getData().size() > 0, "应找到新创建的Token");

        String tokenId = listResponse.getBody().getData().get(0).getId();
        log.info("查询到Token ID: {}", tokenId);

        // Step 4: 查询Token详情
        String getUrl = String.format("http://localhost:%d/api/token/%s", port, tokenId);
        HttpEntity<Void> getEntity = JwtTokenTestHelper.createAuthEntity(jwtToken);
        ResponseEntity<TokenInfoResponse> getResponse = restTemplate.exchange(
                getUrl,
                HttpMethod.GET,
                getEntity,
                TokenInfoResponse.class
        );
        assertEquals(HttpStatus.OK, getResponse.getStatusCode());
        assertEquals(tokenId, getResponse.getBody().getId());
        log.info("查询Token详情成功");

        // Step 5: 删除Token
        String deleteUrl = String.format("http://localhost:%d/api/token/%s", port, tokenId);
        HttpEntity<Void> deleteEntity = JwtTokenTestHelper.createAuthEntity(jwtToken);
        ResponseEntity<Void> deleteResponse = restTemplate.exchange(
                deleteUrl,
                HttpMethod.DELETE,
                deleteEntity,
                Void.class
        );
        assertEquals(HttpStatus.OK, deleteResponse.getStatusCode());
        log.info("删除Token成功");

        // Step 6: 验证Token已删除
        HttpEntity<Void> verifyEntity = JwtTokenTestHelper.createAuthEntity(jwtToken);
        ResponseEntity<TokenInfoResponse> verifyResponse = restTemplate.exchange(
                getUrl,
                HttpMethod.GET,
                verifyEntity,
                TokenInfoResponse.class
        );
        assertEquals(HttpStatus.NOT_FOUND, verifyResponse.getStatusCode());
        log.info("验证Token已删除");

        // 清理测试Client
        clientManagementService.deleteClient(clientId);

        log.info("完整Token生命周期API测试通过");
    }

    /**
     * 辅助方法：换取Token
     */
    private String obtainToken(String clientId, String clientSecret) {
        try {
            String tokenUrl = "http://localhost:" + port + "/oauth2/token";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.setBasicAuth(clientId, clientSecret);

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("grant_type", "client_credentials");
            body.add("scope", "read write");

            HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(tokenUrl, requestEntity, Map.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                String accessToken = (String) response.getBody().get("access_token");
                log.info("为Client {} 换取Token成功", clientId);
                return accessToken;
            }
        } catch (Exception e) {
            log.error("换取Token失败: {}", e.getMessage());
        }
        return null;
    }
}
