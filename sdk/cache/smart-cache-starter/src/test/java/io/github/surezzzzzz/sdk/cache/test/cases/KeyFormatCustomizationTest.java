package io.github.surezzzzzz.sdk.cache.test.cases;

import io.github.surezzzzzz.sdk.cache.layer.L2Cache;
import io.github.surezzzzzz.sdk.cache.configuration.SmartCacheProperties;
import io.github.surezzzzzz.sdk.cache.manager.SmartCacheManager;
import io.github.surezzzzzz.sdk.cache.support.KeyHelper;
import io.github.surezzzzzz.sdk.cache.test.BaseSmartCacheTest;
import io.github.surezzzzzz.sdk.cache.test.SmartCacheTestApplication;
import io.github.surezzzzzz.sdk.redis.route.template.RedisRouteTemplate;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Key Format Customization Test
 * <p>
 * 测试 Redis Key 格式自定义功能（v1.0.2 新增）
 * </p>
 *
 * @author surezzzzzz
 * @since 1.0.2
 */
@Slf4j
@SpringBootTest(classes = SmartCacheTestApplication.class)
class KeyFormatCustomizationTest extends BaseSmartCacheTest {

    @Autowired
    private SmartCacheManager cacheManager;

    @Autowired
    private SmartCacheProperties properties;

    @Autowired
    private RedisRouteTemplate redisRouteTemplate;

    private static final String TEST_CACHE_NAME = "keyFormatTest";

    @BeforeEach
    void setUp() {
        requireRedisAvailable();
    }

    @AfterEach
    void tearDown() {
        if (cacheManager != null) {
            cacheManager.clear(TEST_CACHE_NAME);
        }
    }

    /**
     * 测试默认 key 格式：{keyPrefix}:{cacheName}:{me}::{key}
     */
    @Test
    void testDefaultKeyFormat() {
        log.info("========== 测试默认 key 格式 ==========");

        String testKey = "user123";
        String testValue = "John Doe";

        // 写入缓存
        cacheManager.put(TEST_CACHE_NAME, testKey, testValue);

        // 验证默认格式：sure-cache:keyFormatTest:test-instance::{sure-cache:keyFormatTest:test-instance}user123
        String expectedKeyFormat = "{keyPrefix}:{cacheName}:{me}::{key}";
        String actualKeyFormat = properties.getL2().getKeyFormat();
        assertEquals(expectedKeyFormat, actualKeyFormat, "默认 keyFormat 应该是 {keyPrefix}:{cacheName}:{me}::{key}");

        String expectedRedisKey = KeyHelper.buildCacheKey(
                expectedKeyFormat,
                properties.getKeyPrefix(),
                TEST_CACHE_NAME,
                properties.getMe(),
                testKey
        );

        log.info("预期 Redis key: {}", expectedRedisKey);

        // 验证路由后的 Redis 中存在序列化 payload
        String payload = redisRouteTemplate.execute(expectedRedisKey,
                template -> template.opsForValue().get(expectedRedisKey));
        assertNotNull(payload, "路由后的 Redis 中应该有序列化 payload");
        assertEquals(testValue, cacheManager.get(TEST_CACHE_NAME, testKey), "应通过缓存管理器读取原始值");

        log.info("✓ 默认 key 格式测试通过");
    }

    /**
     * 测试自定义 key 格式（AKSK 老格式）：{keyPrefix}:{me}:{cacheName}::{key}
     */
    @Test
    void testCustomKeyFormatAkskStyle() {
        log.info("========== 测试自定义 key 格式（AKSK 风格） ==========");

        // 临时修改 keyFormat 为 AKSK 老格式
        String originalFormat = properties.getL2().getKeyFormat();
        String akskFormat = "{keyPrefix}:{me}:{cacheName}::{key}";
        properties.getL2().setKeyFormat(akskFormat);

        try {
            String testKey = "token456";
            String testValue = "access-token-xyz";

            // 写入缓存
            cacheManager.put(TEST_CACHE_NAME, testKey, testValue);

            // 验证 AKSK 格式：sure-cache:test-instance:keyFormatTest::{sure-cache:keyFormatTest:test-instance}token456
            String expectedRedisKey = KeyHelper.buildCacheKey(
                    akskFormat,
                    properties.getKeyPrefix(),
                    TEST_CACHE_NAME,
                    properties.getMe(),
                    testKey
            );

            log.info("预期 Redis key (AKSK 格式): {}", expectedRedisKey);

            // 验证路由后的 Redis 中存在序列化 payload
            String payload = redisRouteTemplate.execute(expectedRedisKey,
                    template -> template.opsForValue().get(expectedRedisKey));
            assertNotNull(payload, "路由后的 Redis 中应该有序列化 payload");

            // 验证能正常读取
            String cachedValue = cacheManager.get(TEST_CACHE_NAME, testKey);
            assertEquals(testValue, cachedValue, "应该能正常读取缓存值");

            log.info("✓ AKSK 风格 key 格式测试通过");
        } finally {
            // 恢复原格式
            properties.getL2().setKeyFormat(originalFormat);
        }
    }

