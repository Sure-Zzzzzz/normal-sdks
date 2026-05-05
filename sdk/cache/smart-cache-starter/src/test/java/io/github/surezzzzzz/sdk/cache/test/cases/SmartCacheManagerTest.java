package io.github.surezzzzzz.sdk.cache.test.cases;

import io.github.surezzzzzz.sdk.cache.layer.L1Cache;
import io.github.surezzzzzz.sdk.cache.manager.SmartCacheManager;
import io.github.surezzzzzz.sdk.cache.stats.CacheStats;
import io.github.surezzzzzz.sdk.cache.test.BaseSmartCacheTest;
import io.github.surezzzzzz.sdk.cache.test.SmartCacheTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Smart Cache Manager Test
 *
 * @author Sure
 * @since 1.0.0
 */
@Slf4j
@SpringBootTest(classes = SmartCacheTestApplication.class)
public class SmartCacheManagerTest extends BaseSmartCacheTest {

    @Autowired
    private SmartCacheManager cacheManager;

    @Autowired
    private L1Cache l1Cache;

    @Autowired(required = false)
    private io.github.surezzzzzz.sdk.cache.stats.CacheStatsCollector statsCollector;

    @BeforeEach
    public void setUp() {
        log.info("初始化测试环境...");
        // 清理测试缓存
        cacheManager.clear("testCache");
        // 重置统计信息
        if (statsCollector != null) {
            statsCollector.resetStats("testCache");
        }
        log.info("测试环境初始化完成，Redis可用: {}", isRedisAvailable());
    }

    @Test
    public void testPutAndGetShouldReturnCorrectValue() {
        log.info("========== 测试：put 和 get 应该返回正确的值 ==========");

        // Given
        String cacheName = "testCache";
        String key = "testKey";
        String value = "testValue";
        log.info("缓存名称: {}, Key: {}, Value: {}", cacheName, key, value);

        // When
        cacheManager.put(cacheName, key, value);
        log.info("已写入缓存");
        String result = cacheManager.get(cacheName, key);
        log.info("从缓存读取结果: {}", result);

        // Then - 无论Redis是否可用，都应该能获取到值
        assertNotNull(result);
        assertEquals(value, result);
        log.info("验证通过：缓存值正确");

        // 验证L1有数据
        assertNotNull(l1Cache.get(cacheName, key), "L1缓存应该有数据");

        // 条件验证L2（只在Redis可用时）
        assertL2HasValue(cacheName, key, value);

        log.info("测试通过");
    }

    @Test
    public void testGetWithLoaderShouldLoadAndCacheValue() {
        log.info("========== 测试：get with loader 应该加载并缓存值 ==========");

        // Given
        String cacheName = "testCache";
        String key = "loaderKey";
        String value = "loaderValue";
        log.info("缓存名称: {}, Key: {}, Value: {}", cacheName, key, value);

        // When
        String result = cacheManager.get(cacheName, key, () -> {
            log.info("Loader 被调用，返回值: {}", value);
            return value;
        });
        log.info("第一次获取结果: {}", result);

        // Then
        assertNotNull(result);
        assertEquals(value, result);
        log.info("验证通过：Loader 返回正确的值");

        // When - 第二次获取应该从缓存读取
        String cachedResult = cacheManager.get(cacheName, key);
        log.info("第二次获取结果（应该从缓存）: {}", cachedResult);

        // Then
        assertNotNull(cachedResult);
        assertEquals(value, cachedResult);

        // 验证L1有数据
        assertNotNull(l1Cache.get(cacheName, key), "L1缓存应该有数据");

        // 条件验证L2
        assertL2HasValue(cacheName, key, value);

        log.info("验证通过：第二次从缓存获取成功");
        log.info("测试通过");
    }

    @Test
    public void testEvictShouldRemoveValue() {
        log.info("========== 测试：evict 应该删除缓存值 ==========");

        // Given
        String cacheName = "testCache";
        String key = "evictKey";
        String value = "evictValue";
        cacheManager.put(cacheName, key, value);
        log.info("已写入缓存 - Key: {}, Value: {}", key, value);

        // When
        cacheManager.evict(cacheName, key);
        log.info("已删除缓存 - Key: {}", key);
        String result = cacheManager.get(cacheName, key);
        log.info("删除后获取结果: {}", result);

        // Then - 无论Redis是否可用，都应该删除成功
        assertNull(result);
        assertNull(l1Cache.get(cacheName, key), "L1缓存应该已被删除");
        log.info("验证通过：缓存已被删除");

        // 条件验证L2
        assertL2IsNull(cacheName, key);

        log.info("测试通过");
    }

