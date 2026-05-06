package io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.client.core.executor.TokenRefreshExecutor;
import io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.manager.RedisTokenManager;
import io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.preload.TokenCachePreloadHandler;
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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Token 预刷新测试
 *
 * <p>验证 {@link TokenCachePreloadHandler} 的 preload 能力：
 * <ul>
 *   <li>needPreload() 通过 JWT 解析判断 EXPIRING_SOON，不依赖框架 TTL 查询</li>
 *   <li>reload() 异步换 token，当前请求返回旧值不阻塞</li>
 *   <li>TokenCachePreloadHandler 正确注册为 Spring Bean</li>
 * </ul>
 *
 * <p>注意：preload 触发需要 L1 miss + L2 hit，且 token 处于 EXPIRING_SOON 状态。
 * 通过将 {@code refresh-before-expire} 设为极大值（Integer.MAX_VALUE），
 * 使所有 token 都被判定为 EXPIRING_SOON，从而在测试中稳定触发 preload。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(
        classes = SimpleAkskRedisTokenManagerTestApplication.class,
        properties = {
                // 将 refresh-before-expire 设为极大值，使所有 token 都被判定为 EXPIRING_SOON
                "io.github.surezzzzzz.sdk.auth.aksk.client.token.refresh-before-expire=2147483647",
                // L1 TTL 设短，确保 L1 miss 后走 L2
                "io.github.surezzzzzz.sdk.cache.l1.expire-seconds=2"
        }
)
class TokenPreloadTest {

    @Autowired
    private RedisTokenManager tokenManager;

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
    @DisplayName("needPreload() 对有效 JWT 应返回 EXPIRING_SOON=true（refresh-before-expire=MAX）")
    void testNeedPreloadReturnsTrueForExpiringToken() {
        // 先获取一个真实 token
        String token = tokenManager.getToken();
        assertNotNull(token, "Token 不应为 null");
        assertTrue(token.startsWith("eyJ"), "Token 应为 JWT 格式");

        // refresh-before-expire=MAX，所有有效 token 都应被判定为 EXPIRING_SOON
        java.util.Optional<Boolean> result = preloadHandler.needPreload(cacheName, "default", token);
        assertTrue(result.isPresent(), "needPreload 应返回非 empty");
        assertTrue(result.get(), "refresh-before-expire=MAX 时所有有效 token 应触发 preload");

        log.info("needPreload() 正确返回 true（token EXPIRING_SOON）");
    }

    @Test
    @DisplayName("needPreload() 对非 String 值应返回 false")
    void testNeedPreloadReturnsFalseForNonString() {
        java.util.Optional<Boolean> result = preloadHandler.needPreload(cacheName, "key", 12345);
        assertTrue(result.isPresent(), "needPreload 应返回非 empty");
        assertFalse(result.get(), "非 String 值应返回 false");
    }

    @Test
    @DisplayName("needPreload() 对无法解析的 token 应返回 false")
    void testNeedPreloadReturnsFalseForUnparsableToken() {
        java.util.Optional<Boolean> result = preloadHandler.needPreload(cacheName, "key", "not-a-jwt");
        assertTrue(result.isPresent(), "needPreload 应返回非 empty");
        assertFalse(result.get(), "无法解析的 token 应返回 false");
    }

    @Test
    @DisplayName("TokenRefreshExecutor.checkTokenStatus() 对 refresh-before-expire=0 时应返回 VALID")
    void testCheckTokenStatusReturnsValidWhenRefreshBeforeExpireIsZero() {
        String token = tokenManager.getToken();
        assertNotNull(token, "Token 不应为 null");

        // refresh-before-expire=0：只要 token 未过期就是 VALID，不会 EXPIRING_SOON
        io.github.surezzzzzz.sdk.auth.aksk.client.core.configuration.SimpleAkskClientCoreProperties props =
                new io.github.surezzzzzz.sdk.auth.aksk.client.core.configuration.SimpleAkskClientCoreProperties();
        props.getToken().setRefreshBeforeExpire(0);
        io.github.surezzzzzz.sdk.auth.aksk.client.core.executor.TokenRefreshExecutor executor =
                new io.github.surezzzzzz.sdk.auth.aksk.client.core.executor.TokenRefreshExecutor(props, null);

        TokenRefreshExecutor.TokenStatus status = executor.checkTokenStatus(token);
        assertEquals(TokenRefreshExecutor.TokenStatus.VALID, status,
                "refresh-before-expire=0 时有效 token 应为 VALID，不触发 preload");
        log.info("refresh-before-expire=0 时 token 状态: {}", status);
    }