    /**
     * 测试 KeyHelper.buildCacheKey() 方法
     */
    @Test
    void testKeyHelperBuildCacheKey() {
        log.info("========== 测试 KeyHelper.buildCacheKey() ==========");

        String keyPrefix = "test-prefix";
        String cacheName = "testCache";
        String me = "instance1";
        String key = "key123";

        // 测试默认格式
        String defaultFormat = "{keyPrefix}:{cacheName}:{me}::{key}";
        String result1 = KeyHelper.buildCacheKey(defaultFormat, keyPrefix, cacheName, me, key);
        assertEquals("test-prefix:testCache:instance1::{test-prefix:testCache:instance1}key123", result1);
        log.info("默认格式: {}", result1);

        // 测试 AKSK 格式
        String akskFormat = "{keyPrefix}:{me}:{cacheName}::{key}";
        String result2 = KeyHelper.buildCacheKey(akskFormat, keyPrefix, cacheName, me, key);
        assertEquals("test-prefix:instance1:testCache::{test-prefix:testCache:instance1}key123", result2);
        log.info("AKSK 格式: {}", result2);

        // 测试自定义前缀
        String customFormat = "custom:{cacheName}:{me}::{key}";
        String result3 = KeyHelper.buildCacheKey(customFormat, keyPrefix, cacheName, me, key);
        assertEquals("custom:testCache:instance1::{test-prefix:testCache:instance1}key123", result3);
        log.info("自定义前缀: {}", result3);

        String deprecatedResult = KeyHelper.buildCacheKey(keyPrefix, cacheName, me, key);
        assertEquals(result1, deprecatedResult, "废弃默认格式重载也必须复用缓存命名空间 hash tag");
        log.info("废弃默认格式: {}", deprecatedResult);

        String anotherGroupResult = KeyHelper.buildCacheKey(defaultFormat, keyPrefix, cacheName, "instance2", key);
        assertNotEquals(result1, anotherGroupResult, "不同 me 的 L2 key 必须隔离");
        assertNotEquals(extractHashTag(result1), extractHashTag(anotherGroupResult), "不同 me 的 L2 key 不得共享 Cluster slot");
        assertEquals("test-prefix:preload-lock:testCache:instance1:key123",
                KeyHelper.buildPreloadLockKey(keyPrefix, cacheName, me, key), "预刷新锁必须包含 me");
        assertEquals("test-prefix:testCache:instance1:warmup-complete",
                KeyHelper.buildWarmUpMetadataKey(keyPrefix, cacheName, me, "warmup-complete"), "预热元数据必须包含 me");
        assertNotEquals(
                KeyHelper.buildPreloadLockKey(keyPrefix, cacheName, me, key),
                KeyHelper.buildPreloadLockKey(keyPrefix, cacheName, "instance2", key),
                "不同 me 的预刷新锁必须隔离"
        );
        assertNotEquals(
                KeyHelper.buildWarmUpMetadataKey(keyPrefix, cacheName, me, "warmup-complete"),
                KeyHelper.buildWarmUpMetadataKey(keyPrefix, cacheName, "instance2", "warmup-complete"),
                "不同 me 的预热元数据必须隔离"
        );

        log.info("✓ KeyHelper.buildCacheKey() 测试通过");
    }

    /**
     * 测试 KeyHelper.buildCacheKeyPattern() 方法
     */
    @Test
    void testKeyHelperBuildCacheKeyPattern() {
        log.info("========== 测试 KeyHelper.buildCacheKeyPattern() ==========");

        String keyPrefix = "test-prefix";
        String cacheName = "testCache";
        String me = "instance1";

        // 测试默认格式
        String defaultFormat = "{keyPrefix}:{cacheName}:{me}::{key}";
        String pattern1 = KeyHelper.buildCacheKeyPattern(defaultFormat, keyPrefix, cacheName, me);
        assertEquals("test-prefix:testCache:instance1::*", pattern1);
        log.info("默认格式 pattern: {}", pattern1);

        // 测试 AKSK 格式
        String akskFormat = "{keyPrefix}:{me}:{cacheName}::{key}";
        String pattern2 = KeyHelper.buildCacheKeyPattern(akskFormat, keyPrefix, cacheName, me);
        assertEquals("test-prefix:instance1:testCache::*", pattern2);
        log.info("AKSK 格式 pattern: {}", pattern2);

        log.info("✓ KeyHelper.buildCacheKeyPattern() 测试通过");
    }

