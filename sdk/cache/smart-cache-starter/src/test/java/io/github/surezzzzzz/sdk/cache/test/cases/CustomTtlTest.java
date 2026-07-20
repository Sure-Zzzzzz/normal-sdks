package io.github.surezzzzzz.sdk.cache.test.cases;

import io.github.surezzzzzz.sdk.cache.annotation.SmartCachePut;
import io.github.surezzzzzz.sdk.cache.annotation.SmartCacheable;
import io.github.surezzzzzz.sdk.cache.layer.L2Cache;
import io.github.surezzzzzz.sdk.cache.manager.SmartCacheManager;
import io.github.surezzzzzz.sdk.cache.test.BaseSmartCacheTest;
import io.github.surezzzzzz.sdk.cache.test.SmartCacheTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 自定义 TTL 测试
 *
 * <p>验证业务侧通过编程式 API 和注解式 API 覆盖全局 L2 TTL 的能力：
 * <ul>
 *   <li>{@code put(cacheName, key, value, ttlSeconds)} 写入时指定 TTL</li>
 *   <li>{@code get(cacheName, key, loader, ttlSeconds)} loader 结果写 L2 时指定 TTL</li>
 *   <li>{@code ttlSeconds = 0} 时 fallback 到全局配置</li>
 *   <li>{@code @SmartCacheable(l2TtlSeconds)} 注解式透传</li>
 *   <li>{@code @SmartCachePut(l2TtlSeconds)} 注解式透传</li>
 * </ul>
 *
 * @author Sure
 */
@Slf4j
@SpringBootTest(classes = SmartCacheTestApplication.class)
@Import(CustomTtlTest.TestFixturesConfiguration.class)
public class CustomTtlTest extends BaseSmartCacheTest {

    private static final String CACHE_NAME = "ttl-test";

    /**
     * L2 TTL 随机偏移容差（秒），两次独立写入的差值可达 2 × offset
     * 测试环境 ttl-random-offset-ratio = 0.1，全局 TTL = 300s，offset = 30s
     */
    private static final int TTL_JITTER_TOLERANCE = 65;

    @Autowired
    private SmartCacheManager cacheManager;

    @Autowired
    private L2Cache l2Cache;

    @Autowired
    private TtlTestService ttlTestService;

    @BeforeEach
    void setUp() {
        requireRedisAvailable();
        cacheManager.clear(CACHE_NAME);
        cacheManager.clear("ttl-annotation-test");
    }

    @AfterEach
    void tearDown() {
        cacheManager.clear(CACHE_NAME);
        cacheManager.clear("ttl-annotation-test");
    }

    // ==================== 编程式 API ====================

    @Test
    @DisplayName("put(ttlSeconds) 写入后 L2 TTL 应接近指定值（含随机偏移）")
    void testPutWithCustomTtl() {
        String key = "put-ttl-key";
        int ttlSeconds = 60;
        // offset = ttlSeconds * 0.1 = 6, 范围 [54, 66]
        int expectedOffset = (int) (ttlSeconds * 0.1);

        cacheManager.put(CACHE_NAME, key, "value", ttlSeconds);

        long actualTtl = l2Cache.getTtl(CACHE_NAME, key);
        log.info("put(ttlSeconds={}): actualTtl={}", ttlSeconds, actualTtl);

        assertTrue(actualTtl > 0, "TTL 应大于 0");
        assertTrue(actualTtl <= ttlSeconds + expectedOffset,
                "TTL 不应超过 " + (ttlSeconds + expectedOffset) + "s（含偏移）");
        assertTrue(actualTtl >= ttlSeconds - expectedOffset - 1,
                "TTL 不应低于 " + (ttlSeconds - expectedOffset - 1) + "s（含偏移及整数 TTL 取整）");
    }

