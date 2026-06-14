package io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.client.core.provider.SecurityContextProvider;
import io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.manager.RedisTokenManager;
import io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.model.TokenWithExpiry;
import io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.support.CacheKeyHelper;
import io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.test.SimpleAkskRedisTokenManagerTestApplication;
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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RedisTokenManager 多 securityContext 端到端测试（2.0.1 防 hashCode 碰撞）
 *
 * <p>动机：2.0.1 将 cacheKey 生成算法从 {@code String.hashCode()}（32-bit）升级为
 * SHA-256 截断 128-bit hex。本测试用真实 Spring 上下文 + Redis + OAuth2 Server
 * 验证："不同 securityContext → 不同 cacheKey → 不同 L2 缓存条目 → 互不污染"。
 *
 * <p>覆盖场景：
 * <ul>
 *   <li>场景 1：两个不同的多租户 securityContext，L2 各有独立缓存条目，互相隔离</li>
 *   <li>场景 2：clearToken() 只清除当前 securityContext 对应的 cacheKey，不影响其他</li>
 *   <li>场景 3（关键）：经典 hashCode 碰撞输入 "Aa" / "BB"（两者 hashCode 都是 2112）
 *       在 2.0.1 中应映射到不同 cacheKey，证明老 hashCode 时代的跨上下文串 Token 问题已根除</li>
 * </ul>
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = {
        SimpleAkskRedisTokenManagerTestApplication.class,
        RedisTokenManagerMultiSecurityContextEndToEndTest.TestConfig.class
})
@ActiveProfiles("multiSecurityContext")
class RedisTokenManagerMultiSecurityContextEndToEndTest {

    /**
     * 可变 SecurityContextProvider：通过 {@link AtomicReference} 持有 securityContext，
     * 测试用例运行时可切换值，模拟多租户在同一进程中并存。
     */
    static class MutableSecurityContextProvider implements SecurityContextProvider {
        private final AtomicReference<String> ref = new AtomicReference<>();

        void set(String securityContext) {
            ref.set(securityContext);
        }

        @Override
        public String getSecurityContext() {
            return ref.get();
        }
    }

    @Configuration
    @Profile("multiSecurityContext")
    static class TestConfig {
        @Bean
        public SecurityContextProvider securityContextProvider() {
            return new MutableSecurityContextProvider();
        }
    }

    @Autowired
    private RedisTokenManager tokenManager;

    @Autowired
    private SecurityContextProvider securityContextProvider;

    @Autowired
    private SmartCacheManager cacheManager;

    @Autowired
    private L2Cache l2Cache;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Value("${io.github.surezzzzzz.sdk.auth.aksk.client.redis.token.cache-name:aksk-client-token}")
    private String cacheName;

    private MutableSecurityContextProvider mutableProvider;

    @BeforeEach
    void setUp() {
        mutableProvider = (MutableSecurityContextProvider) securityContextProvider;
        cacheManager.clear(cacheName);
        cleanupTestKeys();
    }

    @AfterEach
    void tearDown() {
        cacheManager.clear(cacheName);
        cleanupTestKeys();
        mutableProvider.set(null);
    }