    /**
     * 测试 clear() 方法在自定义格式下的正确性
     */
    @Test
    void testClearWithCustomKeyFormat() {
        log.info("========== 测试 clear() 在自定义格式下的正确性 ==========");

        // 临时修改 keyFormat
        String originalFormat = properties.getL2().getKeyFormat();
        String customFormat = "{keyPrefix}:{me}:{cacheName}::{key}";
        properties.getL2().setKeyFormat(customFormat);

        try {
            // 写入多个 key
            cacheManager.put(TEST_CACHE_NAME, "key1", "value1");
            cacheManager.put(TEST_CACHE_NAME, "key2", "value2");
            cacheManager.put(TEST_CACHE_NAME, "key3", "value3");

            // 验证写入成功
            assertNotNull(cacheManager.get(TEST_CACHE_NAME, "key1"));
            assertNotNull(cacheManager.get(TEST_CACHE_NAME, "key2"));
            assertNotNull(cacheManager.get(TEST_CACHE_NAME, "key3"));

            // 清空缓存
            cacheManager.clear(TEST_CACHE_NAME);

            // 验证清空成功
            assertNull(cacheManager.get(TEST_CACHE_NAME, "key1"));
            assertNull(cacheManager.get(TEST_CACHE_NAME, "key2"));
            assertNull(cacheManager.get(TEST_CACHE_NAME, "key3"));

            log.info("✓ clear() 在自定义格式下测试通过");
        } finally {
            properties.getL2().setKeyFormat(originalFormat);
        }
    }

    /**
     * 测试 size() 方法在自定义格式下的正确性
     */
    @Test
    void testSizeWithCustomKeyFormat() {
        log.info("========== 测试 size() 在自定义格式下的正确性 ==========");

        // 临时修改 keyFormat
        String originalFormat = properties.getL2().getKeyFormat();
        String customFormat = "{keyPrefix}:{me}:{cacheName}::{key}";
        properties.getL2().setKeyFormat(customFormat);

        try {
            // 写入多个 key
            cacheManager.put(TEST_CACHE_NAME, "key1", "value1");
            cacheManager.put(TEST_CACHE_NAME, "key2", "value2");
            cacheManager.put(TEST_CACHE_NAME, "key3", "value3");

            // 验证 size
            long size = l2Cache.size(TEST_CACHE_NAME);
            assertEquals(3, size, "size() 应该返回正确的数量");

            log.info("✓ size() 在自定义格式下测试通过");
        } finally {
            properties.getL2().setKeyFormat(originalFormat);
        }
    }

    /**
     * 测试 hash tag 确保 Redis Cluster 兼容性
     */
    @Test
    void testHashTagForRedisCluster() {
        log.info("========== 测试 hash tag 确保 Redis Cluster 兼容性 ==========");

        String keyPrefix = "test";
        String cacheName = "cache";
        String me = "instance";
        String key = "mykey";

        String format = "{keyPrefix}:{cacheName}:{me}::{key}";
        String result = KeyHelper.buildCacheKey(format, keyPrefix, cacheName, me, key);

        String secondResult = KeyHelper.buildCacheKey(format, keyPrefix, cacheName, me, "another-key");

        // 同一缓存命名空间必须共享 hash tag，确保 Redis Cluster 批量操作落到同一 slot
        assertTrue(result.contains("{test:cache:instance}mykey"), "key 应包含缓存命名空间 hash tag");
        assertTrue(secondResult.contains("{test:cache:instance}another-key"), "同缓存的另一 key 应复用命名空间 hash tag");
        assertEquals(extractHashTag(result), extractHashTag(secondResult), "同一缓存命名空间的 key 必须落在同一 Redis Cluster slot");
        log.info("生成的同 slot key：{}，{}", result, secondResult);

        log.info("✓ hash tag 测试通过");
    }

    private String extractHashTag(String redisKey) {
        int start = redisKey.indexOf('{');
        int end = redisKey.indexOf('}', start);
        return redisKey.substring(start + 1, end);
    }
}