    @Test
    @DisplayName("put(ttlSeconds=0) 应 fallback 到全局配置")
    void testPutWithZeroTtlFallsBackToGlobal() {
        String keyCustom = "put-zero-ttl-key";
        String keyGlobal = "put-global-ttl-key";

        cacheManager.put(CACHE_NAME, keyCustom, "value", 0);
        cacheManager.put(CACHE_NAME, keyGlobal, "value");

        long ttlCustom = l2Cache.getTtl(CACHE_NAME, keyCustom);
        long ttlGlobal = l2Cache.getTtl(CACHE_NAME, keyGlobal);
        log.info("put(ttlSeconds=0) ttl={}, put() ttl={}", ttlCustom, ttlGlobal);

        assertTrue(ttlCustom > 0, "TTL 应大于 0");
        assertTrue(ttlGlobal > 0, "TTL 应大于 0");
        // 两者都走全局配置，各自独立加随机偏移，差值可达 2 × offset
        assertTrue(Math.abs(ttlCustom - ttlGlobal) <= TTL_JITTER_TOLERANCE,
                "ttlSeconds=0 时应与全局配置一致（含随机偏移），差值: " + Math.abs(ttlCustom - ttlGlobal));
    }

    @Test
    @DisplayName("put(ttlSeconds<0) 应 fallback 到全局配置")
    void testPutWithNegativeTtlFallsBackToGlobal() {
        String keyCustom = "put-negative-ttl-key";
        String keyGlobal = "put-global-ttl-key-2";

        cacheManager.put(CACHE_NAME, keyCustom, "value", -1);
        cacheManager.put(CACHE_NAME, keyGlobal, "value");

        long ttlCustom = l2Cache.getTtl(CACHE_NAME, keyCustom);
        long ttlGlobal = l2Cache.getTtl(CACHE_NAME, keyGlobal);
        log.info("put(ttlSeconds=-1) ttl={}, put() ttl={}", ttlCustom, ttlGlobal);

        assertTrue(ttlCustom > 0, "TTL 应大于 0");
        assertTrue(ttlGlobal > 0, "TTL 应大于 0");
        assertTrue(Math.abs(ttlCustom - ttlGlobal) <= TTL_JITTER_TOLERANCE,
                "ttlSeconds<0 时应与全局配置一致（含随机偏移），差值: " + Math.abs(ttlCustom - ttlGlobal));
    }

    @Test
    @DisplayName("get(loader, ttlSeconds) cache miss 后 L2 TTL 应接近指定值（含随机偏移）")
    void testGetWithLoaderAndCustomTtl() {
        String key = "get-ttl-key";
        int ttlSeconds = 120;
        // offset = ttlSeconds * 0.1 = 12, 范围 [108, 132]
        int expectedOffset = (int) (ttlSeconds * 0.1);

        String result = cacheManager.get(CACHE_NAME, key, () -> "loaded-value", ttlSeconds);
        assertEquals("loaded-value", result);

        long actualTtl = l2Cache.getTtl(CACHE_NAME, key);
        log.info("get(loader, ttlSeconds={}): actualTtl={}", ttlSeconds, actualTtl);

        assertTrue(actualTtl > 0, "TTL 应大于 0");
        assertTrue(actualTtl <= ttlSeconds + expectedOffset,
                "TTL 不应超过 " + (ttlSeconds + expectedOffset) + "s（含偏移）");
        assertTrue(actualTtl >= ttlSeconds - expectedOffset - 1,
                "TTL 不应低于 " + (ttlSeconds - expectedOffset - 1) + "s（含偏移及整数 TTL 取整）");
    }

    @Test
    @DisplayName("get(loader, ttlSeconds) cache hit 时直接返回，不重新写入")
    void testGetWithLoaderAndCustomTtlOnCacheHit() {
        String key = "get-ttl-hit-key";

        // 先写入，TTL=60
        cacheManager.put(CACHE_NAME, key, "cached-value", 60);

        // cache hit，loader 不应被调用
        String result = cacheManager.get(CACHE_NAME, key, () -> {
            fail("cache hit 时不应调用 loader");
            return "should-not-be-called";
        }, 120);

        log.info("get(loader, ttlSeconds=120) on cache hit: result={}", result);
        assertEquals("cached-value", result, "应返回缓存值");
    }

