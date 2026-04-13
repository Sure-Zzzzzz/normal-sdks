package io.github.surezzzzzz.sdk.auth.aksk.server.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.core.constant.ClientType;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.response.ClientInfoResponse;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.response.PageResponse;
import io.github.surezzzzzz.sdk.auth.aksk.server.exception.ClientException;
import io.github.surezzzzzz.sdk.auth.aksk.server.repository.OAuth2RegisteredClientEntityRepository;
import io.github.surezzzzzz.sdk.auth.aksk.server.service.ClientManagementService;
import io.github.surezzzzzz.sdk.auth.aksk.server.test.SimpleAkskServerTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 平台级客户端管理测试
 * <p>
 * 测试平台级AKSK的创建、查询、更新和删除操作
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleAkskServerTestApplication.class)
class PlatformClientManagementTest {

    @Autowired
    private ClientManagementService clientManagementService;

    @Autowired
    private OAuth2RegisteredClientEntityRepository clientRepository;

    /**
     * 每个测试方法执行后清理数据
     */
    @AfterEach
    void cleanupData() {
        log.info("清理测试数据...");
        clientRepository.deleteAll();
        log.info("测试数据清理完成");
    }

    @Test
    void testCreatePlatformClientSuccess() {
        log.info("测试使用有效参数创建平台级客户端");

        // When
        ClientInfoResponse clientInfo = clientManagementService.createPlatformClient(
                "Test Platform Client",
                Arrays.asList("read", "write")
        );
        String clientId = clientInfo.getClientId();

        log.info("创建平台级客户端成功，ID: {}", clientId);

        // Then
        assertNotNull(clientId, "客户端ID不应为null");
        assertTrue(clientId.startsWith("AKP"), "客户端ID应以'AKP'开头");

        log.info("平台级客户端创建测试通过");
    }

    @Test
    void testCreatePlatformClientWithDefaultScopes() {
        log.info("测试使用默认scopes创建平台级客户端");

        // When
        ClientInfoResponse clientInfo = clientManagementService.createPlatformClient("Test Platform Client Default");
        String clientId = clientInfo.getClientId();

        log.info("创建平台级客户端成功（使用默认scopes），ID: {}", clientId);

        // Then
        assertNotNull(clientId, "客户端ID不应为null");
        assertTrue(clientId.startsWith("AKP"), "客户端ID应以'AKP'开头");

        // Verify default scopes
        ClientInfoResponse retrievedClientInfoResponse = clientManagementService.getClientById(clientId);
        log.info("获取客户端信息: {}", clientInfo);

        assertNotNull(clientInfo.getScopes(), "Scopes不应为null");
        assertTrue(clientInfo.getScopes().contains("read"), "应包含'read'权限");
        assertTrue(clientInfo.getScopes().contains("write"), "应包含'write'权限");

        log.info("平台级客户端创建（默认scopes）测试通过");
    }

    @Test
    void testGetPlatformClientByIdSuccess() {
        log.info("测试根据ID查询平台级客户端");

        // Given - Create a platform client first
        ClientInfoResponse clientInfo = clientManagementService.createPlatformClient(
                "Test Platform Client for Retrieval",
                Arrays.asList("read", "write")
        );
        String clientId = clientInfo.getClientId();
        log.info("创建平台级客户端用于查询测试，ID: {}", clientId);

        // When
        ClientInfoResponse retrievedClientInfoResponse = clientManagementService.getClientById(clientId);
        log.info("获取客户端信息: {}", retrievedClientInfoResponse);

        // Then
        assertNotNull(retrievedClientInfoResponse, "客户端信息不应为null");
        assertEquals(clientId, retrievedClientInfoResponse.getClientId(), "客户端ID应匹配");
        assertEquals("Test Platform Client for Retrieval", retrievedClientInfoResponse.getClientName(), "客户端名称应匹配");
        assertEquals(ClientType.PLATFORM.getCode(), retrievedClientInfoResponse.getClientType(), "客户端类型应为PLATFORM");
        assertNull(retrievedClientInfoResponse.getOwnerUserId(), "平台级客户端不应有所属用户ID");

        log.info("平台级客户端查询测试通过");
    }

    @Test
    void testGetPlatformClientByIdNotFound() {
        log.info("测试查询不存在的平台级客户端");

        // When & Then
        assertThrows(ClientException.class, () -> {
            clientManagementService.getClientById("non-existent-client-id");
        }, "查询不存在的客户端应抛出ClientException");

        log.info("平台级客户端未找到测试通过");
    }

