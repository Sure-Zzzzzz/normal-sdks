package io.github.surezzzzzz.sdk.cache.test.cases;

import io.github.surezzzzzz.sdk.cache.cache.L1Cache;
import io.github.surezzzzzz.sdk.cache.manager.SmartCacheManager;
import io.github.surezzzzzz.sdk.cache.test.BaseSmartCacheTest;
import io.github.surezzzzzz.sdk.cache.test.SmartCacheTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * L1 and L2 Cache Integration Test
 * <p>
 * 测试 L1（Caffeine）和 L2（Redis）缓存的集成功能
 * </p>
 *
 * @author Sure
 * @since 1.0.0
 */
@Slf4j
@SpringBootTest(classes = SmartCacheTestApplication.class)
public class L1L2CacheIntegrationTest extends BaseSmartCacheTest {

    @Autowired
    private SmartCacheManager cacheManager;

    @Autowired(required = false)
    private L1Cache l1Cache;

    @BeforeEach
    public void setUp() {
        log.info("========== 初始化测试环境 ==========");
        cacheManager.clear("testCache");
        log.info("测试环境初始化完成，Redis可用: {}", isRedisAvailable());
    }

    @Test
    public void testL1AndL2ShouldBothEnabled() {
        log.info("========== 测试：L1 和 L2 缓存应该都启用 ==========");

        // Then
        assertNotNull(l1Cache, "L1 缓存应该启用");
        if (isRedisAvailable()) {
            assertNotNull(l2Cache, "L2 缓存应该启用");
            log.info("验证通过：L1 和 L2 缓存都已启用");
        } else {
            log.info("验证通过：L1 缓存已启用，L2 降级");
        }
        log.info("测试通过");
    }

    @Test
    public void testL1MissL2HitShouldBackfillL1() {
        // 只在Redis可用时运行此测试
        if (shouldSkipRedisTest("testL1MissL2HitShouldBackfillL1")) {
            return;
        }

        log.info("========== 测试：L1 未命中，L2 命中，应该回写 L1 ==========");

        // Given
        String cacheName = "testCache";
        String key = "backfillKey";
        String value = "backfillValue";
        log.info("缓存名称: {}, Key: {}, Value: {}", cacheName, key, value);

        // When - 写入缓存（会同时写入 L1 和 L2）
        cacheManager.put(cacheName, key, value);
        log.info("已写入缓存到 L1 和 L2");

        // 清空 L1，保留 L2
        l1Cache.evict(cacheName, key);
        log.info("已清空 L1 缓存");

        // 从缓存管理器获取（应该从 L2 获取并回写 L1）
        String result = cacheManager.get(cacheName, key);
        log.info("从缓存管理器获取结果: {}", result);

        // Then
        assertNotNull(result);
        assertEquals(value, result);
        log.info("验证通过：从 L2 获取成功");

        // 验证 L1 已被回写
        String l1Result = l1Cache.get(cacheName, key);
        log.info("从 L1 获取结果: {}", l1Result);
        assertNotNull(l1Result);
        assertEquals(value, l1Result);
        log.info("验证通过：L1 已被回写");
        log.info("测试通过");
    }

    @Test
    public void testL1AndL2BothMissShouldLoadFromSource() {
        log.info("========== 测试：L1 和 L2 都未命中，应该从数据源加载 ==========");

        // Given
        String cacheName = "testCache";
        String key = "loadKey";
        String value = "loadValue";
        log.info("缓存名称: {}, Key: {}, Value: {}", cacheName, key, value);

        // When - 使用 loader 加载
        String result = cacheManager.get(cacheName, key, () -> {
            log.info("Loader 被调用，从数据源加载");
            return value;
        });
        log.info("获取结果: {}", result);

        // Then
        assertNotNull(result);
        assertEquals(value, result);
        log.info("验证通过：从数据源加载成功");

        // 验证 L1 已缓存
        String l1Result = l1Cache.get(cacheName, key);
        assertNotNull(l1Result);
        assertEquals(value, l1Result);
        log.info("验证通过：L1 已缓存");

        // 条件验证 L2
        assertL2HasValue(cacheName, key, value);
        log.info("测试通过");
    }

    @Test
    public void testEvictShouldClearBothL1AndL2() {
        log.info("========== 测试：evict 应该清空 L1 和 L2 ==========");

        // Given
        String cacheName = "testCache";
        String key = "evictKey";
        String value = "evictValue";
        cacheManager.put(cacheName, key, value);
        log.info("已写入缓存 - Key: {}, Value: {}", key, value);

        // When
        cacheManager.evict(cacheName, key);
        log.info("已删除缓存");

        // Then - 验证 L1 已清空
        String l1Result = l1Cache.get(cacheName, key);
        assertNull(l1Result);
        log.info("验证通过：L1 已清空");

        // 条件验证 L2
        assertL2IsNull(cacheName, key);
        log.info("测试通过");
    }

    @Test
    public void testClearShouldClearBothL1AndL2() {
        log.info("========== 测试：clear 应该清空 L1 和 L2 的所有缓存 ==========");

        // Given
        String cacheName = "testCache";
        cacheManager.put(cacheName, "key1", "value1");
        cacheManager.put(cacheName, "key2", "value2");
        cacheManager.put(cacheName, "key3", "value3");
        log.info("已写入 3 个缓存值");

        // When
        cacheManager.clear(cacheName);
        log.info("已清空缓存");

        // Then - 验证 L1 已清空
        assertNull(l1Cache.get(cacheName, "key1"));
        assertNull(l1Cache.get(cacheName, "key2"));
        assertNull(l1Cache.get(cacheName, "key3"));
        log.info("验证通过：L1 已清空");

        // 条件验证 L2 已清空
        assertL2IsNull(cacheName, "key1");
        assertL2IsNull(cacheName, "key2");
        assertL2IsNull(cacheName, "key3");
        log.info("测试通过");
    }

    @Test
    public void testL2FailureShouldNotAffectL1() {
        log.info("========== 测试：L2 故障不应该影响 L1 的正常工作 ==========");

        // Given
        String cacheName = "testCache";
        String key = "resilientKey";
        String value = "resilientValue";
        log.info("缓存名称: {}, Key: {}, Value: {}", cacheName, key, value);

        // When - 写入缓存
        cacheManager.put(cacheName, key, value);
        log.info("已写入缓存");

        // 清空 L2（模拟 L2 故障）- 只在Redis可用时执行
        if (isRedisAvailable()) {
            l2Cache.evict(cacheName, key);
            log.info("已清空 L2（模拟故障）");
        } else {
            log.info("Redis不可用，跳过L2清空步骤");
        }

        // 从缓存管理器获取（应该从 L1 获取）
        String result = cacheManager.get(cacheName, key);
        log.info("从缓存管理器获取结果: {}", result);

        // Then
        assertNotNull(result);
        assertEquals(value, result);
        log.info("验证通过：L2 故障时 L1 仍然正常工作");
        log.info("测试通过");
    }
}
