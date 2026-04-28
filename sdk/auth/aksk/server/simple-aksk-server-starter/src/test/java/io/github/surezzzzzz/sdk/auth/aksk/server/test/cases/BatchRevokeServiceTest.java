package io.github.surezzzzzz.sdk.auth.aksk.server.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.core.model.TokenInfo;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.response.BatchRevokeResponse;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.response.ClientInfoResponse;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.response.TokenInfoResponse;
import io.github.surezzzzzz.sdk.auth.aksk.server.repository.OAuth2AuthorizationEntityRepository;
import io.github.surezzzzzz.sdk.auth.aksk.server.repository.OAuth2RegisteredClientEntityRepository;
import io.github.surezzzzzz.sdk.auth.aksk.server.service.ClientManagementService;
import io.github.surezzzzzz.sdk.auth.aksk.server.service.TokenManagementService;
import io.github.surezzzzzz.sdk.auth.aksk.server.test.SimpleAkskServerTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Arrays;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试 revokeAllByClientId() 方法的各种场景
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleAkskServerTestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BatchRevokeServiceTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TokenManagementService tokenManagementService;

    @Autowired
    private ClientManagementService clientManagementService;

    @Autowired
    private OAuth2AuthorizationEntityRepository authorizationEntityRepository;

    @Autowired
    private OAuth2RegisteredClientEntityRepository clientRepository;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private String testClientId;
    private String testClientSecret;

    @BeforeEach
    void setup() {
        log.info("准备测试数据...");

        ClientInfoResponse client = clientManagementService.createPlatformClient(
                "Batch Revoke Test Client",
                Arrays.asList("read", "write")
        );
        testClientId = client.getClientId();
        testClientSecret = client.getClientSecret();

        log.info("创建测试Client: {}", testClientId);
    }

    @AfterEach
    void cleanup() {
        log.info("清理测试数据...");

        try {
            authorizationEntityRepository.deleteAll();
        } catch (Exception e) {
            log.warn("清理授权记录失败: {}", e.getMessage());
        }

        try {
            if (testClientId != null) {
                clientManagementService.deleteClient(testClientId);
            }
        } catch (Exception e) {
            log.warn("清理测试Client失败: {}", e.getMessage());
        }

        Set<String> keys = redisTemplate.keys("sure-auth-aksk:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }

        log.info("测试数据清理完成");
    }

    @Test
    void testRevokeAllWithActiveTokens() {
        log.info("测试批量撤销有活跃Token的Client");

        // 通过 OAuth2 端点获取 token，确保有活跃 token
        io.github.surezzzzzz.sdk.auth.aksk.server.test.helper.JwtTokenTestHelper.getTokenByClientCredentials(
                new org.springframework.boot.test.web.client.TestRestTemplate(),
                port, testClientId, testClientSecret
        );

        // 确认 token 为 ACTIVE 状态
        io.github.surezzzzzz.sdk.auth.aksk.server.controller.request.TokenQueryRequest queryRequest =
                new io.github.surezzzzzz.sdk.auth.aksk.server.controller.request.TokenQueryRequest();
        queryRequest.setClientId(testClientId);
        queryRequest.setPage(1);
        queryRequest.setSize(10);
        io.github.surezzzzzz.sdk.auth.aksk.server.controller.response.PageResponse<TokenInfoResponse> page =
                tokenManagementService.queryTokens(queryRequest);
        long activeBefore = page.getData().stream()
                .filter(t -> t.getStatus() == TokenInfo.TokenStatus.ACTIVE)
                .count();
        assertTrue(activeBefore >= 1, "撤销前应至少有 1 个 ACTIVE token");

        // 执行批量撤销
        BatchRevokeResponse response = tokenManagementService.revokeAllByClientId(testClientId);
        assertNotNull(response);
        assertEquals(activeBefore, response.getRevokedCount(), "撤销数量应等于活跃 token 数量");

        // 验证撤销后状态
        io.github.surezzzzzz.sdk.auth.aksk.server.controller.response.PageResponse<TokenInfoResponse> pageAfter =
                tokenManagementService.queryTokens(queryRequest);
        long activeAfter = pageAfter.getData().stream()
                .filter(t -> t.getStatus() == TokenInfo.TokenStatus.ACTIVE)
                .count();
        assertEquals(0, activeAfter, "批量撤销后不应有 ACTIVE token");

        log.info("批量撤销测试通过，实际撤销 {} 个Token", response.getRevokedCount());
    }

    @Test
    void testRevokeAllWithNoActiveTokens() {
        log.info("测试批量撤销没有活跃Token的Client");

        BatchRevokeResponse response = tokenManagementService.revokeAllByClientId(testClientId);
        assertNotNull(response);

        // 因为我们没有创建Token，所以应该没有Token被撤销
        assertEquals(0, response.getRevokedCount());

        log.info("批量撤销无Token的Client测试通过");
    }

    @Test
    void testRevokeAllWithEmptyClientId() {
        log.info("测试批量撤销时传入空clientId");

        try {
            BatchRevokeResponse response = tokenManagementService.revokeAllByClientId(null);
            fail("应抛出ClientException");
        } catch (Exception e) {
            log.info("捕获到预期的异常: {}", e.getMessage());
            assertTrue(e.getMessage().contains("clientId"), "异常信息应包含clientId");
        }

        try {
            BatchRevokeResponse response = tokenManagementService.revokeAllByClientId("");
            fail("应抛出ClientException");
        } catch (Exception e) {
            log.info("捕获到预期的异常: {}", e.getMessage());
            assertTrue(e.getMessage().contains("clientId"), "异常信息应包含clientId");
        }
    }

    @Test
    void testRevokeAllWithNonExistentClientId() {
        log.info("测试批量撤销不存在的Client");

        String nonExistentClientId = "AKP" + System.currentTimeMillis();

        try {
            BatchRevokeResponse response = tokenManagementService.revokeAllByClientId(nonExistentClientId);
            fail("应抛出ClientException");
        } catch (Exception e) {
            log.info("捕获到预期的异常: {}", e.getMessage());
            assertTrue(e.getMessage().contains("不存在") || e.getMessage().contains("not found"), "异常信息应包含不存在或not found");
        }
    }
}
