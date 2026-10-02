package io.github.surezzzzzz.sdk.cache.test;

import io.github.surezzzzzz.sdk.cache.layer.L2Cache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Smart Cache Test Base Class
 * <p>
 * 提供 Redis 集成环境校验和 L2 断言功能
 * </p>
 *
 * @author Sure
 * @since 1.0.0
 */
@Slf4j
public abstract class BaseSmartCacheTest {

    @Autowired(required = false)
    protected L2Cache l2Cache;

    /**
     * 验证 route-native Redis 集成环境可用
     */
    protected void requireRedisAvailable() {
        String cacheName = "health-check";
        String key = "ping";
        try {
            assertNotNull(l2Cache, "集成测试必须注册 L2Cache");
            l2Cache.put(cacheName, key, "pong");
            String result = l2Cache.get(cacheName, key, String.class);
            assertEquals("pong", result, "Redis route 健康检查应读回写入值");
        } catch (Exception e) {
            throw new AssertionError("集成测试需要可用的 Redis route 环境：" + e.getMessage(), e);
        } finally {
            if (l2Cache != null) {
                try {
                    l2Cache.evict(cacheName, key);
                } catch (Exception e) {
                    log.warn("Redis route 健康检查清理失败", e);
                }
            }
        }
    }

    protected void assertL2HasValue(String cacheName, String key, Object expectedValue) {
        requireRedisAvailable();
        Object actual = l2Cache.get(cacheName, key);
        assertEquals(expectedValue, actual, "L2 缓存应包含预期值");
    }

    protected void assertL2IsNull(String cacheName, String key) {
        requireRedisAvailable();
        Object actual = l2Cache.get(cacheName, key);
        org.junit.jupiter.api.Assertions.assertNull(actual, "L2 缓存应为空");
    }
}
