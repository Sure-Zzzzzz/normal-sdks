package io.github.surezzzzzz.sdk.cache.test;

import io.github.surezzzzzz.sdk.cache.cache.L2Cache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Smart Cache Test Base Class
 * <p>
 * 提供Redis可用性检测和自适应断言功能
 * </p>
 *
 * @author Sure
 * @since 1.0.0
 */
@Slf4j
public abstract class BaseSmartCacheTest {

    @Autowired(required = false)
    protected L2Cache l2Cache;

    private Boolean redisAvailable = null;

    /**
     * 检查Redis是否可用
     * 结果会被缓存，避免重复检查
     */
    protected boolean isRedisAvailable() {
        if (redisAvailable == null) {
            redisAvailable = checkRedisHealth();
            if (redisAvailable) {
                log.info("✓ Redis is available, tests will verify L1+L2 behavior");
            } else {
                log.warn("✗ Redis is NOT available, tests will verify L1-only degradation behavior");
            }
        }
        return redisAvailable;
    }

    private boolean checkRedisHealth() {
        if (l2Cache == null) {
            return false;
        }
        try {
            // 尝试一个简单的操作
            l2Cache.put("health-check", "ping", "pong");
            String result = l2Cache.get("health-check", "ping");
            l2Cache.evict("health-check", "ping");
            return "pong".equals(result);
        } catch (Exception e) {
            log.debug("Redis health check failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 条件断言：只在Redis可用时验证L2有值
     */
    protected void assertL2HasValue(String cacheName, String key, Object expectedValue) {
        if (isRedisAvailable()) {
            Object actual = l2Cache.get(cacheName, key);
            assertEquals(expectedValue, actual,
                    "L2 cache should have the value when Redis is available");
        } else {
            log.debug("Skipping L2 assertion because Redis is not available");
        }
    }

    /**
     * 条件断言：只在Redis可用时验证L2为null
     */
    protected void assertL2IsNull(String cacheName, String key) {
        if (isRedisAvailable()) {
            Object actual = l2Cache.get(cacheName, key);
            assertNull(actual,
                    "L2 cache should be null when Redis is available");
        } else {
            log.debug("Skipping L2 null assertion because Redis is not available");
        }
    }

    /**
     * 检查是否应该跳过需要Redis的测试
     * 如果Redis不可用，记录日志并返回true
     */
    protected boolean shouldSkipRedisTest(String testName) {
        if (!isRedisAvailable()) {
            log.info("Skipping test '{}' because Redis is not available", testName);
            return true;
        }
        return false;
    }
}
