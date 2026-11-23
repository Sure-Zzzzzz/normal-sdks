package io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.client.core.constant.SimpleAkskClientCoreConstant;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.exception.ConfigurationException;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.manager.ProfileTokenManager;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.support.AkskClientProfileRegistry;
import io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.model.TokenWithExpiry;
import io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.preload.TokenCachePreloadHandler;
import io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.support.CacheKeyHelper;
import io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.support.ProfileCacheKeyHelper;
import io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.test.SimpleAkskRedisTokenManagerTestApplication;
import io.github.surezzzzzz.sdk.cache.layer.L1Cache;
import io.github.surezzzzzz.sdk.cache.layer.L2Cache;
import io.github.surezzzzzz.sdk.cache.manager.SmartCacheManager;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RedisTokenManager 多 AKP Profile 端到端测试
 *
 * <p>依赖真实 Redis 与 AKSK Server：默认凭据取顶层配置，命名 Profile {@code second} 取本地
 * {@code application-local.yml} 的第二组已准入 AKP。验证命名 Profile 取 Token、两个 Profile 的缓存与锁
 * 互不串扰、{@code clearToken(profile)} 只清自身、不存在的 Profile 响亮失败、按 Profile 预刷新。
 *
 * @author surezzzzzz
 */
@Slf4j
@ActiveProfiles("local")
@SpringBootTest(classes = SimpleAkskRedisTokenManagerTestApplication.class)
class RedisTokenManagerProfileEndToEndTest {

    private static final String SECOND = "second";
    private static final String MISSING = "missing-profile";

    @Autowired
    private ProfileTokenManager profileTokenManager;
    @Autowired
    private AkskClientProfileRegistry registry;
    @Autowired
    private TokenCachePreloadHandler preloadHandler;
    @Autowired
    private SmartCacheManager cacheManager;
    @Autowired
    private L1Cache l1Cache;
    @Autowired
    private L2Cache l2Cache;
    @Value("${io.github.surezzzzzz.sdk.auth.aksk.client.redis.token.cache-name:aksk-client-token}")
    private String cacheName;

    /**
     * 断言两次读取命中同一缓存值；失败信息不携带 Token 明文
     */
    private static void assertSameToken(String expected, String actual, String message) {
        assertTrue(expected.equals(actual), message);
    }

    /**
     * 远期过期时间（epoch 秒），用于构造不会因 TTL 失效的预置缓存值
     */
    private static long farFuture() {
        return System.currentTimeMillis() / 1000 + 3600;
    }

    @BeforeEach
    void setUp() {
        cacheManager.clear(cacheName);
    }

    @Test
    @DisplayName("命名 Profile 可独立获取 Token，且与默认 Profile 的 Token 不同")
    void testNamedProfileFetchesItsOwnToken() {
        assertTrue(registry.contains(SECOND));
        String secondToken = profileTokenManager.getToken(SECOND);
        String defaultToken = profileTokenManager.getToken();
        assertNotNull(secondToken);
        assertNotNull(defaultToken);
        assertFalse(secondToken.isEmpty());
        assertFalse(defaultToken.isEmpty());
        // 不用 assertNotEquals：失败时不把 Token 明文打进测试报告
        assertFalse(secondToken.equals(defaultToken), "两个 Profile 使用不同 AKP，Token 不应相同");
    }

    @Test
    @DisplayName("清理默认 Profile 不影响命名 Profile 的缓存")
    void testClearDefaultKeepsNamedProfileCache() {
        String secondToken = profileTokenManager.getToken(SECOND);
        profileTokenManager.clearToken();
        assertSameToken(secondToken, profileTokenManager.getToken(SECOND),
                "命名 Profile 缓存不应被默认 Profile 的清理波及");
    }

    @Test
    @DisplayName("清理命名 Profile 不影响默认 Profile 的缓存")
    void testClearNamedKeepsDefaultProfileCache() {
        String defaultToken = profileTokenManager.getToken();
        profileTokenManager.clearToken(SECOND);
        assertSameToken(defaultToken, profileTokenManager.getToken(),
                "默认 Profile 缓存不应被命名 Profile 的清理波及");
    }

    @Test
    @DisplayName("不存在的 Profile 响亮失败，不回落到默认凭据")
    void testUnknownProfileFailsLoudly() {
        assertFalse(registry.contains(MISSING));
        assertThrows(ConfigurationException.class, () -> profileTokenManager.getToken(MISSING));
    }