    private void cleanupTestKeys() {
        Set<String> keys = stringRedisTemplate.keys("sure-auth-aksk-client:*");
        if (keys != null && !keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
    }

    // ========== 场景 1：两个多租户 securityContext 互相隔离 ==========

    @Test
    @DisplayName("场景1: 两个不同 securityContext 应在 L2 产生两条独立缓存条目，互不污染")
    void testTwoDifferentSecurityContextsHaveIsolatedCacheEntries() {
        log.info("========== 场景1: 多租户 securityContext L2 隔离 ==========");

        String scA = "{\"tenant_id\":\"tenant-A\",\"user_id\":\"alice\"}";
        String scB = "{\"tenant_id\":\"tenant-B\",\"user_id\":\"bob\"}";
        String keyA = CacheKeyHelper.generate(scA);
        String keyB = CacheKeyHelper.generate(scB);

        assertNotEquals(keyA, keyB, "不同 securityContext 应生成不同 cacheKey");
        log.info("scA={} -> cacheKey={}", scA, keyA);
        log.info("scB={} -> cacheKey={}", scB, keyB);

        // 租户 A 获取 token
        mutableProvider.set(scA);
        String tokenA = tokenManager.getToken();
        assertNotNull(tokenA, "tenant-A token 不应为 null");

        // 租户 B 获取 token
        mutableProvider.set(scB);
        String tokenB = tokenManager.getToken();
        assertNotNull(tokenB, "tenant-B token 不应为 null");

        // L2 中两个 key 都应有独立条目，且条目内的 securityContext 字段对得上
        TokenWithExpiry l2A = (TokenWithExpiry) l2Cache.get(cacheName, keyA);
        TokenWithExpiry l2B = (TokenWithExpiry) l2Cache.get(cacheName, keyB);

        assertNotNull(l2A, "L2 应有 tenant-A 的条目");
        assertNotNull(l2B, "L2 应有 tenant-B 的条目");
        assertEquals(scA, l2A.getSecurityContext(), "tenant-A 条目的 securityContext 应回写一致");
        assertEquals(scB, l2B.getSecurityContext(), "tenant-B 条目的 securityContext 应回写一致");
        assertEquals(tokenA, l2A.getToken(), "L2(A) 的 token 与 getToken() 返回值一致");
        assertEquals(tokenB, l2B.getToken(), "L2(B) 的 token 与 getToken() 返回值一致");

        // 关键：切回 scA 再调一次，必须命中 keyA 自己的缓存，不能拿到 tokenB
        mutableProvider.set(scA);
        String tokenAAgain = tokenManager.getToken();
        assertEquals(tokenA, tokenAAgain, "切回 scA 时应命中 keyA 缓存，绝不能串到 tokenB");

        log.info("✓ 两个 securityContext 各自独立缓存，互不污染");
    }

    // ========== 场景 2：clearToken 仅清除当前 securityContext 对应 key ==========

    @Test
    @DisplayName("场景2: clearToken 只清当前 securityContext 的 cacheKey，不影响其他租户")
    void testClearTokenOnlyAffectsCurrentSecurityContext() {
        log.info("========== 场景2: clearToken 隔离性 ==========");

        String scA = "{\"tenant_id\":\"tenant-A\",\"user_id\":\"alice\"}";
        String scB = "{\"tenant_id\":\"tenant-B\",\"user_id\":\"bob\"}";
        String keyA = CacheKeyHelper.generate(scA);
        String keyB = CacheKeyHelper.generate(scB);

        // 两租户分别拉 token，让 L2 各有一条
        mutableProvider.set(scA);
        String tokenA = tokenManager.getToken();
        mutableProvider.set(scB);
        String tokenB = tokenManager.getToken();
        assertNotNull(l2Cache.get(cacheName, keyA), "前置：L2(A) 应有值");
        assertNotNull(l2Cache.get(cacheName, keyB), "前置：L2(B) 应有值");

        // 在 scA 上下文中调 clearToken
        mutableProvider.set(scA);
        tokenManager.clearToken();
        log.info("已在 scA 上下文 clearToken");

        // L2(A) 应被清除，L2(B) 应原封不动
        assertNull(l2Cache.get(cacheName, keyA), "clearToken(scA) 后 L2(A) 应为空");
        TokenWithExpiry l2BAfter = (TokenWithExpiry) l2Cache.get(cacheName, keyB);
        assertNotNull(l2BAfter, "clearToken(scA) 不应影响 L2(B)");
        assertEquals(tokenB, l2BAfter.getToken(), "tenant-B 的 token 应仍然存在且不变");
        assertEquals(scB, l2BAfter.getSecurityContext(), "tenant-B 条目的 securityContext 应仍然是 scB");

        log.info("✓ clearToken 严格按 cacheKey 隔离，多租户互不打扰");
    }

    // ========== 场景 3：经典 hashCode 碰撞输入在 2.0.1 中被隔离开 ==========

    @Test
    @DisplayName("场景3: 经典 hashCode 碰撞输入(Aa/BB)在 2.0.1 中映射到不同 cacheKey，杜绝跨上下文串 Token")
    void testHashCodeCollisionInputsHaveIsolatedCacheEntries() {
        log.info("========== 场景3: hashCode 碰撞输入隔离（2.0.1 升级核心动机） ==========");

        // 老 hashCode 算法下，"Aa" 与 "BB" hashCode 同为 2112 —— 会被映射到同一 cacheKey 串 Token
        String scCollideA = "Aa";
        String scCollideB = "BB";
        assertEquals(scCollideA.hashCode(), scCollideB.hashCode(),
                "前置：两输入在老 hashCode 算法下确实碰撞");

        String keyA = CacheKeyHelper.generate(scCollideA);
        String keyB = CacheKeyHelper.generate(scCollideB);
        assertNotEquals(keyA, keyB,
                "2.0.1 SHA-256 算法下，hashCode 碰撞的两输入必须映射到不同 cacheKey");
        log.info("Aa -> {}", keyA);
        log.info("BB -> {}", keyB);

        // 用 Aa 拉 token
        mutableProvider.set(scCollideA);
        String tokenA = tokenManager.getToken();
        assertNotNull(tokenA, "scCollideA token 不应为 null");

        // 用 BB 拉 token
        mutableProvider.set(scCollideB);
        String tokenB = tokenManager.getToken();
        assertNotNull(tokenB, "scCollideB token 不应为 null");

        // L2 必须为两者各自存一条；如果还是老 hashCode 算法，BB 会覆盖 Aa（同 cacheKey），
        // 这里 keyA 会读不到独立条目（或读到的 securityContext 字段错乱）
        TokenWithExpiry l2A = (TokenWithExpiry) l2Cache.get(cacheName, keyA);
        TokenWithExpiry l2B = (TokenWithExpiry) l2Cache.get(cacheName, keyB);
        assertNotNull(l2A, "L2(Aa) 应有独立条目（老算法下会被 BB 覆盖）");
        assertNotNull(l2B, "L2(BB) 应有独立条目");

        // 核心断言：L2 里 securityContext 回写字段必须正确归属，没有串台
        assertEquals(scCollideA, l2A.getSecurityContext(),
                "L2(Aa) 的 securityContext 必须是 'Aa'，绝不能是 'BB'（防 hashCode 碰撞串 Token）");
        assertEquals(scCollideB, l2B.getSecurityContext(),
                "L2(BB) 的 securityContext 必须是 'BB'，绝不能是 'Aa'");

        // 切回 Aa 再调 getToken，必须命中 keyA 的缓存，不能拿到 BB 那条
        mutableProvider.set(scCollideA);
        String tokenAAgain = tokenManager.getToken();
        assertEquals(tokenA, tokenAAgain,
                "切回 Aa 必须命中 keyA 自己的缓存（老 hashCode 时代会错命中 BB 的 token）");

        log.info("✓ hashCode 碰撞输入在 2.0.1 中被 SHA-256 严格隔离，跨上下文串 Token 问题根除");
    }
}
