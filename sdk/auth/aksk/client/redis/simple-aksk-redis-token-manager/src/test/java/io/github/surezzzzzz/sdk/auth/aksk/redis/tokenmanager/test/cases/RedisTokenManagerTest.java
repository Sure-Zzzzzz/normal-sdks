package io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.client.core.provider.SecurityContextProvider;
import io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.manager.RedisTokenManager;
import io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.test.SimpleAkskRedisTokenManagerTestApplication;
import io.github.surezzzzzz.sdk.cache.layer.L1Cache;
import io.github.surezzzzzz.sdk.cache.layer.L2Cache;
import io.github.surezzzzzz.sdk.cache.manager.SmartCacheManager;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RedisTokenManager 集成测试
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleAkskRedisTokenManagerTestApplication.class)
@ActiveProfiles("nonNullSecurityContext")
class RedisTokenManagerTest {

    @Autowired
    private RedisTokenManager tokenManager;

    @Autowired
    private SecurityContextProvider securityContextProvider;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private SmartCacheManager cacheManager;

    @Autowired
    private L1Cache l1Cache;

    @Autowired
    private L2Cache l2Cache;

    @Value("${io.github.surezzzzzz.sdk.auth.aksk.client.redis.token.cache-name:aksk-client-token}")
    private String cacheName;

    @BeforeEach
    void setUp() {
        cacheManager.clear(cacheName); // 同时清 L1（Caffeine）和 L2（Redis）
        cleanupTestKeys();
    }

    @AfterEach
    void tearDown() {
        cacheManager.clear(cacheName);
        cleanupTestKeys();
    }

    private void cleanupTestKeys() {
        Set<String> keys = stringRedisTemplate.keys("sure-auth-aksk-client:*");
        if (keys != null && !keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
    }

    private String generateCacheKey(String securityContext) {
        return StringUtils.hasText(securityContext)
                ? String.valueOf(securityContext.hashCode())
                : "default";
    }

    @Test
    @DisplayName("测试首次获取 Token - 缓存为空")
    void testGetTokenFirstTime() {
        log.info("========== 测试首次获取 Token - 缓存为空 ==========");

        String token = tokenManager.getToken();

        log.info("获取的 Token: {}", token);
        assertNotNull(token, "Token 不应为 null");
        assertTrue(token.length() > 0, "Token 不应为空字符串");

        // 验证 token 已写入 L2，TTL > 0
        String cacheKey = generateCacheKey(securityContextProvider.getSecurityContext());
        long l2Ttl = l2Cache.getTtl(cacheName, cacheKey);
        assertTrue(l2Ttl > 0, "getToken 后 L2 应有 token，TTL 应大于 0");
        log.info("L2 TTL: {}s", l2Ttl);

        log.info("======================================");
    }

    @Test
    @DisplayName("测试从缓存获取 Token - 两次获取应相同")
    void testGetTokenFromCache() {
        log.info("========== 测试从缓存获取 Token ==========");

        String firstToken = tokenManager.getToken();
        log.info("第一次获取的 Token: {}", firstToken);

        String secondToken = tokenManager.getToken();
        log.info("第二次获取的 Token: {}", secondToken);

        assertEquals(firstToken, secondToken, "两次获取的 Token 应相同（从缓存）");

        log.info("======================================");
    }

    @Test
    @DisplayName("测试清除 Token - clearToken 后 L1 和 L2 均立即清除")
    void testClearToken() {
        log.info("========== 测试清除 Token ==========");

        String firstToken = tokenManager.getToken();
        log.info("第一次获取的 Token: {}", firstToken);
        assertNotNull(firstToken, "第一次 Token 不应为 null");

        tokenManager.clearToken();
        log.info("Token 已清除");

        // 验证 L1 已清除
        String cacheKey = generateCacheKey(securityContextProvider.getSecurityContext());
        Object l1Value = l1Cache.get(cacheName, cacheKey);
        assertNull(l1Value, "clearToken 后 L1 应为 null");
        log.info("L1 已清除");

        // 验证 L2 也已清除
        long l2Ttl = l2Cache.getTtl(cacheName, cacheKey);
        assertTrue(l2Ttl <= 0, "clearToken 后 L2 应为 null，实际 TTL=" + l2Ttl);
        log.info("L2 已清除");

        String secondToken = tokenManager.getToken();
        log.info("第二次获取的 Token: {}", secondToken);
        assertNotNull(secondToken, "第二次 Token 不应为 null");
        assertTrue(secondToken.length() > 0, "第二次 Token 不应为空");

        log.info("======================================");
    }

    @Test
    @DisplayName("测试有 security_context 时使用其 hashCode 作为 cache key")
    void testCacheKeyUsesSecurityContextHash() {
        log.info("========== 测试有 security_context 时使用其 hashCode 作为 cache key ==========");

        String securityContext = securityContextProvider.getSecurityContext();
        String cacheKey = generateCacheKey(securityContext);

        log.info("securityContext={}, cacheKey={}", securityContext, cacheKey);
        assertNotNull(securityContext, "securityContext 不应为 null");
        assertEquals(String.valueOf(securityContext.hashCode()), cacheKey,
                "有 security_context 时 cacheKey 应为其 hashCode");

        String token = tokenManager.getToken();
        assertNotNull(token, "Token 不应为 null");
        assertTrue(token.length() > 0, "Token 不应为空");

        log.info("======================================");
    }

    @Test
    @DisplayName("测试 Token 是非空字符串")
    void testTokenIsNonEmptyString() {
        log.info("========== 测试 Token 是非空字符串 ==========");

        String token = tokenManager.getToken();
        log.info("获取的 Token: {}", token);

        assertNotNull(token, "Token 不应为 null");
        assertTrue(token.length() > 0, "Token 不应为空字符串");

        log.info("======================================");
    }
}
