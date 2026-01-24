package io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.client.core.provider.SecurityContextProvider;
import io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.manager.RedisTokenManager;
import io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.strategy.RedisTokenCacheStrategy;
import io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.test.SimpleAkskRedisTokenManagerTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RedisTokenManager 集成测试
 * <p>
 * 测试 RedisTokenManager 的核心功能
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleAkskRedisTokenManagerTestApplication.class)
class RedisTokenManagerTest {

    @Autowired
    private RedisTokenManager tokenManager;

    @Autowired
    private RedisTokenCacheStrategy tokenCacheStrategy;

    @Autowired
    private SecurityContextProvider securityContextProvider;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @BeforeEach
    void setUp() {
        log.info("======================================");
        log.info("清理 Redis 测试数据");
        log.info("======================================");
        // 只删除测试相关的 key（基于 me 标识）
        cleanupTestKeys();
    }

    @AfterEach
    void tearDown() {
        log.info("======================================");
        log.info("测试结束，清理 Redis 测试数据");
        log.info("======================================");
        // 清理测试数据
        cleanupTestKeys();
    }

    /**
     * 清理测试相关的 Redis Key
     * <p>
     * 只删除 sure-auth-aksk-client:redis-token-manager-test:* 的 key
     */
    private void cleanupTestKeys() {
        String pattern = "sure-auth-aksk-client:redis-token-manager-test:*";
        Set<String> keys = stringRedisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            log.info("删除测试 Key: {}", keys);
            stringRedisTemplate.delete(keys);
        }
    }

    @Test
    @DisplayName("测试首次获取 Token - 缓存为空")
    void testGetTokenFirstTime() {
        log.info("======================================");
        log.info("测试首次获取 Token - 缓存为空");
        log.info("======================================");

        log.info("开始获取 Token（缓存为空，应从服务器获取）...");
        String token = tokenManager.getToken();

        log.info("获取的 Token: {}", token);
        assertNotNull(token, "Token 不应为 null");
        assertTrue(token.length() > 0, "Token 不应为空字符串");

        // 验证 Token 被缓存到 Redis
        String securityContext = securityContextProvider.getSecurityContext();
        String cacheKey = tokenCacheStrategy.generateCacheKey(securityContext);
        String cachedToken = stringRedisTemplate.opsForValue().get(cacheKey);

        log.info("缓存 Key: {}", cacheKey);
        log.info("缓存的 Token: {}", cachedToken);
        assertNotNull(cachedToken, "Token 应被缓存到 Redis");
        assertEquals(token, cachedToken, "获取的 Token 应与缓存的 Token 一致");

        log.info("======================================");
    }

    @Test
    @DisplayName("测试从缓存获取 Token")
    void testGetTokenFromCache() {
        log.info("======================================");
        log.info("测试从缓存获取 Token");
        log.info("======================================");

        log.info("第一次获取 Token...");
        String firstToken = tokenManager.getToken();
        log.info("第一次获取的 Token: {}", firstToken);

        log.info("第二次获取 Token（应从缓存）...");
        String secondToken = tokenManager.getToken();
        log.info("第二次获取的 Token: {}", secondToken);

        assertEquals(firstToken, secondToken, "两次获取的 Token 应相同（从缓存）");

        log.info("======================================");
    }

    @Test
    @DisplayName("测试清除 Token")
    void testClearToken() {
        log.info("======================================");
        log.info("测试清除 Token");
        log.info("======================================");

        log.info("先获取 Token...");
        String firstToken = tokenManager.getToken();
        log.info("第一次获取的 Token: {}", firstToken);

        String securityContext = securityContextProvider.getSecurityContext();
        String cacheKey = tokenCacheStrategy.generateCacheKey(securityContext);

        // 验证缓存存在
        String cachedBeforeClear = stringRedisTemplate.opsForValue().get(cacheKey);
        assertNotNull(cachedBeforeClear, "清除前缓存应存在");

        log.info("清除 Token...");
        tokenManager.clearToken();

        // 验证缓存已清除
        String cachedAfterClear = stringRedisTemplate.opsForValue().get(cacheKey);
        log.info("清除后的缓存: {}", cachedAfterClear);
        assertNull(cachedAfterClear, "清除后缓存应为 null");

        log.info("再次获取 Token（应从服务器重新获取）...");
        String secondToken = tokenManager.getToken();
        log.info("第二次获取的 Token: {}", secondToken);

        assertNotNull(secondToken, "第二次获取的 Token 不应为 null");
        // 注意：新 Token 可能与旧 Token 相同或不同，取决于服务器实现

        log.info("======================================");
    }

    @Test
    @DisplayName("测试带 security_context 获取 Token")
    void testGetTokenWithSecurityContext() {
        log.info("======================================");
        log.info("测试带 security_context 获取 Token");
        log.info("======================================");

        // 注意：默认的 SecurityContextProvider 返回 null
        // 这个测试验证在无 security_context 的场景下能正常工作
        String token = tokenManager.getToken();

        log.info("获取的 Token: {}", token);
        assertNotNull(token, "Token 不应为 null");

        // 验证使用了默认 Key
        String securityContext = securityContextProvider.getSecurityContext();
        String cacheKey = tokenCacheStrategy.generateCacheKey(securityContext);
        log.info("缓存 Key: {}", cacheKey);
        assertTrue(cacheKey.contains("default"), "无 security_context 时应使用 default Key");

        log.info("======================================");
    }

    @Test
    @DisplayName("测试检查 Token 状态 - 有效 Token")
    void testCheckTokenStatusValidToken() {
        log.info("======================================");
        log.info("测试检查 Token 状态 - 有效 Token");
        log.info("======================================");

        log.info("先获取一个新 Token...");
        String token = tokenManager.getToken();
        log.info("获取的 Token: {}", token);

        // 创建 TokenRefreshExecutor 检查状态
        // 注意：这里直接使用 tokenManager 内部的逻辑会更好，但为了测试我们手动检查
        assertNotNull(token, "Token 不应为 null");
        assertTrue(token.startsWith("eyJ"), "Token 应该是 JWT 格式（以 eyJ 开头）");

        log.info("======================================");
    }
}
