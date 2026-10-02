package io.github.surezzzzzz.sdk.cache.test.cases;

import io.github.surezzzzzz.sdk.cache.manager.SmartCacheManager;
import io.github.surezzzzzz.sdk.cache.stats.CacheStats;
import io.github.surezzzzzz.sdk.cache.stats.CacheStatsCollector;
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
 * Batch Operations and Stats Test
 * <p>
 * 测试批量操作和统计功能
 * </p>
 *
 * @author Sure
 * @since 1.0.0
 */
@Slf4j
@SpringBootTest(classes = SmartCacheTestApplication.class)
public class BatchOperationsAndStatsTest {

    @Autowired
    private SmartCacheManager cacheManager;

    @Autowired(required = false)
    private CacheStatsCollector statsCollector;

    @BeforeEach
    public void setUp() {
        log.info("========== 初始化测试环境 ==========");
        cacheManager.clear("testCache");
        if (statsCollector != null) {
            statsCollector.resetStats("testCache");
        }
        log.info("测试环境初始化完成");
    }

    @Test
    public void testPutAllShouldWriteMultipleValues() {
        log.info("========== 测试：putAll 应该批量写入多个值 ==========");

        // Given
        String cacheName = "testCache";
        Map<String, Object> entries = new HashMap<String, Object>();
        entries.put("user:1", "Alice");
        entries.put("user:2", "Bob");
        entries.put("user:3", "Charlie");
        entries.put("user:4", "David");
        entries.put("user:5", "Eve");
        log.info("准备批量写入 5 个缓存值");

        // When
        cacheManager.putAll(cacheName, entries);
        log.info("批量写入完成");

        // Then - 验证所有值都已写入
        assertEquals("Alice", cacheManager.get(cacheName, "user:1"));
        assertEquals("Bob", cacheManager.get(cacheName, "user:2"));
        assertEquals("Charlie", cacheManager.get(cacheName, "user:3"));
        assertEquals("David", cacheManager.get(cacheName, "user:4"));
        assertEquals("Eve", cacheManager.get(cacheName, "user:5"));
        log.info("验证通过：所有值都已写入");
        log.info("测试通过");
    }

    @Test
    public void testGetAllShouldReturnMultipleValues() {
        log.info("========== 测试：getAll 应该批量获取多个值 ==========");

        // Given
        String cacheName = "testCache";
        cacheManager.put(cacheName, "product:1", "Laptop");
        cacheManager.put(cacheName, "product:2", "Mouse");
        cacheManager.put(cacheName, "product:3", "Keyboard");
        cacheManager.put(cacheName, "product:4", "Monitor");
        cacheManager.put(cacheName, "product:5", "Headset");
        log.info("已写入 5 个缓存值");

        List<String> keys = Arrays.asList("product:1", "product:2", "product:3", "product:4", "product:5");
        log.info("准备批量获取 5 个值");

        // When
        Map<String, String> results = cacheManager.getAll(cacheName, keys);
        log.info("批量获取结果: {}", results);

        // Then
        assertNotNull(results);
        assertEquals(5, results.size());
        assertEquals("Laptop", results.get("product:1"));
        assertEquals("Mouse", results.get("product:2"));
        assertEquals("Keyboard", results.get("product:3"));
        assertEquals("Monitor", results.get("product:4"));
        assertEquals("Headset", results.get("product:5"));
        log.info("验证通过：批量获取成功");
        log.info("测试通过");
    }

