package io.github.surezzzzzz.sdk.auth.aksk.server.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.core.model.TokenInfo;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.request.TokenQueryRequest;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.response.ClientInfoResponse;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.response.PageResponse;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.response.ResetSecretResponse;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.response.TokenInfoResponse;
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
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Arrays;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试 resetSecret() 方法的各种场景
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleAkskServerTestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ResetSecretServiceTest {

    @LocalServerPort
    private int port;

    @Autowired
    private ClientManagementService clientManagementService;

    @Autowired
    private io.github.surezzzzzz.sdk.auth.aksk.server.service.TokenManagementService tokenManagementService;

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
                "Reset Secret Test Client",
                Arrays.asList("read", "write")
        );
        testClientId = client.getClientId();
        testClientSecret = client.getClientSecret();

        log.info("创建测试Client: {}, Secret: {}", testClientId, testClientSecret);
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
    void testResetSecretWithRevokeTokens() {
        log.info("测试重置Secret并同时撤销Token");

        // 先创建 token
        io.github.surezzzzzz.sdk.auth.aksk.server.test.helper.JwtTokenTestHelper.getTokenByClientCredentials(
                new org.springframework.boot.test.web.client.TestRestTemplate(),
                port, testClientId, testClientSecret
        );

        // 确认有 ACTIVE token
        TokenQueryRequest query = new TokenQueryRequest();
        query.setClientId(testClientId);
        query.setPage(1);
        query.setSize(10);
        PageResponse<TokenInfoResponse> before = tokenManagementService.queryTokens(query);
        long activeBefore = before.getData().stream()
                .filter(t -> t.getStatus() == TokenInfo.TokenStatus.ACTIVE)
                .count();
        assertTrue(activeBefore >= 1, "重置前应至少有 1 个 ACTIVE token");

        ResetSecretResponse response = clientManagementService.resetSecret(testClientId, true);
        assertNotNull(response);
        assertEquals(testClientId, response.getClientId());
        assertNotNull(response.getClientSecret());
        assertNotEquals(testClientSecret, response.getClientSecret());

        // 验证 token 已被撤销
        PageResponse<TokenInfoResponse> after = tokenManagementService.queryTokens(query);
        long activeAfter = after.getData().stream()
                .filter(t -> t.getStatus() == TokenInfo.TokenStatus.ACTIVE)
                .count();
        assertEquals(0, activeAfter, "revokeTokens=true 时不应有 ACTIVE token");

        log.info("重置Secret成功（撤销Token），新Secret长度: {}", response.getClientSecret().length());
    }

    @Test
    void testResetSecretWithoutRevokeTokens() {
        log.info("测试重置Secret但不撤销Token");

        // 先创建 token
        io.github.surezzzzzz.sdk.auth.aksk.server.test.helper.JwtTokenTestHelper.getTokenByClientCredentials(
                new org.springframework.boot.test.web.client.TestRestTemplate(),
                port, testClientId, testClientSecret
        );

        // 确认有 ACTIVE token
        TokenQueryRequest query = new TokenQueryRequest();
        query.setClientId(testClientId);
        query.setPage(1);
        query.setSize(10);
        PageResponse<TokenInfoResponse> before = tokenManagementService.queryTokens(query);
        long activeBefore = before.getData().stream()
                .filter(t -> t.getStatus() == TokenInfo.TokenStatus.ACTIVE)
                .count();
        assertTrue(activeBefore >= 1, "重置前应至少有 1 个 ACTIVE token");

        ResetSecretResponse response = clientManagementService.resetSecret(testClientId, false);
        assertNotNull(response);
        assertEquals(testClientId, response.getClientId());
        assertNotNull(response.getClientSecret());
        assertNotEquals(testClientSecret, response.getClientSecret());

        // 验证 token 仍然有效
        PageResponse<TokenInfoResponse> after = tokenManagementService.queryTokens(query);
        long activeAfter = after.getData().stream()
                .filter(t -> t.getStatus() == TokenInfo.TokenStatus.ACTIVE)
                .count();
        assertEquals(activeBefore, activeAfter, "revokeTokens=false 时 token 应仍有效");

        log.info("重置Secret成功（不撤销Token），新Secret长度: {}", response.getClientSecret().length());
    }

    @Test
    void testResetSecretClientNotFound() {
        log.info("测试重置不存在Client的Secret");

        String nonExistentClientId = "AKP" + System.currentTimeMillis();

        try {
            ResetSecretResponse response = clientManagementService.resetSecret(nonExistentClientId, true);
            fail("应抛出ClientException");
        } catch (Exception e) {
            log.info("捕获到预期的异常: {}", e.getMessage());
            assertTrue(e.getMessage().contains("不存在") || e.getMessage().contains("not found"), "异常信息应包含不存在或not found");
        }
    }
}
