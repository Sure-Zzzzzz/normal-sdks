package io.github.surezzzzzz.sdk.auth.aksk.server.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.server.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.response.ClientInfoResponse;
import io.github.surezzzzzz.sdk.auth.aksk.server.exception.ClientException;
import io.github.surezzzzzz.sdk.auth.aksk.server.repository.OAuth2RegisteredClientEntityRepository;
import io.github.surezzzzzz.sdk.auth.aksk.server.service.ClientManagementService;
import io.github.surezzzzzz.sdk.auth.aksk.server.test.SimpleAkskServerTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AKU 归属信息修改测试
 *
 * <p>验证 updateOwnerInfo() 的正常流程、平台级拒绝、不存在 Client 等场景。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleAkskServerTestApplication.class)
class UpdateOwnerInfoTest {

    @Autowired
    private ClientManagementService clientManagementService;

    @Autowired
    private OAuth2RegisteredClientEntityRepository clientRepository;

    @AfterEach
    void cleanupData() {
        clientRepository.deleteAll();
    }

    @Test
    void testUpdateOwnerInfoSuccess() {
        log.info("========== 测试：正常修改 AKU 归属信息 ==========");

        // Given
        ClientInfoResponse client = clientManagementService.createUserClient("user-001", "张三", "Test AKU");
        String clientId = client.getClientId();
        log.info("创建 AKU: clientId={}, ownerUserId={}, ownerUsername={}", clientId, client.getOwnerUserId(), client.getOwnerUsername());

        // When
        clientManagementService.updateOwnerInfo(clientId, "user-002", "李四");
        log.info("修改归属信息: ownerUserId=user-002, ownerUsername=李四");

        // Then
        ClientInfoResponse updated = clientManagementService.getClientById(clientId);
        log.info("修改后: ownerUserId={}, ownerUsername={}", updated.getOwnerUserId(), updated.getOwnerUsername());

        assertEquals("user-002", updated.getOwnerUserId(), "ownerUserId 应已更新");
        assertEquals("李四", updated.getOwnerUsername(), "ownerUsername 应已更新");

        log.info("✓ AKU 归属信息修改成功");
    }

    @Test
    void testUpdateOwnerInfoClearsUsername() {
        log.info("========== 测试：ownerUsername 可置为空 ==========");

        // Given
        ClientInfoResponse client = clientManagementService.createUserClient("user-003", "王五", "Test AKU 2");
        String clientId = client.getClientId();

        // When — ownerUsername 传 null
        clientManagementService.updateOwnerInfo(clientId, "user-004", null);

        // Then
        ClientInfoResponse updated = clientManagementService.getClientById(clientId);
        log.info("修改后: ownerUserId={}, ownerUsername={}", updated.getOwnerUserId(), updated.getOwnerUsername());

        assertEquals("user-004", updated.getOwnerUserId(), "ownerUserId 应已更新");
        assertNull(updated.getOwnerUsername(), "ownerUsername 应为 null");

        log.info("✓ ownerUsername 置空测试通过");
    }

    @Test
    void testUpdateOwnerInfoOnPlatformClientThrows() {
        log.info("========== 测试：平台级 AKSK 不允许修改归属信息 ==========");

        // Given
        ClientInfoResponse platform = clientManagementService.createPlatformClient("Test AKP");
        String clientId = platform.getClientId();
        log.info("创建 AKP: clientId={}", clientId);

        // When / Then
        ClientException ex = assertThrows(ClientException.class,
                () -> clientManagementService.updateOwnerInfo(clientId, "user-999", "赵六"),
                "平台级 AKSK 应抛出 ClientException");

        log.info("捕获异常: errorCode={}, message={}", ex.getErrorCode(), ex.getMessage());
        assertEquals(ErrorCode.VALIDATION_FAILED, ex.getErrorCode(), "错误码应为 VALIDATION_FAILED");

        log.info("✓ 平台级 AKSK 拒绝修改归属信息");
    }

    @Test
    void testUpdateOwnerInfoNotFoundThrows() {
        log.info("========== 测试：不存在的 clientId 应抛出 CLIENT_NOT_FOUND ==========");

        // When / Then
        ClientException ex = assertThrows(ClientException.class,
                () -> clientManagementService.updateOwnerInfo("AKU_NOT_EXIST_12345", "user-x", "某人"),
                "不存在的 clientId 应抛出 ClientException");

        log.info("捕获异常: errorCode={}, message={}", ex.getErrorCode(), ex.getMessage());
        assertEquals(ErrorCode.CLIENT_NOT_FOUND, ex.getErrorCode(), "错误码应为 CLIENT_NOT_FOUND");

        log.info("✓ 不存在 clientId 正确抛出 CLIENT_NOT_FOUND");
    }
}
