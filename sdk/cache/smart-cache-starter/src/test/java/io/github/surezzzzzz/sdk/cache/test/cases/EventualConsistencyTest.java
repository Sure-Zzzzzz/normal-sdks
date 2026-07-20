package io.github.surezzzzzz.sdk.cache.test.cases;

import io.github.surezzzzzz.sdk.cache.manager.SmartCacheManager;
import io.github.surezzzzzz.sdk.cache.test.BaseSmartCacheTest;
import io.github.surezzzzzz.sdk.cache.test.SmartCacheTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Eventual Consistency Test
 * <p>
 * 最终一致性模式测试
 * </p>
 *
 * @author Sure
 * @since 1.0.0
 */
@Slf4j
@SpringBootTest(
        classes = SmartCacheTestApplication.class,
        properties = {
                "io.github.surezzzzzz.sdk.cache.me=test-instance-eventual",
                "io.github.surezzzzzz.sdk.cache.consistency.mode=eventual",
                "io.github.surezzzzzz.sdk.cache.pubsub.mode=disabled"
        }
)
public class EventualConsistencyTest extends BaseSmartCacheTest {

    @Autowired
    private SmartCacheManager cacheManager;

    @BeforeEach
    public void setUp() {
        log.info("========== 初始化测试环境 ==========");
        cacheManager.clear("testCache");
        log.info("测试环境初始化完成");
    }

    /**
     * 测试场景：最终一致性模式下的基本缓存操作
     */
    @Test
    public void testEventualConsistencyBasicOperations() throws Exception {
        log.info("========== 测试：最终一致性基本操作 ==========");

        // Given - 写入缓存数据
        log.info("【步骤 1】写入缓存数据");
        cacheManager.put("testCache", "key1", "value1");

        // 验证数据已写入
        String value1 = cacheManager.get("testCache", "key1");
        assertEquals("value1", value1);
        log.info("缓存数据已写入: {}", value1);

        // When - 删除缓存
        log.info("【步骤 2】删除缓存");
        cacheManager.evict("testCache", "key1");

        // Then - 验证缓存已失效
        log.info("【步骤 3】验证缓存已失效");
        String value2 = cacheManager.get("testCache", "key1");
        assertNull(value2, "缓存应该已被删除");

        log.info("✓ 最终一致性基本操作测试通过");
    }

    /**
     * 测试场景：最终一致性模式下的缓存清空
     */
    @Test
    public void testEventualConsistencyClear() throws Exception {
        log.info("========== 测试：最终一致性清空 ==========");

        // Given - 写入多个缓存数据
        log.info("【步骤 1】写入多个缓存数据");
        cacheManager.put("testCache", "key1", "value1");
        cacheManager.put("testCache", "key2", "value2");
        cacheManager.put("testCache", "key3", "value3");

        // 验证数据已写入
        assertEquals("value1", cacheManager.get("testCache", "key1"));
        assertEquals("value2", cacheManager.get("testCache", "key2"));
        assertEquals("value3", cacheManager.get("testCache", "key3"));

        // When - 清空缓存
        log.info("【步骤 2】清空缓存");
        cacheManager.clear("testCache");

        // Then - 验证所有缓存已失效
        log.info("【步骤 3】验证所有缓存已失效");
        assertNull(cacheManager.get("testCache", "key1"));
        assertNull(cacheManager.get("testCache", "key2"));
        assertNull(cacheManager.get("testCache", "key3"));

        log.info("✓ 最终一致性清空测试通过");
    }

    /**
     * 测试场景：最终一致性模式下的TTL过期
     */
    @Test
    public void testEventualConsistencyTTLExpiration() throws Exception {
        log.info("========== 测试：最终一致性TTL过期 ==========");

        // Given - 写入缓存数据
        log.info("【步骤 1】写入缓存数据");
        cacheManager.put("testCache", "ttl-key", "ttl-value");

        // 验证数据已写入
        assertEquals("ttl-value", cacheManager.get("testCache", "ttl-key"));
        log.info("缓存数据已写入");

        // When - 等待L1缓存过期 (配置的是60秒)
        log.info("【步骤 2】等待L1缓存过期");
        Thread.sleep(65000); // 等待65秒

        // Then - 验证L1缓存已过期，但L2缓存仍然存在
        log.info("【步骤 3】验证缓存状态");
        String value = cacheManager.get("testCache", "ttl-key");

        assertEquals("ttl-value", value, "最终一致性模式下 L1 过期后应从 L2 重新加载");
        log.info("L2 缓存仍然存在，数据已从 L2 重新加载到 L1");

        log.info("✓ 最终一致性TTL过期测试通过");
    }
}
