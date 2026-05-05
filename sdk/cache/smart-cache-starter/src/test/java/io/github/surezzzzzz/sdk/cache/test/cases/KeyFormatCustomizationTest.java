package io.github.surezzzzzz.sdk.cache.test.cases;

import io.github.surezzzzzz.sdk.cache.layer.L2Cache;
import io.github.surezzzzzz.sdk.cache.configuration.SmartCacheProperties;
import io.github.surezzzzzz.sdk.cache.manager.SmartCacheManager;
import io.github.surezzzzzz.sdk.cache.support.KeyHelper;
import io.github.surezzzzzz.sdk.cache.test.BaseSmartCacheTest;
import io.github.surezzzzzz.sdk.cache.test.SmartCacheTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Set;

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

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    private static final String TEST_CACHE_NAME = "keyFormatTest";

    @AfterEach
    void tearDown() {
        if (cacheManager != null) {
            cacheManager.clear(TEST_CACHE_NAME);
        }
        if (isRedisAvailable() && redisTemplate != null) {
            Set<String> keys = redisTemplate.keys("*" + TEST_CACHE_NAME + "*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        }
    }

    /**
     * 测试默认 key 格式：{keyPrefix}:{cacheName}:{me}::{key}
     */
    @Test
    void testDefaultKeyFormat() {
        if (shouldSkipRedisTest("testDefaultKeyFormat")) {
            return;
        }

        log.info("========== 测试默认 key 格式 ==========");

        String testKey = "user123";
        String testValue = "John Doe";

        // 写入缓存
        cacheManager.put(TEST_CACHE_NAME, testKey, testValue);

        // 验证默认格式：sure-cache:keyFormatTest:test-instance::{user123}
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

        // 验证 Redis 中的 key 格式
        Object redisValue = redisTemplate.opsForValue().get(expectedRedisKey);
        assertNotNull(redisValue, "Redis 中应该有值");
        assertEquals(testValue, redisValue, "Redis 中的值应该正确");

        log.info("✓ 默认 key 格式测试通过");
    }

    /**
     * 测试自定义 key 格式（AKSK 老格式）：{keyPrefix}:{me}:{cacheName}::{key}
     */
    @Test
    void testCustomKeyFormatAkskStyle() {
        if (shouldSkipRedisTest("testCustomKeyFormatAkskStyle")) {
            return;
        }

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

            // 验证 AKSK 格式：sure-cache:test-instance:keyFormatTest::{token456}
            String expectedRedisKey = KeyHelper.buildCacheKey(
                    akskFormat,
                    properties.getKeyPrefix(),
                    TEST_CACHE_NAME,
                    properties.getMe(),
                    testKey
            );

            log.info("预期 Redis key (AKSK 格式): {}", expectedRedisKey);

            // 验证 Redis 中的 key 格式
            Object redisValue = redisTemplate.opsForValue().get(expectedRedisKey);
            assertNotNull(redisValue, "Redis 中应该有值");
            assertEquals(testValue, redisValue, "Redis 中的值应该正确");

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
        assertEquals("test-prefix:testCache:instance1::{key123}", result1);
        log.info("默认格式: {}", result1);

        // 测试 AKSK 格式
        String akskFormat = "{keyPrefix}:{me}:{cacheName}::{key}";
        String result2 = KeyHelper.buildCacheKey(akskFormat, keyPrefix, cacheName, me, key);
        assertEquals("test-prefix:instance1:testCache::{key123}", result2);
        log.info("AKSK 格式: {}", result2);

        // 测试自定义前缀
        String customFormat = "custom:{cacheName}:{me}::{key}";
        String result3 = KeyHelper.buildCacheKey(customFormat, keyPrefix, cacheName, me, key);
        assertEquals("custom:testCache:instance1::{key123}", result3);
        log.info("自定义前缀: {}", result3);

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
        if (shouldSkipRedisTest("testClearWithCustomKeyFormat")) {
            return;
        }

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
        if (shouldSkipRedisTest("testSizeWithCustomKeyFormat")) {
            return;
        }

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

        // 验证 key 包含 hash tag
        assertTrue(result.contains("{mykey}"), "key 应该包含 hash tag {mykey}");
        log.info("生成的 key: {}", result);

        log.info("✓ hash tag 测试通过");
    }
}
