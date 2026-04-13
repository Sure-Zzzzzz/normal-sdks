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
 * 用户级客户端管理测试
 * <p>
 * 测试用户级AKSK的创建、查询、更新和删除操作
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleAkskServerTestApplication.class)
class UserClientManagementTest {

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
    void testCreateUserClientSuccess() {
        log.info("测试使用有效参数创建用户级客户端");

        // When
        ClientInfoResponse clientInfo = clientManagementService.createUserClient(
                "10086",
                "zhangsan",
                "Test User Client for 10086",
                Arrays.asList("read", "write")
        );
        String clientId = clientInfo.getClientId();

        log.info("创建用户级客户端成功，ID: {}", clientId);

        // Then
        assertNotNull(clientId, "客户端ID不应为null");
        assertTrue(clientId.startsWith("AKU"), "客户端ID应以'AKU'开头");

        log.info("用户级客户端创建测试通过");
    }

    @Test
    void testCreateUserClientWithDefaultScopes() {
        log.info("测试使用默认scopes创建用户级客户端");

        // When
        ClientInfoResponse clientInfo = clientManagementService.createUserClient(
                "10010",
                "lisi",
                "Test User Client for 10010"
        );
        String clientId = clientInfo.getClientId();

        log.info("创建用户级客户端成功（使用默认scopes），ID: {}", clientId);

        // Then
        assertNotNull(clientId, "客户端ID不应为null");
        assertTrue(clientId.startsWith("AKU"), "客户端ID应以'AKU'开头");

        // Verify default scopes and owner
        ClientInfoResponse retrievedClientInfoResponse = clientManagementService.getClientById(clientId);
        log.info("获取客户端信息: {}", retrievedClientInfoResponse);

        assertNotNull(retrievedClientInfoResponse.getScopes(), "Scopes不应为null");
        assertTrue(retrievedClientInfoResponse.getScopes().contains("read"), "应包含'read'权限");
        assertTrue(retrievedClientInfoResponse.getScopes().contains("write"), "应包含'write'权限");
        assertEquals("10010", retrievedClientInfoResponse.getOwnerUserId(), "所属用户ID应匹配");
        assertEquals(ClientType.USER.getCode(), retrievedClientInfoResponse.getClientType(), "客户端类型应为USER");

        log.info("用户级客户端创建（默认scopes）测试通过");
    }

    @Test
    void testGetUserClientsByUserId() {
        log.info("测试根据用户ID查询用户级客户端");

        // Given - Create multiple clients for the same user
        String userId = "user-test-123";
        ClientInfoResponse clientInfo1 = clientManagementService.createUserClient(userId, "wangwu", "User Client 1");
        String clientId1 = clientInfo1.getClientId();
        ClientInfoResponse clientInfo2 = clientManagementService.createUserClient(userId, "wangwu", "User Client 2");
        String clientId2 = clientInfo2.getClientId();
        log.info("为用户 {} 创建客户端: {}, {}", userId, clientId1, clientId2);

        // When
        PageResponse<ClientInfoResponse> pageResponse = clientManagementService.listClients(userId, "user", 1, Integer.MAX_VALUE);
        List<ClientInfoResponse> userClients = pageResponse.getData();
        log.info("为用户 {} 查询到 {} 个客户端", userId, userClients.size());

        // Then
        assertNotNull(userClients, "用户客户端列表不应为null");
        assertEquals(2, userClients.size(), "该用户应有2个客户端");

        boolean foundClient1 = userClients.stream()
                .anyMatch(c -> c.getClientId().equals(clientId1));
        boolean foundClient2 = userClients.stream()
                .anyMatch(c -> c.getClientId().equals(clientId2));

        assertTrue(foundClient1, "应找到第一个创建的客户端");
        assertTrue(foundClient2, "应找到第二个创建的客户端");

        // Verify all clients belong to the correct user
        userClients.forEach(client -> {
            assertEquals(userId, client.getOwnerUserId(), "所有客户端应属于同一用户");
            assertEquals(ClientType.USER.getCode(), client.getClientType(), "所有客户端类型应为USER");
        });

        log.info("用户级客户端按用户ID查询测试通过");
    }

    @Test
    void testDeleteUserClientSuccess() {
        log.info("测试删除用户级客户端");

        // Given - Create a user client first
        ClientInfoResponse clientInfo = clientManagementService.createUserClient("user-delete-test", "zhaoliu", "User Client to Delete");
        String clientId = clientInfo.getClientId();
        log.info("创建用户级客户端用于删除测试，ID: {}", clientId);

        // Verify it exists
        ClientInfoResponse existingClientInfoResponse = clientManagementService.getClientById(clientId);
        assertNotNull(existingClientInfoResponse, "删除前客户端应存在");

        // When
        clientManagementService.deleteClient(clientId);
        log.info("已删除用户级客户端: {}", clientId);

        // Then - Verify it no longer exists
        assertThrows(ClientException.class, () -> {
            clientManagementService.getClientById(clientId);
        }, "删除后应抛出ClientException");

        log.info("用户级客户端删除测试通过");
    }

    @Test
    void testSyncUserScopesSuccess() {
        log.info("测试用户客户端权限同步");

        // Given - Create multiple clients for the same user
        String userId = "user-sync-test";
        ClientInfoResponse clientInfo1 = clientManagementService.createUserClient(userId, "sunqi", "User Client 1", Arrays.asList("read"));
        String clientId1 = clientInfo1.getClientId();
        ClientInfoResponse clientInfo2 = clientManagementService.createUserClient(userId, "sunqi", "User Client 2", Arrays.asList("read"));
        String clientId2 = clientInfo2.getClientId();
        log.info("创建用户客户端用于同步测试: {}, {}", clientId1, clientId2);

        // Verify initial scopes
        ClientInfoResponse client1Before = clientManagementService.getClientById(clientId1);
        log.info("客户端1初始权限: {}", client1Before.getScopes());
        assertEquals(1, client1Before.getScopes().size(), "初始应有1个权限");

        // When - Sync scopes to include read, write, admin
        List<String> newScopes = Arrays.asList("read", "write", "admin");
        int updatedCount = clientManagementService.syncUserScopes(userId, newScopes);
        log.info("为用户 {} 同步权限，更新了 {} 个客户端", userId, updatedCount);

        // Then
        assertEquals(2, updatedCount, "应更新2个客户端");

        // Verify both clients have the new scopes
        ClientInfoResponse client1After = clientManagementService.getClientById(clientId1);
        ClientInfoResponse client2After = clientManagementService.getClientById(clientId2);
        log.info("客户端1更新后权限: {}", client1After.getScopes());
        log.info("客户端2更新后权限: {}", client2After.getScopes());

        assertEquals(3, client1After.getScopes().size(), "客户端1应有3个权限");
        assertEquals(3, client2After.getScopes().size(), "客户端2应有3个权限");
        assertTrue(client1After.getScopes().containsAll(newScopes), "客户端1应包含所有新权限");
        assertTrue(client2After.getScopes().containsAll(newScopes), "客户端2应包含所有新权限");

        log.info("用户权限同步测试通过");
    }

    @Test
    void testSyncUserScopesNoClients() {
        log.info("测试为无客户端的用户同步权限");

        // When - Sync scopes for a user with no clients
        int updatedCount = clientManagementService.syncUserScopes("non-existent-user", Arrays.asList("read", "write"));
        log.info("为不存在的用户同步权限，更新了 {} 个客户端", updatedCount);

        // Then
        assertEquals(0, updatedCount, "应更新0个客户端");

        log.info("用户权限同步（无客户端）测试通过");
    }
}