    @Test
    @DisplayName("get(loader, ttlSeconds=0) 应 fallback 到全局配置")
    void testGetWithLoaderAndZeroTtlFallsBackToGlobal() {
        String keyCustom = "get-zero-ttl-key";
        String keyGlobal = "get-global-ttl-key";

        cacheManager.get(CACHE_NAME, keyCustom, () -> "value", 0);
        cacheManager.get(CACHE_NAME, keyGlobal, () -> "value");

        long ttlCustom = l2Cache.getTtl(CACHE_NAME, keyCustom);
        long ttlGlobal = l2Cache.getTtl(CACHE_NAME, keyGlobal);
        log.info("get(loader, ttlSeconds=0) ttl={}, get(loader) ttl={}", ttlCustom, ttlGlobal);

        assertTrue(ttlCustom > 0, "TTL 应大于 0");
        assertTrue(ttlGlobal > 0, "TTL 应大于 0");
        assertTrue(Math.abs(ttlCustom - ttlGlobal) <= TTL_JITTER_TOLERANCE,
                "ttlSeconds=0 时应与全局配置一致（含随机偏移），差值: " + Math.abs(ttlCustom - ttlGlobal));
    }

    @Test
    @DisplayName("get(loader, ttlSeconds<0) 应 fallback 到全局配置")
    void testGetWithLoaderAndNegativeTtlFallsBackToGlobal() {
        String keyCustom = "get-negative-ttl-key";
        String keyGlobal = "get-global-ttl-key-2";

        cacheManager.get(CACHE_NAME, keyCustom, () -> "value", -1);
        cacheManager.get(CACHE_NAME, keyGlobal, () -> "value");

        long ttlCustom = l2Cache.getTtl(CACHE_NAME, keyCustom);
        long ttlGlobal = l2Cache.getTtl(CACHE_NAME, keyGlobal);
        log.info("get(loader, ttlSeconds=-1) ttl={}, get(loader) ttl={}", ttlCustom, ttlGlobal);

        assertTrue(ttlCustom > 0, "TTL 应大于 0");
        assertTrue(ttlGlobal > 0, "TTL 应大于 0");
        assertTrue(Math.abs(ttlCustom - ttlGlobal) <= TTL_JITTER_TOLERANCE,
                "ttlSeconds<0 时应与全局配置一致（含随机偏移），差值: " + Math.abs(ttlCustom - ttlGlobal));
    }

    @Test
    @DisplayName("不同 key 可以有不同 TTL")
    void testDifferentKeysCanHaveDifferentTtl() {
        cacheManager.put(CACHE_NAME, "short-ttl-key", "value", 30);
        cacheManager.put(CACHE_NAME, "long-ttl-key", "value", 3600);

        long shortTtl = l2Cache.getTtl(CACHE_NAME, "short-ttl-key");
        long longTtl = l2Cache.getTtl(CACHE_NAME, "long-ttl-key");
        log.info("short-ttl-key ttl={}, long-ttl-key ttl={}", shortTtl, longTtl);

        // 30s * 0.1 = 3s offset, 范围 [27, 33]
        assertTrue(shortTtl > 0 && shortTtl <= 33, "短 TTL key 应在 33s 内（含偏移）");
        // 3600s * 0.1 = 360s offset, 范围 [3240, 3960]
        assertTrue(longTtl > 0 && longTtl <= 3960, "长 TTL key 应在 3960s 内（含偏移）");
        assertTrue(longTtl > shortTtl, "长 TTL 应大于短 TTL");
    }

    // ==================== 注解式 API ====================

    @Test
    @DisplayName("@SmartCacheable(l2TtlSeconds) cache miss 后 L2 TTL 应接近指定值（含随机偏移）")
    void testCacheableWithCustomTtl() {
        String key = "anno-1";
        ttlTestService.getWithCustomTtl(key);

        long actualTtl = l2Cache.getTtl("ttl-annotation-test", key);
        log.info("@SmartCacheable(l2TtlSeconds=90): actualTtl={}", actualTtl);

        // 90s * 0.1 = 9s offset, 范围 [81, 99]
        assertTrue(actualTtl > 0, "TTL 应大于 0");
        assertTrue(actualTtl <= 99, "TTL 不应超过 99s（含偏移）");
        assertTrue(actualTtl >= 80, "TTL 不应低于 80s（含偏移及整数 TTL 取整）");
    }

