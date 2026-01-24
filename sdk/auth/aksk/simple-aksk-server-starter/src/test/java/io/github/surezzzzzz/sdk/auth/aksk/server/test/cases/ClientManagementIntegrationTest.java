package io.github.surezzzzzz.sdk.auth.aksk.server.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.core.constant.ClientType;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.request.CreateClientRequest;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.response.BatchClientResponse;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.response.ClientInfoResponse;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.response.CreateClientResponse;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.response.PageResponse;
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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Client管理集成测试
 * <p>
 * 测试Client管理REST API的完整流程：
 * - 创建Client（平台级/用户级）
 * - 查询Client列表
 * - 查询单个Client详情
 * - 删除Client
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(
        classes = SimpleAkskServerTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class ClientManagementIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ClientManagementService clientManagementService;

    @Autowired
    private OAuth2RegisteredClientEntityRepository clientRepository;

    private String jwtToken;
    private String bootstrapClientId;
    private String bootstrapClientSecret;

    /**
     * 每个测试方法执行前通过Service创建bootstrap client并获取JWT token
     */
    @BeforeEach
    void setup() {
        log.info("通过Service创建bootstrap client用于测试...");

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
    }

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
    void testCreatePlatformClientAPI() {
        log.info("测试通过REST API创建平台级Client");

        // Given
        CreateClientRequest request = new CreateClientRequest();
        request.setType(ClientType.PLATFORM.getValue());
        request.setName("API Test Platform Client");

        // When
        String url = String.format("http://localhost:%d/api/client", port);
        HttpEntity<CreateClientRequest> entity = JwtTokenTestHelper.createAuthEntity(jwtToken, request);
        ResponseEntity<CreateClientResponse> response = restTemplate.postForEntity(
                url,
                entity,
                CreateClientResponse.class
        );

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().getClientId());
        assertNotNull(response.getBody().getClientSecret());
        assertTrue(response.getBody().getClientId().startsWith("AKP"));
        assertTrue(response.getBody().getClientSecret().startsWith("SK"));

        log.info("通过REST API创建平台级Client成功: {}", response.getBody().getClientId());
    }

    @Test
    void testCreateUserClientAPI() {
        log.info("测试通过REST API创建用户级Client");

        // Given
        CreateClientRequest request = new CreateClientRequest();
        request.setType(ClientType.USER.getValue());
        request.setName("API Test User Client");
        request.setOwnerUserId("apiTestUser001");
        request.setOwnerUsername("API Test User");

        // When
        String url = String.format("http://localhost:%d/api/client", port);
        HttpEntity<CreateClientRequest> entity = JwtTokenTestHelper.createAuthEntity(jwtToken, request);
        ResponseEntity<CreateClientResponse> response = restTemplate.postForEntity(
                url,
                entity,
                CreateClientResponse.class
        );

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().getClientId());
        assertNotNull(response.getBody().getClientSecret());
        assertTrue(response.getBody().getClientId().startsWith("AKU"));
        assertTrue(response.getBody().getClientSecret().startsWith("SK"));

        log.info("通过REST API创建用户级Client成功: {}", response.getBody().getClientId());
    }

    @Test
    void testGetAllClientsAPI() {
        log.info("测试通过REST API查询所有Client");

        // Given - Create test clients via Service
        clientManagementService.createPlatformClient("Test Client 1");
        clientManagementService.createPlatformClient("Test Client 2");

        // When - Query platform clients
        String url = String.format("http://localhost:%d/api/client?type=platform", port);
        HttpEntity<Void> platformRequest = JwtTokenTestHelper.createAuthEntity(jwtToken);
        ResponseEntity<PageResponse<ClientInfoResponse>> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                platformRequest,
                new ParameterizedTypeReference<PageResponse<ClientInfoResponse>>() {
                }
        );

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().getData());
        // Bootstrap + 2 test clients = 3
        assertEquals(3, response.getBody().getData().size());
        assertEquals(3L, response.getBody().getTotal().longValue());

        log.info("通过REST API查询所有Client成功，共 {} 个", response.getBody().getData().size());
    }

    @Test
    void testGetClientByIdAPI() {
        log.info("测试通过REST API根据ID查询Client");

        // Given - Create a test client via Service
        ClientInfoResponse testClient = clientManagementService.createPlatformClient("Test Client for Get");
        String clientId = testClient.getClientId();

        // When
        String url = String.format("http://localhost:%d/api/client/%s", port, clientId);
        HttpEntity<Void> entity = JwtTokenTestHelper.createAuthEntity(jwtToken);
        ResponseEntity<ClientInfoResponse> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                ClientInfoResponse.class
        );

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(clientId, response.getBody().getClientId());
        assertEquals("Test Client for Get", response.getBody().getClientName());

        log.info("通过REST API根据ID查询Client成功: {}", clientId);
    }

    @Test
    void testGetClientsByTypeAPI() {
        log.info("测试通过REST API根据类型查询Client");

        // Given - Create test clients via Service
        clientManagementService.createPlatformClient("Platform Client 1");
        clientManagementService.createPlatformClient("Platform Client 2");
        clientManagementService.createUserClient("User Client 1", "user001", "User 001");

        // When
        String url = String.format("http://localhost:%d/api/client?type=%s", port, ClientType.PLATFORM.getValue());
        HttpEntity<Void> typeQueryRequest = JwtTokenTestHelper.createAuthEntity(jwtToken);
        ResponseEntity<PageResponse<ClientInfoResponse>> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                typeQueryRequest,
                new ParameterizedTypeReference<PageResponse<ClientInfoResponse>>() {
                }
        );

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().getData());
        // Bootstrap client + 2 test platform clients = 3
        assertEquals(3, response.getBody().getData().size());
        assertEquals(3L, response.getBody().getTotal().longValue());
        response.getBody().getData().forEach(client -> {
            assertEquals(ClientType.PLATFORM.getCode(), client.getClientType());
        });

        log.info("通过REST API根据类型查询Client成功，共 {} 个平台级Client", response.getBody().getData().size());
    }

    @Test
    void testDeleteClientAPI() {
        log.info("测试通过REST API删除Client");

        // Given - Create a test client via Service
        ClientInfoResponse testClient = clientManagementService.createPlatformClient("Test Client for Delete");
        String clientId = testClient.getClientId();

        // When
        String url = String.format("http://localhost:%d/api/client/%s", port, clientId);
        HttpEntity<Void> deleteRequest = JwtTokenTestHelper.createAuthEntity(jwtToken);
        ResponseEntity<Void> response = restTemplate.exchange(
                url,
                HttpMethod.DELETE,
                deleteRequest,
                Void.class
        );

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());

        log.info("通过REST API删除Client成功: {}", clientId);
    }

    @Test
    void testBatchGetClientsByIdsAPI() {
        log.info("测试通过REST API批量查询Client");

        // Given - Create test clients via Service
        ClientInfoResponse client1 = clientManagementService.createPlatformClient("Batch Test Client 1");
        ClientInfoResponse client2 = clientManagementService.createPlatformClient("Batch Test Client 2");
        ClientInfoResponse client3 = clientManagementService.createUserClient("user001", "Test User", "Batch Test Client 3");

        List<String> clientIds = new ArrayList<>();
        clientIds.add(client1.getClientId());
        clientIds.add(client2.getClientId());
        clientIds.add(client3.getClientId());

        // When - 使用clientIds参数批量查询
        String url = UriComponentsBuilder.fromHttpUrl(String.format("http://localhost:%d/api/client", port))
                .queryParam("clientIds", clientIds.toArray())
                .toUriString();

        HttpEntity<Void> batchQueryRequest = JwtTokenTestHelper.createAuthEntity(jwtToken);
        ResponseEntity<BatchClientResponse> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                batchQueryRequest,
                BatchClientResponse.class
        );

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().getClients());

        Map<String, ClientInfoResponse> clients = response.getBody().getClients();
        assertEquals(3, clients.size());

        // 验证返回的Client信息
        assertTrue(clients.containsKey(client1.getClientId()));
        assertTrue(clients.containsKey(client2.getClientId()));
        assertTrue(clients.containsKey(client3.getClientId()));

        assertEquals("Batch Test Client 1", clients.get(client1.getClientId()).getClientName());
        assertEquals("Batch Test Client 2", clients.get(client2.getClientId()).getClientName());
        assertEquals("Batch Test Client 3", clients.get(client3.getClientId()).getClientName());

        log.info("通过REST API批量查询Client成功，共查询 {} 个", clients.size());
    }

    @Test
    void testBatchGetClientsWithNonExistentIds() {
        log.info("测试批量查询包含不存在的Client ID");

        // Given
        ClientInfoResponse client1 = clientManagementService.createPlatformClient("Existing Client");

        List<String> clientIds = new ArrayList<>();
        clientIds.add(client1.getClientId());
        clientIds.add("NON_EXISTENT_ID_1");
        clientIds.add("NON_EXISTENT_ID_2");

        // When
        String url = UriComponentsBuilder.fromHttpUrl(String.format("http://localhost:%d/api/client", port))
                .queryParam("clientIds", clientIds.toArray())
                .toUriString();

        HttpEntity<Void> batchQueryRequest = JwtTokenTestHelper.createAuthEntity(jwtToken);
        ResponseEntity<BatchClientResponse> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                batchQueryRequest,
                BatchClientResponse.class
        );

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().getClients());

        Map<String, ClientInfoResponse> clients = response.getBody().getClients();
        // 只返回存在的Client
        assertEquals(1, clients.size());
        assertTrue(clients.containsKey(client1.getClientId()));
        assertFalse(clients.containsKey("NON_EXISTENT_ID_1"));
        assertFalse(clients.containsKey("NON_EXISTENT_ID_2"));

        log.info("批量查询不存在ID测试通过，只返回存在的Client");
    }
}
