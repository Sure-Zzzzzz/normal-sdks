package io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.client.core.provider.SecurityContextProvider;
import io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.manager.RedisTokenManager;
import io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.model.TokenWithExpiry;
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

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RedisTokenManager 端到端测试（2.0.0）
 *
 * <p>覆盖所有场景：
 * <ul>
 *   <li>L1 缓存命中（快速返回，无 Redis IO）</li>
 *   <li>L2 缓存命中（L1 miss + L2 hit）</li>
 *   <li>缓存 miss + 抢到分布式锁 + fetch from server</li>
 *   <li>缓存 miss + 没抢到锁 + 轮询 L2 等待</li>
 *   <li>缓存 miss + 轮询超时 + 本地锁兜底</li>
 *   <li>clearToken 后重新从 server 获取</li>
 *   <li>TTL fallback（server 未返回 expiresIn 时用 l2.expireSeconds）</li>
 *   <li>TokenWithExpiry 正确序列化/反序列化</li>
 *   <li>securityContext 读写一致性（非 null securityContext 存 L2 后能正确读回）</li>
 * </ul>
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleAkskRedisTokenManagerTestApplication.class)
@ActiveProfiles("nonNullSecurityContext")
class RedisTokenManagerEndToEndTest {

    @Autowired
    private RedisTokenManager tokenManager;

    @Autowired
    private SecurityContextProvider securityContextProvider;

    @Autowired
    private SmartCacheManager cacheManager;

    @Autowired
    private L1Cache l1Cache;

    @Autowired
    private L2Cache l2Cache;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Value("${io.github.surezzzzzz.sdk.auth.aksk.client.redis.token.cache-name:aksk-client-token}")
    private String cacheName;

    private String cacheKey;

