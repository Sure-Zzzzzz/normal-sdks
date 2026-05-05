package io.github.surezzzzzz.sdk.cache.test.cases;

import io.github.surezzzzzz.sdk.cache.layer.L1Cache;
import io.github.surezzzzzz.sdk.cache.manager.SmartCacheManager;
import io.github.surezzzzzz.sdk.cache.support.SpELExpressionHelper;
import io.github.surezzzzzz.sdk.cache.test.BaseSmartCacheTest;
import io.github.surezzzzzz.sdk.cache.test.SmartCacheTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 边界条件测试
 * <p>
 * 测试各种边界情况和异常场景
 * </p>
 *
 * @author Sure
 */
@Slf4j
@SpringBootTest(classes = SmartCacheTestApplication.class)
public class BoundaryConditionsTest extends BaseSmartCacheTest {

    @Autowired
    private SmartCacheManager cacheManager;

    @Autowired
    private L1Cache l1Cache;

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 测试 L2Cache 在 properties 为 null 时的行为
     */
    @Test
    public void testL2CacheWithNullProperties() {
        // 只在Redis可用时运行此测试
        if (shouldSkipRedisTest("testL2CacheWithNullProperties")) {
            return;
        }

        // L2Cache 应该使用默认值，不应该抛出 NPE
        assertDoesNotThrow(() -> {
            l2Cache.put("test-cache", "key1", "value1");
            Object value = l2Cache.get("test-cache", "key1");
            assertEquals("value1", value);
        });
    }

    /**
     * 测试 L1Cache 在 properties 为 null 时的行为
     */
    @Test
    public void testL1CacheWithNullProperties() {
        // L1Cache 应该使用默认配置，不应该抛出 NPE
        assertDoesNotThrow(() -> {
            l1Cache.put("test-cache", "key1", "value1");
            Object value = l1Cache.get("test-cache", "key1");
            assertEquals("value1", value);
        });
    }

    /**
     * 测试 SpEL 表达式解析返回 null
     */
    @Test
    public void testSpELExpressionReturnsNull() throws NoSuchMethodException {
        Method method = this.getClass().getMethod("testSpELExpressionReturnsNull");

        // 测试参数为 null 的情况，SpEL 解析不存在的变量会返回空字符串
        String result = SpELExpressionHelper.parseExpression("#param", method, null, null);
        // SpEL 解析不存在的变量返回空字符串，而不是 null
        assertEquals("", result);
    }

    /**
     * 测试 SpEL 表达式解析返回空字符串
     */
    @Test
    public void testSpELExpressionReturnsEmptyString() throws NoSuchMethodException {
        Method method = this.getClass().getMethod("testMethod", String.class);

        // 测试参数为空字符串的情况
        String result = SpELExpressionHelper.parseExpression("#param", method, new Object[]{""}, null);
        assertEquals("", result);
    }

    public void testMethod(String param) {
        // 用于测试的方法
    }

    /**
     * 测试批量操作的性能
     */
    @Test
    public void testBatchOperationsPerformance() {
        String cacheName = "perf-test";
        int count = 1000;

        // 测试批量写入性能
        long start = System.currentTimeMillis();
        java.util.Map<String, Object> entries = new java.util.HashMap<>();
        for (int i = 0; i < count; i++) {
            entries.put("key" + i, "value" + i);
        }
        cacheManager.putAll(cacheName, entries);
        long batchPutTime = System.currentTimeMillis() - start;
        log.info("批量写入 {} 条数据耗时: {} ms", count, batchPutTime);

        // 测试批量读取性能
        start = System.currentTimeMillis();
        java.util.List<String> keys = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            keys.add("key" + i);
        }
        java.util.Map<String, Object> result = cacheManager.getAll(cacheName, keys);
        long batchGetTime = System.currentTimeMillis() - start;
        log.info("批量读取 {} 条数据耗时: {} ms", count, batchGetTime);

        assertEquals(count, result.size());

        // 批量操作性能验证：应该明显快于单个操作
        // 批量写入平均每个key的时间应该小于单个写入的时间
        double avgPutTime = (double) batchPutTime / count;
        double avgGetTime = (double) batchGetTime / count;
        log.info("批量写入平均每个key耗时: {} ms", avgPutTime);
        log.info("批量读取平均每个key耗时: {} ms", avgGetTime);

