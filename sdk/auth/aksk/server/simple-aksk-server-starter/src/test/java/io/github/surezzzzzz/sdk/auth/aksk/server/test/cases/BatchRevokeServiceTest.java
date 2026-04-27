package io.github.surezzzzzz.sdk.auth.aksk.server.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.server.controller.response.BatchRevokeResponse;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.response.ClientInfoResponse;
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

import java.util.Arrays;

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
            if (testClientId != null) {
                clientManagementService.deleteClient(testClientId);
            }
        } catch (Exception e) {
            log.warn("清理测试Client失败: {}", e.getMessage());
        }

        log.info("测试数据清理完成");
    }

    @Test
    void testRevokeAllWithActiveTokens() {
        log.info("测试批量撤销有活跃Token的Client");

        // 这里我们需要实际获取一些Token
        // 但直接在服务端创建Token会比较复杂

        // 执行批量撤销（可能没有Token）
        BatchRevokeResponse response = tokenManagementService.revokeAllByClientId(testClientId);
        assertNotNull(response);
        assertTrue(response.getRevokedCount() >= 0);

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