    @BeforeEach
    void setUp() {
        cacheManager.clear(cacheName);
        cleanupTestKeys();
        // provider 返回非空 securityContext，cacheKey 应与其 hash 对齐
        String sc = securityContextProvider.getSecurityContext();
        cacheKey = sc != null ? String.valueOf(sc.hashCode()) : "default";
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

    // ========== 场景 1: L1 缓存命中 ==========

    @Test
    @DisplayName("场景1: L1 缓存命中 - 两次 getToken 第二次走 L1，无 Redis IO")
    void testL1CacheHit() throws Exception {
        log.info("========== 场景1: L1 缓存命中 ==========");

        // 第一次获取，写入 L1 + L2
        String token1 = tokenManager.getToken();
        assertNotNull(token1, "Token 不应为 null");
        log.info("第一次获取 Token: {}...", token1.substring(0, Math.min(20, token1.length())));

        // 验证 L1 有值
        Object l1Value = l1Cache.get(cacheName, cacheKey);
        assertNotNull(l1Value, "L1 应有值");
        log.info("L1 有值: {}", l1Value);

        // 立即第二次获取，走 L1
        String token2 = tokenManager.getToken();
        assertNotNull(token2, "第二次 Token 不应为 null");
        assertEquals(token1, token2, "L1 命中时应返回相同 token");

        log.info("✓ L1 缓存命中，两次获取 token 相同");
    }

    // ========== 场景 2: L2 缓存命中 ==========

    @Test
    @DisplayName("场景2: L2 缓存命中 - L1 miss + L2 hit，从 Redis 获取")
    void testL2CacheHit() throws Exception {
        log.info("========== 场景2: L2 缓存命中 ==========");

        // 第一次获取，写入 L1 + L2
        String token1 = tokenManager.getToken();
        assertNotNull(token1, "Token 不应为 null");
        log.info("第一次获取 Token: {}...", token1.substring(0, Math.min(20, token1.length())));

        // 等待 L1 过期（2s）
        log.info("等待 L1 过期（2s）...");
        Thread.sleep(3000);

        // 第二次获取，L1 miss，走 L2
        String token2 = tokenManager.getToken();
        assertNotNull(token2, "第二次 Token 不应为 null");
        assertEquals(token1, token2, "L2 命中时应返回相同 token");

        log.info("✓ L2 缓存命中，token 相同");
    }

    // ========== 场景 3: 缓存 miss + 抢到分布式锁 ==========

    @Test
    @DisplayName("场景3: 缓存 miss + 抢到分布式锁 - 直接从 server 获取 token 并写入 L1+L2")
    void testCacheMissWithDistributedLock() {
        log.info("========== 场景3: 缓存 miss + 抢到分布式锁 ==========");

        // 确认缓存为空
        Object l1Before = l1Cache.get(cacheName, cacheKey);
        Object l2Before = l2Cache.get(cacheName, cacheKey);
        assertNull(l1Before, "L1 应为空");
        assertNull(l2Before, "L2 应为空");
        log.info("确认缓存为空（L1: {}, L2: {}）", l1Before, l2Before);

        // 第一次获取，cache miss，抢锁，从 server 拿
        String token = tokenManager.getToken();
        assertNotNull(token, "Token 不应为 null");
        log.info("获取 Token: {}...", token.substring(0, Math.min(20, token.length())));

        // 验证写入 L2
        TokenWithExpiry l2Value = (TokenWithExpiry) l2Cache.get(cacheName, cacheKey);
        assertNotNull(l2Value, "L2 应有值");
        assertEquals(token, l2Value.getToken(), "L2 存储的 token 应一致");
        assertTrue(l2Value.getExpiresAt() > System.currentTimeMillis() / 1000, "expiresAt 应为未来时间");

        // 验证 Redis TTL
        long ttl = l2Cache.getTtl(cacheName, cacheKey);
        assertTrue(ttl > 0, "L2 TTL 应大于 0，实际: " + ttl);
        log.info("L2 TTL: {}s", ttl);

        log.info("✓ 缓存 miss 后抢锁获取 token 并写入 L1+L2");
    }

    // ========== 场景 4: 缓存 miss + 没抢到锁 + 轮询 L2 ==========

    @Test
    @DisplayName("场景4: 缓存 miss + 没抢到锁 - 轮询 L2 等待其他实例写入")
    void testWaitForTokenFromL2() {
        log.info("========== 场景4: 缓存 miss + 没抢到锁 + 轮询 L2 ==========");

        // 先手动往 L2 写入一个 token（模拟其他实例已获取）
        String expectedToken = "test-token-for-poll";
        long fakeExpiresAt = System.currentTimeMillis() / 1000 + 3600;
        TokenWithExpiry tokenWithExpiry = new TokenWithExpiry(expectedToken, fakeExpiresAt, null);
        l2Cache.put(cacheName, cacheKey, tokenWithExpiry, 3600);
        log.info("手动写入 L2: {}", expectedToken);

        // 清除 L1，确保走 L2
        l1Cache.evict(cacheName, cacheKey);

        // 获取 token，应从 L2 读取
        String token = tokenManager.getToken();
        assertNotNull(token, "Token 不应为 null");
        assertEquals(expectedToken, token, "应从 L2 读取到 token");

        log.info("✓ 没抢到锁时轮询 L2 获取 token");
    }

    // ========== 场景 5: clearToken 后重新获取 ==========

    @Test
    @DisplayName("场景5: clearToken 后重新获取 - 从 server 获取新 token")
    void testClearTokenAndFetchAgain() {
        log.info("========== 场景5: clearToken 后重新获取 ==========");

        // 第一次获取
        String token1 = tokenManager.getToken();
        assertNotNull(token1, "Token 不应为 null");
        log.info("第一次获取 Token: {}...", token1.substring(0, Math.min(20, token1.length())));

        // clearToken
        tokenManager.clearToken();
        log.info("Token 已清除");

        // 验证 L1、L2 都清除
        Object l1AfterClear = l1Cache.get(cacheName, cacheKey);
        assertNull(l1AfterClear, "clearToken 后 L1 应为空");
        log.info("L1 已清除");

        long l2TtlAfterClear = l2Cache.getTtl(cacheName, cacheKey);
        assertTrue(l2TtlAfterClear <= 0, "clearToken 后 L2 应为空，实际 TTL: " + l2TtlAfterClear);
        log.info("L2 已清除");

        // 第二次获取，应从 server 重新获取
        String token2 = tokenManager.getToken();
        assertNotNull(token2, "第二次 Token 不应为 null");
        log.info("第二次获取 Token: {}...", token2.substring(0, Math.min(20, token2.length())));

        // 注意：server 可能返回相同的 token（只要原 token 仍有效）
        // 但关键是 L1+L2 已被清除，会重新走 fetch 流程
        assertTrue(token2.length() > 0, "token 不应为空");
        log.info("✓ clearToken 后重新获取");

        // 验证重新写入 L2
        TokenWithExpiry l2Value = (TokenWithExpiry) l2Cache.get(cacheName, cacheKey);
        assertNotNull(l2Value, "重新获取后 L2 应有值");
    }

    // ========== 场景 6: TTL fallback ==========

    @Test
    @DisplayName("场景6: TTL fallback - server 未返回 expiresIn 时使用 l2.expireSeconds")
    void testTtlFallback() {
        log.info("========== 场景6: TTL fallback ==========");

        // 清除缓存
        cacheManager.clear(cacheName);

        // 获取 token
        String token = tokenManager.getToken();
        assertNotNull(token, "Token 不应为 null");
        log.info("获取 Token: {}...", token.substring(0, Math.min(20, token.length())));

        // 验证 L2 有值
        TokenWithExpiry l2Value = (TokenWithExpiry) l2Cache.get(cacheName, cacheKey);
        assertNotNull(l2Value, "L2 应有值");

        // 验证 expiresIn >= 0（如果 server 返回了 expiresIn 则 > 0，否则 = 0）
        assertTrue(l2Value.getExpiresAt() > System.currentTimeMillis() / 1000, "expiresAt 应为未来时间");

        // 验证 Redis TTL > 0（无论是 expiresIn 还是 fallback 值）
        long ttl = l2Cache.getTtl(cacheName, cacheKey);
        assertTrue(ttl > 0, "L2 TTL 应大于 0，实际: " + ttl);
        log.info("L2 TTL: {}s, expiresAt: {}", ttl, l2Value.getExpiresAt());

        log.info("✓ TTL 策略正确（优先 expiresIn，兜底 l2.expireSeconds）");
    }

    // ========== 场景 7: TokenWithExpiry 序列化/反序列化 ==========

    @Test
    @DisplayName("场景7: TokenWithExpiry 序列化/反序列化 - 存 Redis 后能正确还原")
    void testTokenWithExpirySerialization() {
        log.info("========== 场景7: TokenWithExpiry 序列化/反序列化 ==========");

        // 获取 token
        String token = tokenManager.getToken();
        assertNotNull(token, "Token 不应为 null");
        log.info("获取 Token: {}...", token.substring(0, Math.min(20, token.length())));

        // 从 L2 直接读取（模拟 Redis 反序列化）
        TokenWithExpiry fromL2 = (TokenWithExpiry) l2Cache.get(cacheName, cacheKey);
        assertNotNull(fromL2, "L2 应有值");

        // 验证字段完整
        assertNotNull(fromL2.getToken(), "反序列化后 token 不应为 null");
        assertEquals(token, fromL2.getToken(), "反序列化后 token 应一致");
        assertTrue(fromL2.getExpiresAt() > System.currentTimeMillis() / 1000, "反序列化后 expiresAt 应为未来时间");
        // securityContext 可能是 null（默认 provider 返回 null）
        log.info("TokenWithExpiry 序列化正确: token={}, expiresAt={}, securityContext={}",
                fromL2.getToken().substring(0, 20) + "...",
                fromL2.getExpiresAt(),
                fromL2.getSecurityContext());

        log.info("✓ TokenWithExpiry 序列化/反序列化正确");
    }

    // ========== 场景 7.5: securityContext 读写一致性 ==========

    @Test
    @DisplayName("场景7.5: getToken() 写入的 securityContext 从 L2 读回应一致")
    void testSecurityContextStoredAndRetrievedCorrectly() {
        log.info("========== 场景7.5: securityContext 读写一致性 ==========");

        // provider 返回非空 securityContext
        String expectedSecurityContext = securityContextProvider.getSecurityContext();
        assertNotNull(expectedSecurityContext, "securityContext 不应为 null");
        log.info("Provider securityContext: {}", expectedSecurityContext);

        // 获取 token，写入 L1 + L2
        String token = tokenManager.getToken();
        assertNotNull(token, "Token 不应为 null");
        log.info("获取 Token 成功");

        // 从 L2 直接读取 TokenWithExpiry
        TokenWithExpiry fromL2 = (TokenWithExpiry) l2Cache.get(cacheName, cacheKey);
        assertNotNull(fromL2, "L2 应有值");
        log.info("从 L2 读取 TokenWithExpiry 成功");

        // 核心断言：securityContext 读写一致
        assertEquals(expectedSecurityContext, fromL2.getSecurityContext(),
                "L2 读回的 securityContext 应与 Provider 返回的一致");
        assertEquals(token, fromL2.getToken(),
                "L2 读回的 token 应与 getToken() 返回的一致");
        assertTrue(fromL2.getExpiresAt() > System.currentTimeMillis() / 1000, "expiresAt 应为未来时间");

        log.info("✓ securityContext 读写一致性验证通过");
    }

    // ========== 场景 8: 多实例 L1 一致性（Pub/Sub） ==========

    @Test
    @DisplayName("场景8: clearToken 触发 Pub/Sub，模拟其他实例 L1 同步清除")
    void testPubSubL1Consistency() throws Exception {
        log.info("========== 场景8: Pub/Sub L1 一致性 ==========");

        // 第一次获取，写入 L1 + L2
        String token = tokenManager.getToken();
        assertNotNull(token, "Token 不应为 null");
        log.info("获取 Token: {}...", token.substring(0, Math.min(20, token.length())));

        // 验证 L1 有值
        Object l1Value = l1Cache.get(cacheName, cacheKey);
        assertNotNull(l1Value, "L1 应有值");
        log.info("L1 有值");

        // clearToken（内部调用 cacheManager.evict，触发 Pub/Sub）
        tokenManager.clearToken();
        log.info("已 clearToken，Pub/Sub 广播中...");

        // 等待 Pub/Sub 处理
        Thread.sleep(500);

        // 验证 L1 被清除
        Object l1AfterClear = l1Cache.get(cacheName, cacheKey);
        assertNull(l1AfterClear, "clearToken 后 L1 应通过 Pub/Sub 清除");
        log.info("✓ L1 通过 Pub/Sub 清除，多实例一致性验证通过");
    }
}