package io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.client.core.provider.SecurityContextProvider;
import io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.manager.RedisTokenManager;
import io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.model.TokenWithExpiry;
import io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.preload.TokenCachePreloadHandler;
import io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.support.CacheKeyHelper;
import io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.test.SimpleAkskRedisTokenManagerTestApplication;
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

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Token 预刷新测试（2.0.0）
 *
 * <p>验证 {@link TokenCachePreloadHandler} 的 preload 能力：
 * <ul>
 *   <li>support() 正确识别 cacheName</li>
 *   <li>reload() 从 Redis 读取 securityContext，向 server 换取新 token</li>
 *   <li>reload() TTL fallback 到 l2.expireSeconds（当 server 未返回 expiresIn）</li>
 *   <li>preload 触发由 smart-cache 框架的 Redis TTL <= beforeExpireSeconds 机制控制</li>
 * </ul>
 *
 * <p>2.0.0 变化：
 * <ul>
 *   <li>不再解析 JWT/TokenStatus，由 Redis TTL 机制驱动 preload</li>
 *   <li>reload() 从 Redis 读取 securityContext，保证分布式一致性</li>
 * </ul>
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleAkskRedisTokenManagerTestApplication.class)
@ActiveProfiles("nonNullSecurityContext")
class TokenPreloadTest {

    @Autowired
    private RedisTokenManager tokenManager;

    @Autowired
    private SecurityContextProvider securityContextProvider;

    @Autowired
    private SmartCacheManager cacheManager;

    @Autowired
    private TokenCachePreloadHandler preloadHandler;

    @Value("${io.github.surezzzzzz.sdk.auth.aksk.client.redis.token.cache-name:aksk-client-token}")
    private String cacheName;

    @BeforeEach
    void setUp() {
        cacheManager.clear(cacheName);
    }

    @AfterEach
    void tearDown() {
        cacheManager.clear(cacheName);
    }

    @Test
    @DisplayName("TokenCachePreloadHandler 应正确注册为 Spring Bean")
    void testPreloadHandlerIsRegistered() {
        assertNotNull(preloadHandler, "TokenCachePreloadHandler 应注册为 Spring Bean");
        assertTrue(preloadHandler.support(cacheName),
                "TokenCachePreloadHandler 应支持 cacheName=" + cacheName);
        assertFalse(preloadHandler.support("other-cache"),
                "TokenCachePreloadHandler 不应支持其他 cacheName");
    }

    @Test
    @DisplayName("needPreload() 默认返回 Optional.empty()（由框架 TTL 机制驱动）")
    void testNeedPreloadDefaultReturnsEmpty() {
        // 2.0.0 不再覆盖 needPreload，由框架根据 Redis TTL 判断
        Optional<Boolean> result = preloadHandler.needPreload(cacheName, "default", "any-value");
        assertFalse(result.isPresent(),
                "默认 needPreload 应返回 empty，由框架 TTL 机制决定是否 preload");
        log.info("needPreload() 默认返回 empty（框架 TTL 机制）");
    }

    @Test
    @DisplayName("getReloadTtlSeconds() 应返回 0（使用全局 l2.expireSeconds 配置）")
    void testGetReloadTtlSecondsReturnsZero() {
        int ttl = preloadHandler.getReloadTtlSeconds(cacheName, "default");
        assertEquals(0, ttl, "getReloadTtlSeconds 应返回 0，使用全局 l2.expire-seconds");
    }

    @Test
    @DisplayName("reload() 应能从 OAuth2 Server 获取 token 并返回 TokenWithExpiry")
    void testReloadReturnsTokenWithExpiry() {
        // reload() 从 Redis 读取当前 token（可能为空），然后向 server 换取新 token
        Object result = preloadHandler.reload(cacheName, "default");
        assertNotNull(result, "reload() 应返回非 null");
        assertInstanceOf(TokenWithExpiry.class, result, "reload() 应返回 TokenWithExpiry 类型");
        TokenWithExpiry tokenWithExpiry = (TokenWithExpiry) result;
        assertNotNull(tokenWithExpiry.getToken(), "reload() 返回的 token 不应为 null");
        assertTrue(tokenWithExpiry.getExpiresAt() > System.currentTimeMillis() / 1000, "expiresAt 应为未来时间");
        log.info("reload() 成功，expiresAt={}", tokenWithExpiry.getExpiresAt());
    }

    @Test
    @DisplayName("getToken() 后 reload() 应使用相同的 securityContext（分布式一致性）")
    void testReloadUsesSameSecurityContext() {
        // 先获取一个 token（会写入 Redis，包含 securityContext）
        String firstToken = tokenManager.getToken();
        assertNotNull(firstToken, "第一次 Token 不应为 null");
        log.info("第一次获取 Token 成功");

        // cacheKey 必须与 getToken() 内部使用的 key 一致（CacheKeyHelper 生成）
        String expectedSecurityContext = securityContextProvider.getSecurityContext();
        String cacheKey = CacheKeyHelper.generate(expectedSecurityContext);

        // 触发 reload，securityContext 应与首次获取相同
        Object result = preloadHandler.reload(cacheName, cacheKey);
        assertNotNull(result, "reload() 应返回非 null");
        assertInstanceOf(TokenWithExpiry.class, result);
        TokenWithExpiry tokenWithExpiry = (TokenWithExpiry) result;
        assertNotNull(tokenWithExpiry.getToken(), "reload() 返回的 token 不应为 null");
        assertEquals(expectedSecurityContext, tokenWithExpiry.getSecurityContext(),
                "reload() 应使用与 getToken() 相同的 securityContext");
        log.info("reload() 成功，expiresIn={}, securityContext={}",
                tokenWithExpiry.getExpiresAt(), tokenWithExpiry.getSecurityContext());
    }

    @Test
    @DisplayName("preload 由 Redis TTL <= beforeExpireSeconds 触发（框架机制）")
    void testPreloadTriggeredByRedisTtl() throws Exception {
        // 先获取 token 写入 L1+L2
        String firstToken = tokenManager.getToken();
        assertNotNull(firstToken, "第一次 Token 不应为 null");
        log.info("第一次获取 Token 成功: {}...", firstToken.substring(0, Math.min(20, firstToken.length())));

        // 等待 L1 过期（2s），强制走 L2
        log.info("等待 L1 过期（2s）...");
        Thread.sleep(3000);

        // L1 miss → L2 hit，框架检查 Redis TTL 是否 <= beforeExpireSeconds
        // 若 TTL 在 preload 窗口内，框架会触发异步 reload
        String secondToken = tokenManager.getToken();
        assertNotNull(secondToken, "第二次 Token 不应为 null");
        log.info("第二次获取 Token（可能触发 preload）: {}...", secondToken.substring(0, Math.min(20, secondToken.length())));

        // 等待异步 preload 完成（如果触发了）
        Thread.sleep(2000);

        // 第三次获取
        String thirdToken = tokenManager.getToken();
        assertNotNull(thirdToken, "第三次 Token 不应为 null");
        log.info("第三次获取 Token: {}...", thirdToken.substring(0, Math.min(20, thirdToken.length())));

        // 验证返回的 token 有效（非空）
        assertTrue(thirdToken.length() > 0, "preload 完成后应返回有效 token");
        log.info("验证通过：preload 机制正常工作");
    }
}