    @Test
    public void testClearShouldRemoveAllValues() {
        log.info("========== 测试：clear 应该清空所有缓存值 ==========");

        // Given
        String cacheName = "testCache";
        cacheManager.put(cacheName, "key1", "value1");
        cacheManager.put(cacheName, "key2", "value2");
        cacheManager.put(cacheName, "key3", "value3");
        log.info("已写入 3 个缓存值");

        // When
        cacheManager.clear(cacheName);
        log.info("已清空缓存");

        // Then
        assertNull(cacheManager.get(cacheName, "key1"));
        assertNull(cacheManager.get(cacheName, "key2"));
        assertNull(cacheManager.get(cacheName, "key3"));
        log.info("验证通过：所有缓存值已被清空");
        log.info("测试通过");
    }

    @Test
    public void testGetAllShouldReturnMultipleValues() {
        log.info("========== 测试：getAll 应该返回多个缓存值 ==========");

        // Given
        String cacheName = "testCache";
        cacheManager.put(cacheName, "key1", "value1");
        cacheManager.put(cacheName, "key2", "value2");
        cacheManager.put(cacheName, "key3", "value3");
        List<String> keys = Arrays.asList("key1", "key2", "key3");
        log.info("已写入 3 个缓存值，准备批量获取");

        // When
        Map<String, String> results = cacheManager.getAll(cacheName, keys);
        log.info("批量获取结果: {}", results);

        // Then
        assertNotNull(results);
        assertEquals(3, results.size());
        assertEquals("value1", results.get("key1"));
        assertEquals("value2", results.get("key2"));
        assertEquals("value3", results.get("key3"));
        log.info("验证通过：批量获取成功");
        log.info("测试通过");
    }

    @Test
    public void testPutAllShouldWriteMultipleValues() {
        log.info("========== 测试：putAll 应该写入多个缓存值 ==========");

        // Given
        String cacheName = "testCache";
        Map<String, Object> entries = new HashMap<String, Object>();
        entries.put("key1", "value1");
        entries.put("key2", "value2");
        entries.put("key3", "value3");
        log.info("准备批量写入 3 个缓存值");

        // When
        cacheManager.putAll(cacheName, entries);
        log.info("批量写入完成");

        // Then
        assertEquals("value1", cacheManager.get(cacheName, "key1"));
        assertEquals("value2", cacheManager.get(cacheName, "key2"));
        assertEquals("value3", cacheManager.get(cacheName, "key3"));
        log.info("验证通过：批量写入成功");
        log.info("测试通过");
    }

    @Test
    public void testGetStatsShouldReturnCacheStatistics() {
        log.info("========== 测试：getStats 应该返回缓存统计信息 ==========");

        // Given
        String cacheName = "testCache";
        cacheManager.put(cacheName, "key1", "value1");
        String result = cacheManager.get(cacheName, "key1");
        log.info("已写入并读取缓存，结果: {}", result);

        // When
        CacheStats stats = cacheManager.getStats(cacheName);
        log.info("获取统计信息: {}", stats);

        // Then
        assertNotNull(stats, "统计功能已启用，stats 不应为 null");
        assertEquals(1, stats.getTotalRequests(), "总请求数应该是1");
        assertEquals(1, stats.getL1HitCount(), "L1命中次数应该是1");
        assertEquals(0, stats.getL2HitCount(), "L2命中次数应该是0");
        assertEquals(0, stats.getMissCount(), "未命中次数应该是0");
        assertEquals(100.0, stats.getHitRate(), 0.01, "命中率应该是100%");
        assertTrue(stats.getCacheSize() >= 1, "缓存大小应该至少是1");
        log.info("验证通过：统计信息精确匹配");
        log.info("测试通过");
    }

    @Test
    public void testSizeShouldReturnCacheSize() {
        log.info("========== 测试：size 应该返回缓存大小 ==========");

        // Given
        String cacheName = "testCache";
        cacheManager.put(cacheName, "key1", "value1");
        cacheManager.put(cacheName, "key2", "value2");
        log.info("已写入 2 个缓存值");

        // When
        long size = cacheManager.size(cacheName);
        log.info("缓存大小: {}", size);

        // Then
        // size() 返回 L1 + L2 的总大小
        // 如果Redis可用：L1有2个 + L2有2个 = 4
        // 如果Redis不可用：只有L1的2个 = 2
        if (isRedisAvailable()) {
            assertEquals(4, size, "缓存大小应该是4（L1有2个key + L2有2个key）");
        } else {
            assertEquals(2, size, "缓存大小应该是2（只有L1的2个key）");
        }
        log.info("验证通过：缓存大小正确");
        log.info("测试通过");
    }
}
