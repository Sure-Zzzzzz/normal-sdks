package io.github.surezzzzzz.sdk.auth.aksk.server.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.server.constant.SimpleAkskServerConstant;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.response.ClientInfoResponse;
import io.github.surezzzzzz.sdk.auth.aksk.server.repository.OAuth2AuthorizationEntityRepository;
import io.github.surezzzzzz.sdk.auth.aksk.server.repository.OAuth2RegisteredClientEntityRepository;
import io.github.surezzzzzz.sdk.auth.aksk.server.service.ClientManagementService;
import io.github.surezzzzzz.sdk.auth.aksk.server.test.SimpleAkskServerTestApplication;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterRedisKeyConstant;
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
 * OAuth2 限流端到端测试
 *
 * @author surezzzzzz
 */
@Slf4j
// OAuth2 主限流维度由 AkskOAuth2ClientIdKeyProvider 提供，优先从 Basic Auth/client_id 提取 clientId。
// E2E 只覆盖 count=1/1s 用于稳定触发 429，不覆盖 key-strategy，避免把 fallback 误读成主限流维度。
@SpringBootTest(
        classes = SimpleAkskServerTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "io.github.surezzzzzz.sdk.auth.aksk.server.limiter.oauth2.token.algorithm=sliding",
                "io.github.surezzzzzz.sdk.auth.aksk.server.limiter.oauth2.token.limits[0].count=1",
                "io.github.surezzzzzz.sdk.auth.aksk.server.limiter.oauth2.token.limits[0].window=1",
                "io.github.surezzzzzz.sdk.auth.aksk.server.limiter.oauth2.token.limits[0].unit=SECONDS"
        }
)
class OAuth2LimiterEndToEndTest {

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

    @BeforeEach
    void setUp() {
        cleanupData();
    }

    @AfterEach
    void tearDown() {
        cleanupData();
    }

    @Test
    void testTokenEndpointRateLimitedBySlidingWindow() {
        log.info("========== 测试 OAuth2 token 端点滑动窗口真实 Redis 限流 ==========");

        ClientInfoResponse firstClient = clientManagementService.createPlatformClient("OAuth2 Limiter E2E Client A");
        ClientInfoResponse secondClient = clientManagementService.createPlatformClient("OAuth2 Limiter E2E Client B");
        HttpEntity<MultiValueMap<String, String>> firstClientRequest = buildTokenRequest(
                firstClient.getClientId(), firstClient.getClientSecret());
        HttpEntity<MultiValueMap<String, String>> secondClientRequest = buildTokenRequest(
                secondClient.getClientId(), secondClient.getClientSecret());
        String tokenUrl = "http://localhost:" + port + "/oauth2/token";

        ResponseEntity<Map> firstResponse = restTemplate.exchange(tokenUrl, HttpMethod.POST, firstClientRequest, Map.class);
        ResponseEntity<Map> secondResponse = restTemplate.exchange(tokenUrl, HttpMethod.POST, firstClientRequest, Map.class);
        ResponseEntity<Map> otherClientResponse = restTemplate.exchange(tokenUrl, HttpMethod.POST, secondClientRequest, Map.class);

        log.info("第一次 token 响应: status={}, headers={}, body={}",
                firstResponse.getStatusCode(), firstResponse.getHeaders(), firstResponse.getBody());
        log.info("同 clientId 第二次 token 响应: status={}, headers={}, body={}",
                secondResponse.getStatusCode(), secondResponse.getHeaders(), secondResponse.getBody());
        log.info("不同 clientId token 响应: status={}, headers={}, body={}",
                otherClientResponse.getStatusCode(), otherClientResponse.getHeaders(), otherClientResponse.getBody());

        assertEquals(HttpStatus.OK, firstResponse.getStatusCode(), "第一次请求应通过限流并成功获取 token");
        assertNotNull(firstResponse.getBody(), "第一次响应体不应为空");
        assertNotNull(firstResponse.getBody().get(SimpleAkskServerConstant.OAUTH2_RESPONSE_ACCESS_TOKEN),
                "第一次响应应包含 access_token");
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, secondResponse.getStatusCode(), "同 clientId 第二次请求应触发 429 限流");
        assertEquals(HttpStatus.OK, otherClientResponse.getStatusCode(), "不同 clientId 应独立计数并允许请求");
        assertNotNull(otherClientResponse.getBody(), "不同 clientId 响应体不应为空");
        assertNotNull(otherClientResponse.getBody().get(SimpleAkskServerConstant.OAUTH2_RESPONSE_ACCESS_TOKEN),
                "不同 clientId 响应应包含 access_token");
        assertEquals("1", secondResponse.getHeaders().getFirst(SmartRedisLimiterConstant.HEADER_RETRY_AFTER),
                "429 响应应包含 Retry-After=1");
        assertEquals("1", secondResponse.getHeaders().getFirst(SmartRedisLimiterConstant.HEADER_X_RATELIMIT_LIMIT),
                "429 响应应包含限流阈值");
        assertEquals("0", secondResponse.getHeaders().getFirst(SmartRedisLimiterConstant.HEADER_X_RATELIMIT_REMAINING),
                "429 响应应包含剩余配额 0");

        Set<String> limiterKeys = redisTemplate.keys(SmartRedisLimiterRedisKeyConstant.KEY_PREFIX + "*");
        log.info("smart-limiter Redis keys: {}", limiterKeys);
        assertNotNull(limiterKeys, "限流 Redis key 查询结果不应为 null");
        assertFalse(limiterKeys.isEmpty(), "真实 Redis 中应写入 smart-limiter 限流 key");
        assertTrue(limiterKeys.stream().anyMatch(key -> key.contains("client:" + firstClient.getClientId())),
                "OAuth2 token 端点主限流维度应为 clientId provider 生成的 client:{clientId}");
    }

    private HttpEntity<MultiValueMap<String, String>> buildTokenRequest(String clientId, String clientSecret) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth(clientId, clientSecret);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add(SimpleAkskServerConstant.OAUTH2_PARAM_GRANT_TYPE, SimpleAkskServerConstant.AUTHORIZATION_GRANT_TYPE);
        body.add(SimpleAkskServerConstant.OAUTH2_PARAM_SCOPE, "read write");
        return new HttpEntity<>(body, headers);
    }

    private void cleanupData() {
        authorizationRepository.deleteAll();
        clientRepository.deleteAll();
        deleteRedisKeys("sure-auth-aksk:*");
        deleteRedisKeys(SmartRedisLimiterRedisKeyConstant.KEY_PREFIX + "*");
    }

    private void deleteRedisKeys(String pattern) {
        Set<String> keys = redisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
            log.info("清理 Redis keys: pattern={}, count={}", pattern, keys.size());
        }
    }
}
