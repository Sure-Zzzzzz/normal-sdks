package io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.client.core.provider.SecurityContextProvider;
import io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.manager.RedisTokenManager;
import io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.support.CacheKeyHelper;
import io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.test.SimpleAkskRedisTokenManagerTestApplication;
import io.github.surezzzzzz.sdk.cache.layer.L1Cache;
import io.github.surezzzzzz.sdk.cache.layer.L2Cache;
import io.github.surezzzzzz.sdk.cache.manager.SmartCacheManager;
import io.github.surezzzzzz.sdk.redis.route.template.RedisRouteTemplate;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RedisTokenManager 集成测试
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleAkskRedisTokenManagerTestApplication.class)
// local profile 激活 application-local.yml（凭据）；SB 2.3/2.2 无 spring.config.import，必须显式带上
@ActiveProfiles({"local", "nonNullSecurityContext"})
class RedisTokenManagerTest {

    @Autowired
    private RedisTokenManager tokenManager;

    @Autowired
    private SecurityContextProvider securityContextProvider;

    @Autowired
    private RedisRouteTemplate redisRouteTemplate;

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
        // 走 Route 数据源（与缓存同库）删键；Boot 自动装配的 StringRedisTemplate 连默认 db，删不到缓存键
        redisRouteTemplate.execute("sure-auth-aksk-client:", template -> {
            Set<String> keys = template.keys("sure-auth-aksk-client:*");
            if (keys != null && !keys.isEmpty()) {
                template.delete(keys);
            }
            return null;
        });
    }

    private String generateCacheKey(String securityContext) {
        return CacheKeyHelper.generate(securityContext);
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
    @DisplayName("测试有 security_context 时使用 CacheKeyHelper 生成 cache key")
    void testCacheKeyUsesSecurityContextHash() {
        log.info("========== 测试有 security_context 时使用 CacheKeyHelper 生成 cache key ==========");

        String securityContext = securityContextProvider.getSecurityContext();
        String cacheKey = generateCacheKey(securityContext);

        log.info("securityContext={}, cacheKey={}", securityContext, cacheKey);
        assertNotNull(securityContext, "securityContext 不应为 null");
        assertEquals(CacheKeyHelper.generate(securityContext), cacheKey,
                "有 security_context 时 cacheKey 应由 CacheKeyHelper 生成");

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