    @Test
    @DisplayName("@SmartCachePut(l2TtlSeconds) 写入后 L2 TTL 应接近指定值（含随机偏移）")
    void testCachePutWithCustomTtl() {
        String key = "anno-put-1";
        ttlTestService.putWithCustomTtl(key, "put-value");

        long actualTtl = l2Cache.getTtl("ttl-annotation-test", key);
        log.info("@SmartCachePut(l2TtlSeconds=45): actualTtl={}", actualTtl);

        // 45s * 0.1 = 4s offset, 范围 [41, 49]
        assertTrue(actualTtl > 0, "TTL 应大于 0");
        assertTrue(actualTtl <= 49, "TTL 不应超过 49s（含偏移）");
        assertTrue(actualTtl >= 39, "TTL 不应低于 39s（含偏移及整数 TTL 取整）");
    }

    @Test
    @DisplayName("@SmartCacheable(l2TtlSeconds=0) 应 fallback 到全局配置")
    void testCacheableWithZeroTtlFallsBackToGlobal() {
        String keyZero = "anno-zero-ttl";
        String keyDefault = "anno-default-ttl";

        ttlTestService.getWithZeroTtl(keyZero);
        ttlTestService.getWithDefaultTtl(keyDefault);

        long ttlZero = l2Cache.getTtl("ttl-annotation-test", keyZero);
        long ttlDefault = l2Cache.getTtl("ttl-annotation-test", keyDefault);
        log.info("@SmartCacheable(l2TtlSeconds=0) ttl={}, @SmartCacheable() ttl={}", ttlZero, ttlDefault);

        assertTrue(ttlZero > 0, "TTL 应大于 0");
        assertTrue(ttlDefault > 0, "TTL 应大于 0");
        assertTrue(Math.abs(ttlZero - ttlDefault) <= TTL_JITTER_TOLERANCE,
                "l2TtlSeconds=0 时应与全局配置一致（含随机偏移），差值: " + Math.abs(ttlZero - ttlDefault));
    }

    @Test
    @DisplayName("@SmartCachePut(l2TtlSeconds=0) 应 fallback 到全局配置")
    void testCachePutWithZeroTtlFallsBackToGlobal() {
        String keyZero = "anno-put-zero-ttl";
        String keyDefault = "anno-put-default-ttl";

        ttlTestService.putWithZeroTtl(keyZero, "value");
        ttlTestService.putWithDefaultTtl(keyDefault, "value");

        long ttlZero = l2Cache.getTtl("ttl-annotation-test", keyZero);
        long ttlDefault = l2Cache.getTtl("ttl-annotation-test", keyDefault);
        log.info("@SmartCachePut(l2TtlSeconds=0) ttl={}, @SmartCachePut() ttl={}", ttlZero, ttlDefault);

        assertTrue(ttlZero > 0, "TTL 应大于 0");
        assertTrue(ttlDefault > 0, "TTL 应大于 0");
        assertTrue(Math.abs(ttlZero - ttlDefault) <= TTL_JITTER_TOLERANCE,
                "l2TtlSeconds=0 时应与全局配置一致（含随机偏移），差值: " + Math.abs(ttlZero - ttlDefault));
    }

    @TestConfiguration
    static class TestFixturesConfiguration {

        @Bean
        TtlTestService ttlTestService() {
            return new TtlTestService();
        }
    }

    // ==================== 测试用 Service ====================

    /**
     * 测试用 Service，提供带不同 ttlSeconds 的注解方法
     */
    static class TtlTestService {

        @SmartCacheable(cacheName = "ttl-annotation-test", key = "#key", l2TtlSeconds = 90)
        public String getWithCustomTtl(String key) {
            return "value-for-" + key;
        }

        @SmartCacheable(cacheName = "ttl-annotation-test", key = "#key", l2TtlSeconds = 0)
        public String getWithZeroTtl(String key) {
            return "value-for-" + key;
        }

        @SmartCacheable(cacheName = "ttl-annotation-test", key = "#key")
        public String getWithDefaultTtl(String key) {
            return "value-for-" + key;
        }

        @SmartCachePut(cacheName = "ttl-annotation-test", key = "#key", l2TtlSeconds = 45)
        public String putWithCustomTtl(String key, String value) {
            return value;
        }

        @SmartCachePut(cacheName = "ttl-annotation-test", key = "#key", l2TtlSeconds = 0)
        public String putWithZeroTtl(String key, String value) {
            return value;
        }

        @SmartCachePut(cacheName = "ttl-annotation-test", key = "#key")
        public String putWithDefaultTtl(String key, String value) {
            return value;
        }
    }
}