    @Test
    void testGetPlatformClientsByType() {
        log.info("测试根据类型查询所有平台级客户端");

        // Given - Create multiple platform clients
        ClientInfoResponse clientInfo1 = clientManagementService.createPlatformClient("Platform Client 1");
        String clientId1 = clientInfo1.getClientId();
        ClientInfoResponse clientInfo2 = clientManagementService.createPlatformClient("Platform Client 2");
        String clientId2 = clientInfo2.getClientId();
        log.info("创建平台级客户端: {}, {}", clientId1, clientId2);

        // When
        PageResponse<ClientInfoResponse> pageResponse = clientManagementService.listClients(null, "platform", 1, Integer.MAX_VALUE);
        List<ClientInfoResponse> platformClients = pageResponse.getData();
        log.info("查询到 {} 个平台级客户端", platformClients.size());

        // Then
        assertNotNull(platformClients, "平台级客户端列表不应为null");
        assertTrue(platformClients.size() >= 2, "应至少有2个平台级客户端");

        boolean foundClient1 = platformClients.stream()
                .anyMatch(c -> c.getClientId().equals(clientId1));
        boolean foundClient2 = platformClients.stream()
                .anyMatch(c -> c.getClientId().equals(clientId2));

        assertTrue(foundClient1, "应找到第一个创建的客户端");
        assertTrue(foundClient2, "应找到第二个创建的客户端");

        log.info("平台级客户端按类型查询测试通过");
    }

    @Test
    void testDeletePlatformClientSuccess() {
        log.info("测试删除平台级客户端");

        // Given - Create a platform client first
        ClientInfoResponse clientInfo = clientManagementService.createPlatformClient("Platform Client to Delete");
        String clientId = clientInfo.getClientId();
        log.info("创建平台级客户端用于删除测试，ID: {}", clientId);

        // Verify it exists
        ClientInfoResponse existingClientInfoResponse = clientManagementService.getClientById(clientId);
        assertNotNull(existingClientInfoResponse, "删除前客户端应存在");

        // When
        clientManagementService.deleteClient(clientId);
        log.info("已删除平台级客户端: {}", clientId);

        // Then - Verify it no longer exists
        assertThrows(ClientException.class, () -> {
            clientManagementService.getClientById(clientId);
        }, "删除后应抛出ClientException");

        log.info("平台级客户端删除测试通过");
    }

    @Test
    void testDeletePlatformClientNotFound() {
        log.info("测试删除不存在的平台级客户端");

        // When & Then
        assertThrows(ClientException.class, () -> {
            clientManagementService.deleteClient("non-existent-client-id");
        }, "删除不存在的客户端应抛出ClientException");

        log.info("平台级客户端删除未找到测试通过");
    }

    @Test
    void testRegenerateSecretKeySuccess() {
        log.info("测试平台级客户端密钥重新生成");

        // Given - Create a platform client first
        ClientInfoResponse clientInfo = clientManagementService.createPlatformClient("Platform Client for Secret Regeneration");
        String clientId = clientInfo.getClientId();
        log.info("创建平台级客户端用于密钥重新生成测试，ID: {}", clientId);

        // When
        String newSecret = clientManagementService.regenerateSecretKey(clientId);
        log.info("为客户端重新生成密钥: {}, 新密钥长度: {}", clientId, newSecret.length());

        // Then
        assertNotNull(newSecret, "新密钥不应为null");
        assertFalse(newSecret.isEmpty(), "新密钥不应为空");
        assertTrue(newSecret.length() > 0, "新密钥长度应大于0");

        log.info("平台级客户端密钥重新生成测试通过");
    }

    @Test
    void testClientEnabledDefaultValue() {
        log.info("测试平台级客户端enabled字段默认值");

        // Given - Create a platform client
        ClientInfoResponse clientInfo = clientManagementService.createPlatformClient("Platform Client for Enabled Default Test");
        String clientId = clientInfo.getClientId();
        log.info("创建平台级客户端用于enabled默认值测试，ID: {}", clientId);

        // When
        ClientInfoResponse retrievedClientInfoResponse = clientManagementService.getClientById(clientId);
        log.info("获取客户端信息: {}", retrievedClientInfoResponse);

        // Then
        assertNotNull(retrievedClientInfoResponse, "客户端信息不应为null");
        assertTrue(retrievedClientInfoResponse.isEnabled(), "客户端enabled字段默认值应为true");

        log.info("平台级客户端enabled默认值测试通过");
    }

