package io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.test.cases;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.manager.RedisTokenManager;
import io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.test.SimpleAkskRedisTokenManagerTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RedisTokenManager 端到端测试
 * <p>
 * 测试从 Redis 获取 Token，然后调用 Server API 的完整流程
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleAkskRedisTokenManagerTestApplication.class)
class RedisTokenManagerEndToEndTest {

    @Autowired
    private RedisTokenManager tokenManager;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Value("${io.github.surezzzzzz.sdk.auth.aksk.client.server-url}")
    private String serverUrl;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        log.info("======================================");
        log.info("清理 Redis 测试数据");
        log.info("======================================");
        cleanupTestKeys();
    }

    @AfterEach
    void tearDown() {
        log.info("======================================");
        log.info("测试结束，清理 Redis 测试数据");
        log.info("======================================");
        cleanupTestKeys();
    }

    /**
     * 清理测试相关的 Redis Key
     */
    private void cleanupTestKeys() {
        String pattern = "sure-auth-aksk-client:redis-token-manager-test:*";
        Set<String> keys = stringRedisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            log.info("删除测试 Key: {}", keys);
            stringRedisTemplate.delete(keys);
        }
    }

    @Test
    @DisplayName("端到端测试 - 获取 Token 并调用 Server API 查询 Client 列表")
    void testEndToEndGetTokenAndQueryClients() {
        log.info("======================================");
        log.info("端到端测试 - 获取 Token 并调用 Server API 查询 Client 列表");
        log.info("======================================");

        // Step 1: 使用 RedisTokenManager 获取 Token
        log.info("Step 1: 使用 RedisTokenManager 获取 Token");
        String token = tokenManager.getToken();
        log.info("获取的 Token: {}", token);
        assertNotNull(token, "Token 不应为 null");
        assertTrue(token.startsWith("eyJ"), "Token 应该是 JWT 格式");

        // Step 2: 使用 Token 调用 Server API
        log.info("Step 2: 使用 Token 调用 Server API - 批量查询 Client 信息");
        String apiUrl = serverUrl + "/api/client";

        // 构造请求头
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> requestEntity = new HttpEntity<>(headers);

        try {
            // 调用 API（不传 clientIds 参数，测试分页查询）
            String queryUrl = apiUrl + "?page=1&size=10";
            log.info("调用 API: {}", queryUrl);

            ResponseEntity<String> response = restTemplate.exchange(
                    queryUrl,
                    HttpMethod.GET,
                    requestEntity,
                    String.class
            );

            // Step 3: 验证响应
            log.info("Step 3: 验证 API 响应");
            log.info("响应状态码: {}", response.getStatusCode());
            log.info("响应内容: {}", response.getBody());

            assertEquals(HttpStatus.OK, response.getStatusCode(), "API 调用应成功返回 200");
            assertNotNull(response.getBody(), "响应内容不应为 null");

            // 解析响应 JSON
            JsonNode jsonResponse = objectMapper.readTree(response.getBody());
            log.info("响应 JSON 结构: {}", jsonResponse.toPrettyString());

            // 验证响应结构（根据实际 API 响应调整）
            assertTrue(jsonResponse.has("content") || jsonResponse.has("data") || jsonResponse.has("clients"),
                    "响应应包含数据字段");

            log.info("======================================");
            log.info("端到端测试成功：Token 获取 → API 调用 → 响应验证");
            log.info("======================================");

        } catch (HttpClientErrorException e) {
            log.error("API 调用失败 - HTTP 错误");
            log.error("状态码: {}", e.getStatusCode());
            log.error("响应内容: {}", e.getResponseBodyAsString());
            fail("API 调用失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("API 调用失败 - 未知错误", e);
            fail("API 调用失败: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("端到端测试 - 获取 Token 并调用 Server API 查询 Token 统计")
    void testEndToEndGetTokenAndQueryTokenStatistics() {
        log.info("======================================");
        log.info("端到端测试 - 获取 Token 并调用 Server API 查询 Token 统计");
        log.info("======================================");

        // Step 1: 使用 RedisTokenManager 获取 Token
        log.info("Step 1: 使用 RedisTokenManager 获取 Token");
        String token = tokenManager.getToken();
        log.info("获取的 Token: {}", token);
        assertNotNull(token, "Token 不应为 null");

        // Step 2: 使用 Token 调用 Server API - 获取 Token 统计
        log.info("Step 2: 使用 Token 调用 Server API - 获取 Token 统计");
        String apiUrl = serverUrl + "/api/token/statistics";

        // 构造请求头
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> requestEntity = new HttpEntity<>(headers);

        try {
            log.info("调用 API: {}", apiUrl);

            ResponseEntity<String> response = restTemplate.exchange(
                    apiUrl,
                    HttpMethod.GET,
                    requestEntity,
                    String.class
            );

            // Step 3: 验证响应
            log.info("Step 3: 验证 API 响应");
            log.info("响应状态码: {}", response.getStatusCode());
            log.info("响应内容: {}", response.getBody());

            assertEquals(HttpStatus.OK, response.getStatusCode(), "API 调用应成功返回 200");
            assertNotNull(response.getBody(), "响应内容不应为 null");

            // 解析响应 JSON
            JsonNode jsonResponse = objectMapper.readTree(response.getBody());
            log.info("响应 JSON 结构: {}", jsonResponse.toPrettyString());

            // 验证响应结构包含统计信息
            assertTrue(jsonResponse.has("totalCount") || jsonResponse.has("total") || jsonResponse.has("count"),
                    "响应应包含统计数据字段");

            log.info("======================================");
            log.info("端到端测试成功：Token 获取 → Token 统计 API 调用 → 响应验证");
            log.info("======================================");

        } catch (HttpClientErrorException e) {
            log.error("API 调用失败 - HTTP 错误");
            log.error("状态码: {}", e.getStatusCode());
            log.error("响应内容: {}", e.getResponseBodyAsString());
            fail("API 调用失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("API 调用失败 - 未知错误", e);
            fail("API 调用失败: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("端到端测试 - 验证无 Token 调用 API 会返回 401")
    void testEndToEndApiCallWithoutTokenShouldReturn401() {
        log.info("======================================");
        log.info("端到端测试 - 验证无 Token 调用 API 会返回 401");
        log.info("======================================");

        String apiUrl = serverUrl + "/api/client?page=1&size=10";

        // 不设置 Authorization 头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> requestEntity = new HttpEntity<>(headers);

        try {
            log.info("调用 API（无 Token）: {}", apiUrl);

            ResponseEntity<String> response = restTemplate.exchange(
                    apiUrl,
                    HttpMethod.GET,
                    requestEntity,
                    String.class
            );

            // 如果到这里说明没有抛异常，检查状态码
            log.info("响应状态码: {}", response.getStatusCode());
            assertTrue(response.getStatusCode().is4xxClientError(),
                    "无 Token 调用应返回 4xx 错误");

        } catch (HttpClientErrorException e) {
            log.info("捕获到预期的 HTTP 错误");
            log.info("状态码: {}", e.getStatusCode());
            log.info("响应内容: {}", e.getResponseBodyAsString());

            // 验证是 401 Unauthorized
            assertEquals(HttpStatus.UNAUTHORIZED, e.getStatusCode(),
                    "无 Token 调用应返回 401 Unauthorized");

            log.info("======================================");
            log.info("端到端测试成功：验证了无 Token 调用 API 返回 401");
            log.info("======================================");
        } catch (Exception e) {
            log.error("测试失败 - 未知错误", e);
            fail("测试失败: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("端到端测试 - 使用 Token 缓存，验证第二次调用使用缓存的 Token")
    void testEndToEndTokenCachingAcrossMultipleApiCalls() {
        log.info("======================================");
        log.info("端到端测试 - 使用 Token 缓存，验证第二次调用使用缓存的 Token");
        log.info("======================================");

        // Step 1: 第一次获取 Token
        log.info("Step 1: 第一次获取 Token（从服务器）");
        String token1 = tokenManager.getToken();
        log.info("第一次获取的 Token: {}", token1);
        assertNotNull(token1, "Token 不应为 null");

        // Step 2: 使用 Token 调用 API
        log.info("Step 2: 使用第一次的 Token 调用 API");
        String apiUrl = serverUrl + "/api/token/statistics";
        HttpHeaders headers1 = new HttpHeaders();
        headers1.setBearerAuth(token1);
        HttpEntity<String> requestEntity1 = new HttpEntity<>(headers1);

        ResponseEntity<String> response1 = restTemplate.exchange(
                apiUrl,
                HttpMethod.GET,
                requestEntity1,
                String.class
        );
        assertEquals(HttpStatus.OK, response1.getStatusCode(), "第一次 API 调用应成功");
        log.info("第一次 API 调用成功");

        // Step 3: 第二次获取 Token（应从缓存）
        log.info("Step 3: 第二次获取 Token（应从 Redis 缓存）");
        String token2 = tokenManager.getToken();
        log.info("第二次获取的 Token: {}", token2);
        assertNotNull(token2, "Token 不应为 null");

        // Step 4: 验证两次获取的 Token 相同
        assertEquals(token1, token2, "两次获取的 Token 应相同（来自缓存）");
        log.info("验证通过：两次获取的 Token 相同，使用了缓存");

        // Step 5: 使用第二次的 Token 调用 API
        log.info("Step 5: 使用第二次的 Token 调用 API");
        HttpHeaders headers2 = new HttpHeaders();
        headers2.setBearerAuth(token2);
        HttpEntity<String> requestEntity2 = new HttpEntity<>(headers2);

        ResponseEntity<String> response2 = restTemplate.exchange(
                apiUrl,
                HttpMethod.GET,
                requestEntity2,
                String.class
        );
        assertEquals(HttpStatus.OK, response2.getStatusCode(), "第二次 API 调用应成功");
        log.info("第二次 API 调用成功");

        log.info("======================================");
        log.info("端到端测试成功：验证了 Token 缓存机制");
        log.info("======================================");
    }
}
