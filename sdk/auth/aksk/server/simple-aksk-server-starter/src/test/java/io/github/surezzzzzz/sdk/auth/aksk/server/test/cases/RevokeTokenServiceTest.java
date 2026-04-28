package io.github.surezzzzzz.sdk.auth.aksk.server.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.server.controller.response.ClientInfoResponse;
import io.github.surezzzzzz.sdk.auth.aksk.server.entity.OAuth2AuthorizationEntity;
import io.github.surezzzzzz.sdk.auth.aksk.server.repository.OAuth2AuthorizationEntityRepository;
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
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试 revokeToken() 方法的各种场景
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleAkskServerTestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RevokeTokenServiceTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TokenManagementService tokenManagementService;

    @Autowired
    private ClientManagementService clientManagementService;

    @Autowired
    private OAuth2AuthorizationEntityRepository authorizationEntityRepository;

    @Autowired
    private RegisteredClientRepository registeredClientRepository;

    @Autowired
    private OAuth2AuthorizationService authorizationService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private String testClientId;
    private String testClientSecret;

    @BeforeEach
    void setup() {
        log.info("准备测试数据...");

        ClientInfoResponse client = clientManagementService.createPlatformClient(
                "Revoke Test Client",
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

        log.info("测试数据清理完成");

        Set<String> keys = redisTemplate.keys("sure-auth-aksk:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Test
    void testRevokeActiveToken() {
        log.info("测试撤销活跃Token");

        // 创建一个Token
        String testTokenId = createAuthorizationDirectly(testClientId);
        assertNotNull(testTokenId, "无法创建测试Token");

        // 检查Token是否存在
        Optional<OAuth2AuthorizationEntity> entityBefore = authorizationEntityRepository.findById(testTokenId);
        assertTrue(entityBefore.isPresent(), "创建的Token不存在");
        assertFalse(isTokenRevoked(entityBefore.get()), "新创建的Token不应已撤销");

        // 执行撤销
        tokenManagementService.revokeToken(testTokenId);

        // 等待一小段时间以确保撤销操作完成
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 重新从数据库加载实体以验证变更
        Optional<OAuth2AuthorizationEntity> entityAfter = authorizationEntityRepository.findById(testTokenId);
        assertTrue(entityAfter.isPresent(), "Token记录不应被删除");
        assertTrue(isTokenRevoked(entityAfter.get()), "Token应已被标记为撤销");

        log.info("撤销活跃Token测试通过");
    }

    @Test
    void testRevokeExpiredTokenSkipped() {
        log.info("测试撤销已过期Token应被跳过");

        // 创建一个已过期的Token
        String testTokenId = createExpiredAuthorizationDirectly(testClientId);
        assertNotNull(testTokenId, "无法创建测试Token");

        // 记录撤销前的状态
        Optional<OAuth2AuthorizationEntity> entityBefore = authorizationEntityRepository.findById(testTokenId);
        assertTrue(entityBefore.isPresent(), "创建的Token不存在");

        // 执行撤销
        tokenManagementService.revokeToken(testTokenId);

        // 验证Token没有被修改
        Optional<OAuth2AuthorizationEntity> entityAfter = authorizationEntityRepository.findById(testTokenId);
        assertTrue(entityAfter.isPresent(), "Token记录不应被删除");
        assertFalse(isTokenRevoked(entityAfter.get()), "已过期的Token不应被标记为撤销");

        log.info("撤销已过期Token跳过测试通过");
    }

    @Test
    void testRevokeAlreadyRevokedTokenSkipped() {
        log.info("测试撤销已撤销的Token应被跳过");

        // 创建一个Token并立即撤销
        String testTokenId = createAuthorizationDirectly(testClientId);
        assertNotNull(testTokenId, "无法创建测试Token");
        tokenManagementService.revokeToken(testTokenId);

        // 记录第一次撤销后的状态
        Optional<OAuth2AuthorizationEntity> entityAfterFirst = authorizationEntityRepository.findById(testTokenId);
        assertTrue(entityAfterFirst.isPresent());
        assertTrue(isTokenRevoked(entityAfterFirst.get()), "第一次撤销后应为已撤销");
        byte[] metadataAfterFirst = entityAfterFirst.get().getAccessTokenMetadata();

        // 再次执行撤销
        tokenManagementService.revokeToken(testTokenId);

        // 验证第二次撤销没有改变状态
        Optional<OAuth2AuthorizationEntity> entityAfterSecond = authorizationEntityRepository.findById(testTokenId);
        assertTrue(entityAfterSecond.isPresent());
        assertTrue(isTokenRevoked(entityAfterSecond.get()), "第二次撤销后仍应为已撤销");
        assertArrayEquals(metadataAfterFirst, entityAfterSecond.get().getAccessTokenMetadata(),
                "第二次撤销不应修改metadata");

        log.info("再次撤销已撤销的Token测试通过（状态未改变）");
    }

    @Test
    void testRevokeTokenNotInMysql() {
        log.info("测试撤销MySQL中不存在的Token");

        // 使用一个不存在的Token ID
        String nonExistentTokenId = UUID.randomUUID().toString();

        // 执行撤销，不应抛出异常
        tokenManagementService.revokeToken(nonExistentTokenId);

        log.info("撤销MySQL中不存在的Token测试通过（无异常）");
    }

    /**
     * 判断Token是否已被撤销
     */
    private boolean isTokenRevoked(OAuth2AuthorizationEntity entity) {
        if (entity.getAccessTokenMetadata() == null) {
            return false;
        }

        String metadataStr = new String(entity.getAccessTokenMetadata());
        return metadataStr.contains("\"metadata.token.invalidated\":true");
    }

    /**
     * 直接创建授权记录，避免HTTP调用复杂度
     */
    private String createAuthorizationDirectly(String clientId) {
        try {
            RegisteredClient registeredClient = registeredClientRepository.findByClientId(clientId);
            if (registeredClient == null) {
                log.error("找不到Client: {}", clientId);
                return null;
            }

            Instant issuedAt = Instant.now();
            Instant expiresAt = issuedAt.plusSeconds(3600); // 1小时后过期

            OAuth2AccessToken accessToken = new OAuth2AccessToken(
                    OAuth2AccessToken.TokenType.BEARER,
                    "test_token_" + UUID.randomUUID().toString(),
                    issuedAt,
                    expiresAt,
                    new HashSet<>(Arrays.asList("read", "write"))
            );

            OAuth2Authorization authorization = OAuth2Authorization.withRegisteredClient(registeredClient)
                    .principalName(clientId)
                    .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                    .token(accessToken)
                    .build();

            authorizationService.save(authorization);

            log.info("为Client {} 创建授权记录成功", clientId);
            return authorization.getId();
        } catch (Exception e) {
            log.error("创建授权记录失败 for client {}: {}", clientId, e.getMessage(), e);
            return null;
        }
    }

    /**
     * 创建一个已过期的Token
     */
    private String createExpiredAuthorizationDirectly(String clientId) {
        try {
            RegisteredClient registeredClient = registeredClientRepository.findByClientId(clientId);
            if (registeredClient == null) {
                log.error("找不到Client: {}", clientId);
                return null;
            }

            Instant issuedAt = Instant.now().minusSeconds(3600 * 2); // 2小时前签发
            Instant expiresAt = Instant.now().minusSeconds(3600); // 1小时前过期

            OAuth2AccessToken accessToken = new OAuth2AccessToken(
                    OAuth2AccessToken.TokenType.BEARER,
                    "expired_token_" + UUID.randomUUID().toString(),
                    issuedAt,
                    expiresAt,
                    new HashSet<>(Arrays.asList("read", "write"))
            );

            OAuth2Authorization authorization = OAuth2Authorization.withRegisteredClient(registeredClient)
                    .principalName(clientId)
                    .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                    .token(accessToken)
                    .build();

            authorizationService.save(authorization);

            log.info("为Client {} 创建过期授权记录成功", clientId);
            return authorization.getId();
        } catch (Exception e) {
            log.error("创建授权记录失败 for client {}: {}", clientId, e.getMessage(), e);
            return null;
        }
    }
}