    @Test
    void testDisableClientSuccess() {
        log.info("测试禁用平台级客户端");

        // Given - Create a platform client first
        ClientInfoResponse clientInfo = clientManagementService.createPlatformClient("Platform Client to Disable");
        String clientId = clientInfo.getClientId();
        log.info("创建平台级客户端用于禁用测试，ID: {}", clientId);

        // Verify it's enabled by default
        ClientInfoResponse retrievedClientInfoResponse = clientManagementService.getClientById(clientId);
        assertTrue(retrievedClientInfoResponse.isEnabled(), "禁用前客户端应为启用状态");

        // When
        clientManagementService.disableClient(clientId);
        log.info("已禁用平台级客户端: {}", clientId);

        // Then - Verify it's disabled
        ClientInfoResponse disabledClientInfoResponse = clientManagementService.getClientById(clientId);
        assertFalse(disabledClientInfoResponse.isEnabled(), "禁用后客户端应为禁用状态");

        log.info("平台级客户端禁用测试通过");
    }

    @Test
    void testEnableClientSuccess() {
        log.info("测试启用平台级客户端");

        // Given - Create and disable a platform client first
        ClientInfoResponse clientInfo = clientManagementService.createPlatformClient("Platform Client to Enable");
        String clientId = clientInfo.getClientId();
        log.info("创建平台级客户端用于启用测试，ID: {}", clientId);

        clientManagementService.disableClient(clientId);
        log.info("已禁用平台级客户端: {}", clientId);

        // Verify it's disabled
        ClientInfoResponse disabledClientInfoResponse = clientManagementService.getClientById(clientId);
        assertFalse(disabledClientInfoResponse.isEnabled(), "启用前客户端应为禁用状态");

        // When
        clientManagementService.enableClient(clientId);
        log.info("已启用平台级客户端: {}", clientId);

        // Then - Verify it's enabled
        ClientInfoResponse enabledClientInfoResponse = clientManagementService.getClientById(clientId);
        assertTrue(enabledClientInfoResponse.isEnabled(), "启用后客户端应为启用状态");

        log.info("平台级客户端启用测试通过");
    }

    @Test
    void testDisableClientNotFound() {
        log.info("测试禁用不存在的平台级客户端");

        // When & Then
        assertThrows(ClientException.class, () -> {
            clientManagementService.disableClient("non-existent-client-id");
        }, "禁用不存在的客户端应抛出ClientException");

        log.info("平台级客户端禁用未找到测试通过");
    }

    @Test
    void testEnableClientNotFound() {
        log.info("测试启用不存在的平台级客户端");

        // When & Then
        assertThrows(ClientException.class, () -> {
            clientManagementService.enableClient("non-existent-client-id");
        }, "启用不存在的客户端应抛出ClientException");

        log.info("平台级客户端启用未找到测试通过");
    }

    @Test
    void testRegenerateSecretKeyNotFound() {
        log.info("测试为不存在的客户端重新生成密钥");

        // When & Then
        assertThrows(ClientException.class, () -> {
            clientManagementService.regenerateSecretKey("non-existent-client-id");
        }, "为不存在的客户端重新生成密钥应抛出ClientException");

        log.info("平台级客户端重新生成密钥未找到测试通过");
    }

    @Test
    void testCreatePlatformClientWithEmptyScopes() {
        log.info("测试使用空scopes列表创建平台级客户端");

        // When
        ClientInfoResponse clientInfo = clientManagementService.createPlatformClient(
                "Platform Client with Empty Scopes",
                Arrays.asList()
        );
        String clientId = clientInfo.getClientId();

        log.info("创建平台级客户端成功（空scopes），ID: {}", clientId);

        // Then
        assertNotNull(clientId, "客户端ID不应为null");
        assertTrue(clientId.startsWith("AKP"), "客户端ID应以'AKP'开头");

        // Verify default scopes are used
        ClientInfoResponse retrievedClientInfoResponse = clientManagementService.getClientById(clientId);
        assertNotNull(retrievedClientInfoResponse.getScopes(), "Scopes不应为null");
        assertTrue(retrievedClientInfoResponse.getScopes().contains("read"), "应包含默认'read'权限");
        assertTrue(retrievedClientInfoResponse.getScopes().contains("write"), "应包含默认'write'权限");

        log.info("平台级客户端空scopes测试通过");
    }

    @Test
    void testCreatePlatformClientWithLongName() {
        log.info("测试使用长名称创建平台级客户端");

        // Given - Create a long client name (within 200 char limit)
        String longName = "A".repeat(200);

        // When
        ClientInfoResponse clientInfo = clientManagementService.createPlatformClient(longName);
        String clientId = clientInfo.getClientId();

        log.info("创建平台级客户端成功（长名称），ID: {}", clientId);

        // Then
        assertNotNull(clientId, "客户端ID不应为null");
        assertTrue(clientId.startsWith("AKP"), "客户端ID应以'AKP'开头");

        ClientInfoResponse retrievedClientInfoResponse = clientManagementService.getClientById(clientId);
        assertEquals(longName, retrievedClientInfoResponse.getClientName(), "客户端名称应匹配");

        log.info("平台级客户端长名称测试通过");
    }
}