    @Test
    @DisplayName("两个 Profile 并发取 Token 互不串扰，各自命中同一缓存值")
    void testConcurrentFetchAcrossProfiles() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<String>> futures = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                // 偶数位提交默认 Profile，奇数位提交命名 Profile
                futures.add(pool.submit(() -> profileTokenManager.getToken()));
                futures.add(pool.submit(() -> profileTokenManager.getToken(SECOND)));
            }
            Set<String> defaultTokens = new HashSet<>();
            Set<String> secondTokens = new HashSet<>();
            for (int i = 0; i < futures.size(); i++) {
                String token = futures.get(i).get(30, TimeUnit.SECONDS);
                assertNotNull(token);
                (i % 2 == 0 ? defaultTokens : secondTokens).add(token);
            }
            assertEquals(1, defaultTokens.size(), "并发下默认 Profile 应命中同一缓存值");
            assertEquals(1, secondTokens.size(), "并发下命名 Profile 应命中同一缓存值");
            for (String secondToken : secondTokens) {
                for (String defaultToken : defaultTokens) {
                    assertFalse(secondToken.equals(defaultToken), "两个 Profile 的 Token 不应相同");
                }
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("按 Profile 预刷新使用该 Profile 的凭据身份")
    void testPreloadReloadUsesRequestedProfile() {
        profileTokenManager.getToken(SECOND);
        String credentialHash = registry.credentialHash(registry.resolve(SECOND));
        String cacheKey = ProfileCacheKeyHelper.generate(SECOND, credentialHash, null);

        Object result = preloadHandler.reload(cacheName, cacheKey);

        assertTrue(result instanceof TokenWithExpiry);
        TokenWithExpiry reloaded = (TokenWithExpiry) result;
        assertNotNull(reloaded.getToken());
        assertEquals(SECOND, reloaded.getProfile());
        assertEquals(credentialHash, reloaded.getCredentialHash());
    }

    @Test
    @DisplayName("命名 Profile 缓存 key 为三段结构且不含 clientId 明文")
    void testNamedProfileCacheKeyShape() {
        String key = ProfileCacheKeyHelper.generate(SECOND, registry.credentialHash(registry.resolve(SECOND)), null);
        assertTrue(key.matches("second:[0-9a-f]{32}:default"));
        assertFalse(key.contains(registry.resolve(SECOND).getClientId()), "缓存 key 不得包含 clientId 明文");
    }

    @Test
    @DisplayName("默认链路读到旧版本无元数据缓存值时刷新，并补写 Profile 与凭据身份")
    void testLegacyDefaultCacheValueIsRefreshedAndBackfilled() {
        // 本 Context 使用默认空安全上下文，默认链路的 key 即旧算法 key
        String legacyToken = "legacy-token-without-profile-metadata";
        String defaultKey = CacheKeyHelper.generate(null);
        l2Cache.put(cacheName, defaultKey, new TokenWithExpiry(legacyToken, farFuture(), null), 3600);

        String token = profileTokenManager.getToken();

        assertNotNull(token);
        assertFalse(token.isEmpty());
        assertFalse(token.equals(legacyToken), "旧格式缓存值应按未命中处理并重新换取 Token");
        TokenWithExpiry backfilled = l2Cache.get(cacheName, defaultKey, TokenWithExpiry.class);
        assertNotNull(backfilled, "刷新后应把新值写回 L2");
        assertEquals(SimpleAkskClientCoreConstant.DEFAULT_PROFILE, backfilled.getProfile());
        assertEquals(registry.credentialHash(registry.resolve(SimpleAkskClientCoreConstant.DEFAULT_PROFILE)),
                backfilled.getCredentialHash());
    }

    @Test
    @DisplayName("命名 Profile 读到无元数据缓存值时用自己的凭据刷新，不误用默认 AKP")
    void testLegacyNamedProfileCacheValueIsRefreshed() {
        String legacyToken = "legacy-token-without-profile-metadata";
        String secondCredentialHash = registry.credentialHash(registry.resolve(SECOND));
        String secondKey = ProfileCacheKeyHelper.generate(SECOND, secondCredentialHash, null);
        l2Cache.put(cacheName, secondKey, new TokenWithExpiry(legacyToken, farFuture(), null), 3600);

        String secondToken = profileTokenManager.getToken(SECOND);
        String defaultToken = profileTokenManager.getToken();

        assertFalse(secondToken.isEmpty());
        assertFalse(secondToken.equals(legacyToken), "命名 Profile 不得使用缺元数据的缓存值");
        assertFalse(secondToken.equals(defaultToken), "命名 Profile 应使用自己的 AKP，而不是默认凭据");
        TokenWithExpiry backfilled = l2Cache.get(cacheName, secondKey, TokenWithExpiry.class);
        assertNotNull(backfilled);
        assertEquals(SECOND, backfilled.getProfile());
    }
}

/**
 * 只有命名 Profile、没有默认凭据时的启动与调用行为
 *
 * <p>独立 Spring Context：把顶层 client-id / client-secret 置空，只保留 {@code profiles.second}。
 * 验证上下文可正常启动、命名 Profile 可用、旧 {@code getToken()} 抛出配置异常。
 *
 * @author surezzzzzz
 */
@Slf4j
@ActiveProfiles("local")
@SpringBootTest(classes = SimpleAkskRedisTokenManagerTestApplication.class, properties = {
        "io.github.surezzzzzz.sdk.auth.aksk.client.client-id=",
        "io.github.surezzzzzz.sdk.auth.aksk.client.client-secret="
})
class RedisTokenManagerProfileOnlyConfigTest {

    @Autowired
    private ProfileTokenManager profileTokenManager;
    @Autowired
    private AkskClientProfileRegistry registry;

    @Test
    @DisplayName("只有命名 Profile 时上下文正常启动，命名 Profile 可用")
    void testNamedProfileWorksWithoutDefaultCredentials() {
        assertFalse(registry.contains(SimpleAkskClientCoreConstant.DEFAULT_PROFILE));
        String token = profileTokenManager.getToken("second");
        assertNotNull(token);
        assertFalse(token.isEmpty());
    }

    @Test
    @DisplayName("未配置默认凭据时调用旧 getToken() 抛出配置异常")
    void testLegacyGetTokenFailsWithoutDefaultCredentials() {
        assertThrows(ConfigurationException.class, () -> profileTokenManager.getToken());
    }
}