    @Test
    public void testGetAllWithPartialMissShouldReturnAvailableValues() {
        log.info("========== 测试：getAll 部分未命中应该返回可用的值 ==========");

        // Given
        String cacheName = "testCache";
        cacheManager.put(cacheName, "item:1", "Value1");
        cacheManager.put(cacheName, "item:3", "Value3");
        cacheManager.put(cacheName, "item:5", "Value5");
        log.info("已写入 3 个缓存值（item:1, item:3, item:5）");

        List<String> keys = Arrays.asList("item:1", "item:2", "item:3", "item:4", "item:5");
        log.info("准备批量获取 5 个值（包含 2 个不存在的）");

        // When
        Map<String, String> results = cacheManager.getAll(cacheName, keys);
        log.info("批量获取结果: {}", results);

        // Then
        assertNotNull(results);
        assertEquals(3, results.size()); // 只返回存在的 3 个值
        assertEquals("Value1", results.get("item:1"));
        assertNull(results.get("item:2")); // 不存在的 key 不在结果中
        assertEquals("Value3", results.get("item:3"));
        assertNull(results.get("item:4")); // 不存在的 key 不在结果中
        assertEquals("Value5", results.get("item:5"));
        log.info("验证通过：部分未命中时返回可用的值");
        log.info("测试通过");
    }

    @Test
    public void testStatsShouldTrackCacheOperations() {
        log.info("========== 测试：统计功能应该跟踪缓存操作 ==========");

        if (statsCollector == null) {
            log.warn("统计功能未启用，跳过测试");
            return;
        }

        // Given
        String cacheName = "testCache";
        log.info("缓存名称: {}", cacheName);

        // When - 执行一系列缓存操作
        // 写入 3 个值
        cacheManager.put(cacheName, "stats:1", "Value1");
        cacheManager.put(cacheName, "stats:2", "Value2");
        cacheManager.put(cacheName, "stats:3", "Value3");
        log.info("已写入 3 个缓存值");

        // 命中 2 次（L1命中）
        String v1 = cacheManager.get(cacheName, "stats:1");
        String v2 = cacheManager.get(cacheName, "stats:2");
        log.info("已命中 2 次: {}, {}", v1, v2);

        // 未命中 1 次
        String v3 = cacheManager.get(cacheName, "stats:999");
        log.info("已未命中 1 次: {}", v3);

        // Then - 获取统计信息并验证精确值
        CacheStats stats = cacheManager.getStats(cacheName);
        log.info("统计信息: {}", stats);

        assertNotNull(stats);
        assertEquals(3, stats.getTotalRequests(), "总请求数应该是3（2次命中+1次未命中）");
        assertEquals(2, stats.getL1HitCount(), "L1命中次数应该是2");
        assertEquals(0, stats.getL2HitCount(), "L2命中次数应该是0（因为都在L1命中）");
        assertEquals(1, stats.getMissCount(), "未命中次数应该是1");
        assertEquals(66.67, stats.getHitRate(), 0.01, "命中率应该是66.67%（2/3）");
        assertEquals(66.67, stats.getL1HitRate(), 0.01, "L1命中率应该是66.67%（2/3）");
        assertEquals(0.0, stats.getL2HitRate(), 0.01, "L2命中率应该是0%");
        assertTrue(stats.getCacheSize() >= 3, "缓存大小应该至少是3");

        log.info("总请求数: {}", stats.getTotalRequests());
        log.info("命中率: {}%", stats.getHitRate());
        log.info("L1 命中率: {}%", stats.getL1HitRate());
        log.info("L2 命中率: {}%", stats.getL2HitRate());
        log.info("缓存大小: {}", stats.getCacheSize());
        log.info("验证通过：统计功能正常工作");
        log.info("测试通过");
    }

    @Test
    public void testSizeShouldReturnCorrectCount() {
        log.info("========== 测试：size 应该返回正确的缓存数量 ==========");

        // Given
        String cacheName = "testCache";
        log.info("缓存名称: {}", cacheName);

        // When - 写入 10 个值
        for (int i = 1; i <= 10; i++) {
            cacheManager.put(cacheName, "size:test:" + i, "Value" + i);
        }
        log.info("已写入 10 个缓存值");

        long size = cacheManager.size(cacheName);
        log.info("缓存大小: {}", size);

        // Then
        assertTrue(size >= 10); // L1 + L2 可能有重复计数
        log.info("验证通过：缓存大小正确");
        log.info("测试通过");
    }
}
