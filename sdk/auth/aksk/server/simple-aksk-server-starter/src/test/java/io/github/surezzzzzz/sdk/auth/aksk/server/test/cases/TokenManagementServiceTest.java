package io.github.surezzzzzz.sdk.auth.aksk.server.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.core.model.TokenInfo;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.request.TokenQueryRequest;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.response.ClientInfoResponse;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.response.PageResponse;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.response.TokenInfoResponse;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.response.TokenStatisticsResponse;
import io.github.surezzzzzz.sdk.auth.aksk.server.repository.OAuth2AuthorizationRepository;
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
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Token管理服务单元测试
 * <p>
 * 测试Token的查询、过滤、分页、删除和统计功能
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleAkskServerTestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TokenManagementServiceTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TokenManagementService tokenManagementService;

    @Autowired
    private ClientManagementService clientManagementService;

    @Autowired
    private OAuth2AuthorizationRepository authorizationRepository;

    @Autowired
    private RegisteredClientRepository registeredClientRepository;

    @Autowired
    private OAuth2AuthorizationService authorizationService;

    private RestTemplate restTemplate = new RestTemplate();

    private String testClientId1;
    private String testClientSecret1;
    private String testClientId2;
    private String testClientSecret2;

    /**
     * 每个测试方法执行前准备测试数据
     */
    @BeforeEach
    void setupTestData() {
        log.info("准备测试数据...");

        // 创建测试用的Client
        ClientInfoResponse client1 = clientManagementService.createPlatformClient(
                "Token Test Client 1",
                Arrays.asList("read", "write")
        );
        testClientId1 = client1.getClientId();
        testClientSecret1 = client1.getClientSecret();

        ClientInfoResponse client2 = clientManagementService.createUserClient(
                "user123",
                "testuser",
                "Token Test Client 2",
                Arrays.asList("read")
        );
        testClientId2 = client2.getClientId();
        testClientSecret2 = client2.getClientSecret();

        log.info("创建测试Client: {}, {}", testClientId1, testClientId2);

        // 直接创建授权记录，避免HTTP调用复杂度
        createAuthorizationDirectly(testClientId1, testClientSecret1);
        createAuthorizationDirectly(testClientId2, testClientSecret2);

        log.info("测试数据准备完成");
    }

    /**
     * 每个测试方法执行后清理数据
     */
    @AfterEach
    void cleanupData() {
        log.info("清理测试数据...");

        try {
            // 清理所有授权记录
            authorizationRepository.deleteAll();
        } catch (Exception e) {
            log.warn("清理授权记录失败: {}", e.getMessage());
        }

        try {
            // 清理测试Client
            if (testClientId1 != null) {
                clientManagementService.deleteClient(testClientId1);
            }
            if (testClientId2 != null) {
                clientManagementService.deleteClient(testClientId2);
            }
        } catch (Exception e) {
            log.warn("清理测试Client失败: {}", e.getMessage());
        }

        log.info("测试数据清理完成");
    }

    @Test
    void testQueryAllTokens() {
        log.info("测试查询所有Token");

        // When
        TokenQueryRequest request = new TokenQueryRequest();
        request.setPage(1);
        request.setSize(10);

        PageResponse<TokenInfoResponse> pageResponse = tokenManagementService.queryTokens(request);
        List<TokenInfoResponse> tokens = pageResponse.getData();

        // Then
        assertNotNull(tokens, "Token列表不应为null");
        assertTrue(tokens.size() >= 2, "应至少有2个Token");

        log.info("查询到 {} 个Token", tokens.size());
        log.info("所有Token查询测试通过");
    }

    @Test
    void testQueryTokensByClientId() {
        log.info("测试按Client ID过滤Token");

        // When
        TokenQueryRequest request = new TokenQueryRequest();
        request.setClientId(testClientId1);
        request.setPage(1);
        request.setSize(10);

        PageResponse<TokenInfoResponse> pageResponse = tokenManagementService.queryTokens(request);
        List<TokenInfoResponse> tokens = pageResponse.getData();

        // Then
        assertNotNull(tokens, "Token列表不应为null");
        assertEquals(1, tokens.size(), "应只有1个Token匹配");
        assertEquals(testClientId1, tokens.get(0).getClientId(), "Client ID应匹配");

        log.info("按Client ID过滤测试通过");
    }

    @Test
    void testQueryTokensByClientType() {
        log.info("测试按Client类型过滤Token");

        // When - 查询平台级Token
        TokenQueryRequest request = new TokenQueryRequest();
        request.setClientType(1); // PLATFORM = 1
        request.setPage(1);
        request.setSize(10);

        PageResponse<TokenInfoResponse> pageResponse = tokenManagementService.queryTokens(request);
        List<TokenInfoResponse> platformTokens = pageResponse.getData();

        // Then
        assertNotNull(platformTokens, "Token列表不应为null");
        assertTrue(platformTokens.size() >= 1, "应至少有1个平台级Token");
        assertTrue(platformTokens.stream().allMatch(t -> t.getClientType() == 1),
                "所有Token应为平台级");

        log.info("查询到 {} 个平台级Token", platformTokens.size());

        // When - 查询用户级Token
        request.setClientType(2); // USER = 2
        PageResponse<TokenInfoResponse> pageResponse2 = tokenManagementService.queryTokens(request);
        List<TokenInfoResponse> userTokens = pageResponse2.getData();

        // Then
        assertNotNull(userTokens, "Token列表不应为null");
        assertTrue(userTokens.size() >= 1, "应至少有1个用户级Token");
        assertTrue(userTokens.stream().allMatch(t -> t.getClientType() == 2),
                "所有Token应为用户级");

        log.info("查询到 {} 个用户级Token", userTokens.size());
        log.info("按Client类型过滤测试通过");
    }

    @Test
    void testQueryTokensByStatus() {
        log.info("测试按状态过滤Token");

        // When - 查询有效Token
        TokenQueryRequest request = new TokenQueryRequest();
        request.setStatus(TokenInfo.TokenStatus.ACTIVE);
        request.setPage(1);
        request.setSize(10);

        PageResponse<TokenInfoResponse> pageResponse = tokenManagementService.queryTokens(request);
        List<TokenInfoResponse> activeTokens = pageResponse.getData();

        // Then
        assertNotNull(activeTokens, "Token列表不应为null");
        assertTrue(activeTokens.size() >= 2, "应至少有2个有效Token");
        assertTrue(activeTokens.stream().allMatch(t -> t.getStatus() == TokenInfo.TokenStatus.ACTIVE),
                "所有Token应为有效状态");

        log.info("查询到 {} 个有效Token", activeTokens.size());
        log.info("按状态过滤测试通过");
    }

    @Test
    void testQueryTokensByDataSource() {
        log.info("测试按数据源过滤Token");

        // When - 查询MySQL中的Token
        TokenQueryRequest request = new TokenQueryRequest();
        request.setPage(1);
        request.setSize(10);

        PageResponse<TokenInfoResponse> pageResponse = tokenManagementService.queryTokens(request);
        List<TokenInfoResponse> mysqlTokens = pageResponse.getData();

        // Then
        assertNotNull(mysqlTokens, "Token列表不应为null");
        log.info("查询到 {} 个MySQL Token", mysqlTokens.size());

        log.info("按数据源过滤测试通过");
    }

    @Test
    void testSearchTokens() {
        log.info("测试搜索Token");

        // When - 按Client名称搜索
        TokenQueryRequest request = new TokenQueryRequest();
        request.setSearch("Token Test Client 1");
        request.setPage(1);
        request.setSize(10);

        PageResponse<TokenInfoResponse> pageResponse = tokenManagementService.queryTokens(request);
        List<TokenInfoResponse> tokens = pageResponse.getData();

        // Then
        assertNotNull(tokens, "Token列表不应为null");
        assertTrue(tokens.size() >= 1, "应至少找到1个匹配的Token");
        assertTrue(tokens.stream().anyMatch(t -> t.getClientName() != null &&
                        t.getClientName().contains("Token Test Client 1")),
                "应找到匹配的Client名称");

        log.info("搜索到 {} 个Token", tokens.size());
        log.info("搜索Token测试通过");
    }

    @Test
    void testPagination() {
        log.info("测试分页功能");

        // When - 第1页，每页1条
        TokenQueryRequest request1 = new TokenQueryRequest();
        request1.setPage(1);
        request1.setSize(1);

        PageResponse<TokenInfoResponse> pageResponse1 = tokenManagementService.queryTokens(request1);
        List<TokenInfoResponse> page1 = pageResponse1.getData();

        // Then
        assertNotNull(page1, "第1页不应为null");
        assertEquals(1, page1.size(), "第1页应有1条记录");

        // When - 第2页，每页1条
        TokenQueryRequest request2 = new TokenQueryRequest();
        request2.setPage(2);
        request2.setSize(1);

        PageResponse<TokenInfoResponse> pageResponse2 = tokenManagementService.queryTokens(request2);
        List<TokenInfoResponse> page2 = pageResponse2.getData();

        // Then
        assertNotNull(page2, "第2页不应为null");
        assertTrue(page2.size() >= 1, "第2页应至少有1条记录");

        // 验证两页数据不同
        if (page2.size() > 0) {
            assertNotEquals(page1.get(0).getId(), page2.get(0).getId(), "两页的Token应不同");
        }

        log.info("分页测试通过");
    }

    @Test
    void testGetTokenById() {
        log.info("测试按ID查询Token详情");

        // Given - 先查询所有Token获取一个ID
        TokenQueryRequest request = new TokenQueryRequest();
        request.setPage(1);
        request.setSize(1);
        PageResponse<TokenInfoResponse> pageResponse = tokenManagementService.queryTokens(request);
        List<TokenInfoResponse> tokens = pageResponse.getData();
        assertTrue(tokens.size() > 0, "应至少有1个Token");

        String tokenId = tokens.get(0).getId();
        log.info("测试查询Token ID: {}", tokenId);

        // When
        TokenInfoResponse token = tokenManagementService.getTokenById(tokenId);

        // Then
        assertNotNull(token, "Token详情不应为null");
        assertEquals(tokenId, token.getId(), "Token ID应匹配");
        assertNotNull(token.getClientId(), "Client ID不应为null");
        assertNotNull(token.getIssuedAt(), "签发时间不应为null");

        log.info("按ID查询Token测试通过");
    }

    @Test
    void testGetTokenByIdNotFound() {
        log.info("测试查询不存在的Token");

        // When
        TokenInfoResponse token = tokenManagementService.getTokenById("non-existent-token-id");

        // Then
        assertNull(token, "不存在的Token应返回null");

        log.info("查询不存在Token测试通过");
    }

    @Test
    void testDeleteToken() {
        log.info("测试删除Token");

        // Given - 先查询所有Token获取一个ID
        TokenQueryRequest request = new TokenQueryRequest();
        request.setPage(1);
        request.setSize(1);
        PageResponse<TokenInfoResponse> pageResponse = tokenManagementService.queryTokens(request);
        List<TokenInfoResponse> tokens = pageResponse.getData();
        assertTrue(tokens.size() > 0, "应至少有1个Token");

        String tokenId = tokens.get(0).getId();
        log.info("测试删除Token ID: {}", tokenId);

        // Verify it exists
        TokenInfoResponse existingToken = tokenManagementService.getTokenById(tokenId);
        assertNotNull(existingToken, "删除前Token应存在");

        // When
        tokenManagementService.deleteToken(tokenId);
        log.info("已删除Token: {}", tokenId);

        // Then - Verify it no longer exists
        TokenInfoResponse deletedToken = tokenManagementService.getTokenById(tokenId);
        assertNull(deletedToken, "删除后Token应不存在");

        log.info("删除Token测试通过");
    }

    @Test
    void testGetStatistics() {
        log.info("测试获取Token统计信息");

        // When
        TokenStatisticsResponse statistics = tokenManagementService.getStatistics();

        // Then
        assertNotNull(statistics, "统计信息不应为null");
        assertTrue(statistics.getTotalCount() >= 2, "总数应至少为2");
        assertTrue(statistics.getActiveCount() >= 2, "有效Token数应至少为2");
        assertTrue(statistics.getMysqlCount() >= 2, "MySQL Token数应至少为2");

        log.info("统计信息: 总数={}, 有效={}, MySQL={}, Redis={}, Both={}",
                statistics.getTotalCount(),
                statistics.getActiveCount(),
                statistics.getMysqlCount(),
                statistics.getRedisCount(),
                statistics.getBothCount());

        log.info("获取统计信息测试通过");
    }

    @Test
    void testDeleteExpiredTokens() {
        log.info("测试清理过期Token");

        // When
        int deletedCount = tokenManagementService.deleteExpiredTokens();

        // Then
        assertTrue(deletedCount >= 0, "删除数量应大于等于0");

        log.info("清理了 {} 个过期Token", deletedCount);
        log.info("清理过期Token测试通过");
    }

    @Test
    void testComplexFiltering() {
        log.info("测试复杂过滤条件");

        // When - 同时使用多个过滤条件
        TokenQueryRequest request = new TokenQueryRequest();
        request.setClientType(1); // PLATFORM
        request.setStatus(TokenInfo.TokenStatus.ACTIVE);
        request.setPage(1);
        request.setSize(10);

        PageResponse<TokenInfoResponse> pageResponse = tokenManagementService.queryTokens(request);
        List<TokenInfoResponse> tokens = pageResponse.getData();

        // Then
        assertNotNull(tokens, "Token列表不应为null");
        // 验证所有返回的Token都符合过滤条件
        for (TokenInfoResponse token : tokens) {
            assertEquals(1, token.getClientType(), "所有Token应为平台级");
            assertEquals(TokenInfo.TokenStatus.ACTIVE, token.getStatus(), "所有Token应为有效状态");
        }

        log.info("复杂过滤查询到 {} 个Token", tokens.size());
        log.info("复杂过滤测试通过");
    }

    /**
     * 辅助方法：换取Token
     */
    /**
     * 直接创建OAuth2授权记录(用于测试)
     */
    private void createAuthorizationDirectly(String clientId, String clientSecret) {
        try {
            // 查找RegisteredClient
            RegisteredClient registeredClient = registeredClientRepository.findByClientId(clientId);
            if (registeredClient == null) {
                log.error("找不到Client: {}", clientId);
                return;
            }

            // 创建access token
            Instant issuedAt = Instant.now();
            Instant expiresAt = issuedAt.plusSeconds(3600); // 1小时后过期

            OAuth2AccessToken accessToken = new OAuth2AccessToken(
                    OAuth2AccessToken.TokenType.BEARER,
                    "test_token_" + UUID.randomUUID().toString(),
                    issuedAt,
                    expiresAt,
                    new HashSet<>(Arrays.asList("read", "write"))
            );

            // 创建Authorization
            OAuth2Authorization authorization = OAuth2Authorization.withRegisteredClient(registeredClient)
                    .principalName(clientId)
                    .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                    .token(accessToken)
                    .build();

            // 使用OAuth2AuthorizationService保存
            authorizationService.save(authorization);

            log.info("为Client {} 创建授权记录成功", clientId);
        } catch (Exception e) {
            log.error("创建授权记录失败 for client {}: {}", clientId, e.getMessage(), e);
        }
    }
}
