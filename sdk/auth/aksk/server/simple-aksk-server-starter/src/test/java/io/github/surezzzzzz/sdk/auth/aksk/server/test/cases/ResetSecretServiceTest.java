package io.github.surezzzzzz.sdk.auth.aksk.server.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.server.controller.response.ClientInfoResponse;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.response.ResetSecretResponse;
import io.github.surezzzzzz.sdk.auth.aksk.server.service.ClientManagementService;
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
            if (testClientId != null) {
                clientManagementService.deleteClient(testClientId);
            }
        } catch (Exception e) {
            log.warn("清理测试Client失败: {}", e.getMessage());
        }

        log.info("测试数据清理完成");
    }

    @Test
    void testResetSecretWithRevokeTokens() {
        log.info("测试重置Secret并同时撤销Token");

        ResetSecretResponse response = clientManagementService.resetSecret(testClientId, true);
        assertNotNull(response);

        assertEquals(testClientId, response.getClientId());
        assertNotNull(response.getClientSecret());
        assertNotEquals(testClientSecret, response.getClientSecret());

        log.info("重置Secret成功，新Secret: {}", response.getClientSecret());
        log.info("测试通过");
    }

    @Test
    void testResetSecretWithoutRevokeTokens() {
        log.info("测试重置Secret但不撤销Token");

        ResetSecretResponse response = clientManagementService.resetSecret(testClientId, false);
        assertNotNull(response);

        assertEquals(testClientId, response.getClientId());
        assertNotNull(response.getClientSecret());
        assertNotEquals(testClientSecret, response.getClientSecret());

        log.info("重置Secret成功，新Secret: {}", response.getClientSecret());
        log.info("测试通过");
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