    @Test
    @DisplayName("expire-seconds=3600, before-expire-seconds=300：新 token 应为 VALID，不触发 preload")
    void testProductionConfigNewTokenIsValid() {
        // 生产配置：expire-seconds=3600, refresh-before-expire=300
        // 刚换的 token 剩余有效期 ~3600s > 300s，应为 VALID，不触发 preload
        String token = tokenManager.getToken();
        assertNotNull(token, "Token 不应为 null");

        io.github.surezzzzzz.sdk.auth.aksk.client.core.configuration.SimpleAkskClientCoreProperties props =
                new io.github.surezzzzzz.sdk.auth.aksk.client.core.configuration.SimpleAkskClientCoreProperties();
        props.getToken().setRefreshBeforeExpire(300); // 生产默认值
        io.github.surezzzzzz.sdk.auth.aksk.client.core.executor.TokenRefreshExecutor executor =
                new io.github.surezzzzzz.sdk.auth.aksk.client.core.executor.TokenRefreshExecutor(props, null);

        TokenRefreshExecutor.TokenStatus status = executor.checkTokenStatus(token);
        assertEquals(TokenRefreshExecutor.TokenStatus.VALID, status,
                "新 token 剩余 ~3600s > refresh-before-expire=300s，应为 VALID");
        log.info("生产配置(refresh-before-expire=300) 新 token 状态: {}", status);
    }

    @Test
    @DisplayName("expire-seconds=3600, before-expire-seconds=300：refresh-before-expire=3600 时应触发 preload")
    void testProductionConfigExpiringTokenTriggersPreload() {
        // 用 refresh-before-expire 远大于 jwt.expires-in，确保无论 server 配置如何都触发
        // 等价于生产场景中 token 剩余 ≤ refresh-before-expire 时的状态
        String token = tokenManager.getToken();
        assertNotNull(token, "Token 不应为 null");

        io.github.surezzzzzz.sdk.auth.aksk.client.core.configuration.SimpleAkskClientCoreProperties props =
                new io.github.surezzzzzz.sdk.auth.aksk.client.core.configuration.SimpleAkskClientCoreProperties();
        props.getToken().setRefreshBeforeExpire(Integer.MAX_VALUE / 1000); // 足够大，避免 *1000 溢出
        io.github.surezzzzzz.sdk.auth.aksk.client.core.executor.TokenRefreshExecutor executor =
                new io.github.surezzzzzz.sdk.auth.aksk.client.core.executor.TokenRefreshExecutor(props, null);

        TokenRefreshExecutor.TokenStatus status = executor.checkTokenStatus(token);
        assertEquals(TokenRefreshExecutor.TokenStatus.EXPIRING_SOON, status,
                "refresh-before-expire 远大于 jwt.expires-in 时，token 应为 EXPIRING_SOON");
        log.info("refresh-before-expire=MAX/1000 时 token 状态: {}", status);
    }

    @Test
    @DisplayName("getReloadTtlSeconds() 应返回 0（使用 default 实现，走全局配置）")
    void testGetReloadTtlSecondsReturnsZero() {
        int ttl = preloadHandler.getReloadTtlSeconds(cacheName, "default");
        assertEquals(0, ttl, "getReloadTtlSeconds 应返回 0，使用全局 l2.expire-seconds");
    }

    @Test
    @DisplayName("reload() 应能成功从 OAuth2 Server 获取 token")
    void testReloadReturnsToken() {
        Object result = preloadHandler.reload(cacheName, "default");
        assertNotNull(result, "reload() 应返回非 null");
        assertInstanceOf(String.class, result, "reload() 应返回 String");
        String token = (String) result;
        assertTrue(token.startsWith("eyJ"), "reload() 应返回 JWT 格式 token");
        assertEquals(3, token.split("\\.").length, "JWT 应包含 3 个部分（header.payload.signature）");
        log.info("reload() 成功返回 token: {}...", token.substring(0, 20));
    }

    @Test
    @DisplayName("getToken() 触发 preload 后应返回旧值，不阻塞")
    void testGetTokenTriggersPreloadAndReturnsOldValue() throws Exception {
        // 先获取 token，写入 L1+L2
        String firstToken = tokenManager.getToken();
        assertNotNull(firstToken, "第一次 Token 不应为 null");
        log.info("第一次获取 Token: {}", firstToken.substring(0, 20) + "...");

        // 等待 L1 过期（2s）
        log.info("等待 L1 过期（2s）...");
        Thread.sleep(3000);

        // L1 miss → L2 hit → needPreload 返回 true（refresh-before-expire=MAX）→ 触发异步 preload
        // 当前请求应立即返回旧值
        String secondToken = tokenManager.getToken();
        assertNotNull(secondToken, "第二次 Token 不应为 null");
        log.info("第二次获取 Token（preload 触发后）: {}", secondToken.substring(0, 20) + "...");

        // 等待异步 preload 完成
        Thread.sleep(2000);

        // preload 完成后，L1/L2 应有新 token
        String thirdToken = tokenManager.getToken();
        assertNotNull(thirdToken, "第三次 Token 不应为 null");
        log.info("第三次获取 Token（preload 完成后）: {}", thirdToken.substring(0, 20) + "...");

        // 第二次返回旧值（preload 触发时不阻塞）
        assertEquals(firstToken, secondToken, "preload 触发时应返回旧值，不阻塞");

        // 第三次应返回有效 token（preload 已完成写回）
        assertTrue(thirdToken.startsWith("eyJ"), "preload 完成后应返回 JWT 格式 token");
        assertEquals(3, thirdToken.split("\\.").length, "preload 完成后 JWT 应包含 3 个部分");
        log.info("验证通过：preload 触发时返回旧值，不阻塞当前请求");
    }
}