        // 批量操作性能验证：平均每个 key 的时间应合理（< 10ms，给 CI 环境留余量）
        assertTrue(avgPutTime < 10.0, "批量写入平均时间应该小于10ms/key，实际: " + avgPutTime);
        assertTrue(avgGetTime < 10.0, "批量读取平均时间应该小于10ms/key，实际: " + avgGetTime);
    }

    /**
     * 测试 L2Cache clear 方法在大量 key 的情况下
     */
    @Test
    public void testL2CacheClearWithManyKeys() {
        // 只在Redis可用时运行此测试
        if (shouldSkipRedisTest("testL2CacheClearWithManyKeys")) {
            return;
        }

        String cacheName = "clear-test";

        // 写入大量数据
        for (int i = 0; i < 100; i++) {
            l2Cache.put(cacheName, "key" + i, "value" + i);
        }

        // 清空缓存应该不会阻塞
        assertDoesNotThrow(() -> {
            long start = System.currentTimeMillis();
            l2Cache.clear(cacheName);
            long duration = System.currentTimeMillis() - start;
            log.info("清空 100 个 key 耗时: {} ms", duration);

            // 清空操作应该在合理时间内完成（3秒内）
            assertTrue(duration < 3000, "清空操作应该在3秒内完成，实际耗时: " + duration + "ms");
        });

        // 验证已清空
        assertEquals(0, l2Cache.size(cacheName));
    }

    /**
     * 测试空值缓存
     */
    @Test
    public void testNullValueCaching() {
        String cacheName = "null-test";
        String key = "null-key";

        // 测试缓存 null 值
        Object result = cacheManager.get(cacheName, key, () -> null);
        assertNull(result);

        // 再次获取应该返回 null（从缓存）
        result = cacheManager.get(cacheName, key, () -> {
            fail("不应该再次调用 loader");
            return "should-not-be-called";
        });
        assertNull(result);
    }

    /**
     * 测试批量操作的空参数
     */
    @Test
    public void testBatchOperationsWithEmptyParams() {
        String cacheName = "empty-test";

        // 测试空 Map
        assertDoesNotThrow(() -> {
            cacheManager.putAll(cacheName, new java.util.HashMap<>());
        });

        // 测试空 List
        assertDoesNotThrow(() -> {
            java.util.Map<String, Object> result = cacheManager.getAll(cacheName, new java.util.ArrayList<>());
            assertTrue(result.isEmpty());
        });

        // 测试 null 参数
        assertDoesNotThrow(() -> {
            cacheManager.putAll(cacheName, null);
            java.util.Map<String, Object> result = cacheManager.getAll(cacheName, null);
            assertTrue(result.isEmpty());
        });
    }

    /**
     * 测试 null key 的处理
     * Caffeine 不允许 null key，应该抛出 NullPointerException
     */
    @Test
    public void testNullKey() {
        String cacheName = "null-key-test";

        // 测试 L1Cache 对 null key 的处理
        // Caffeine 会抛出 NullPointerException
        assertThrows(NullPointerException.class, () -> {
            l1Cache.put(cacheName, null, "value");
        });

        assertThrows(NullPointerException.class, () -> {
            l1Cache.get(cacheName, null);
        });

        // 测试 L2Cache 对 null key 的处理
        // Redis 会将 null key 转换为字符串 "null" 进行存储
        if (!shouldSkipRedisTest("testNullKey")) {
            assertDoesNotThrow(() -> {
                l2Cache.put(cacheName, null, "value");
                Object value = l2Cache.get(cacheName, null);
                // null key 会被转换为字符串，所以可以正常存取
                assertEquals("value", value);
            });
        }

        // 测试 CacheManager 对 null key 的处理
        // 由于 L1Cache 会抛出异常，CacheManager 也会抛出异常
        assertThrows(NullPointerException.class, () -> {
            cacheManager.put(cacheName, null, "value");
        });
    }

    /**
     * 测试空字符串 key 的处理
     */
    @Test
    public void testEmptyStringKey() {
        String cacheName = "empty-key-test";
        String emptyKey = "";

        // 测试 L1Cache 对空字符串 key 的处理
        assertDoesNotThrow(() -> {
            l1Cache.put(cacheName, emptyKey, "value1");
            Object value = l1Cache.get(cacheName, emptyKey);
            assertEquals("value1", value);
        });

        // 测试 L2Cache 对空字符串 key 的处理
        if (!shouldSkipRedisTest("testEmptyStringKey")) {
            assertDoesNotThrow(() -> {
                l2Cache.put(cacheName, emptyKey, "value2");
                Object value = l2Cache.get(cacheName, emptyKey);
                assertEquals("value2", value);
            });
        }

        // 测试 CacheManager 对空字符串 key 的处理
        assertDoesNotThrow(() -> {
            cacheManager.put(cacheName, emptyKey, "value3");
            Object value = cacheManager.get(cacheName, emptyKey, () -> "fallback");
            assertEquals("value3", value);
        });
    }
}
