package io.github.surezzzzzz.sdk.cache.test.cases;

import io.github.surezzzzzz.sdk.cache.exception.SmartCacheException;
import io.github.surezzzzzz.sdk.cache.manager.SmartCacheManager;
import io.github.surezzzzzz.sdk.cache.test.SmartCacheTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Circular Dependency Test
 *
 * @author Sure
 * @since 1.0.0
 */
@Slf4j
@SpringBootTest(classes = SmartCacheTestApplication.class)
public class CircularDependencyTest {

    @Autowired
    private SmartCacheManager cacheManager;

    @BeforeEach
    public void setUp() {
        log.info("初始化测试环境...");
        cacheManager.clear("testCache");
        log.info("测试环境初始化完成");
    }

    @Test
    public void testCircularDependencyShouldThrowException() {
        log.info("========== 测试：循环依赖应该抛出异常 ==========");

        // Given
        String cacheName = "testCache";
        String key = "circularKey";
        log.info("缓存名称: {}, Key: {}", cacheName, key);

        // When & Then
        assertThrows(SmartCacheException.class, () -> {
            cacheManager.get(cacheName, key, () -> {
                log.info("Loader 被调用，尝试触发循环依赖");
                // 在 loader 中调用同一个 key，触发循环依赖
                return cacheManager.get(cacheName, key, () -> "value");
            });
        });
        log.info("验证通过：循环依赖检测成功");
        log.info("测试通过");
    }

    @Test
    public void testNestedCachingWithoutCircularDependencyShouldWork() {
        log.info("========== 测试：嵌套缓存（无循环依赖）应该正常工作 ==========");

        // Given
        String cacheName = "testCache";
        String key1 = "key1";
        String key2 = "key2";
        log.info("缓存名称: {}, Key1: {}, Key2: {}", cacheName, key1, key2);

        // When
        String result = cacheManager.get(cacheName, key1, () -> {
            log.info("Loader1 被调用，加载 key1");
            // 在 loader 中调用不同的 key，不会触发循环依赖
            String nestedValue = cacheManager.get(cacheName, key2, () -> {
                log.info("Loader2 被调用，加载 key2");
                return "nestedValue";
            });
            return "value1-" + nestedValue;
        });
        log.info("获取结果: {}", result);

        // Then
        assertNotNull(result);
        assertEquals("value1-nestedValue", result);
        log.info("验证通过：嵌套缓存正常工作");
        log.info("测试通过");
    }

    @Test
    public void testThreadLocalCleanupAfterException() {
        log.info("========== 测试：异常后 ThreadLocal 应该被清理 ==========");

        // Given
        String cacheName = "testCache";
        String key = "cleanupKey";
        log.info("缓存名称: {}, Key: {}", cacheName, key);

        // When - 触发循环依赖异常
        try {
            cacheManager.get(cacheName, key, () -> {
                return cacheManager.get(cacheName, key, () -> "value");
            });
            fail("应该抛出 SmartCacheException");
        } catch (SmartCacheException e) {
            log.info("捕获到预期的异常: {}", e.getMessage());
        }

        // Then - 再次使用相同的 key 应该正常工作
        String result = cacheManager.get(cacheName, key, () -> {
            log.info("Loader 被调用，返回正常值");
            return "normalValue";
        });
        log.info("异常后再次获取结果: {}", result);

        assertNotNull(result);
        assertEquals("normalValue", result);
        log.info("验证通过：ThreadLocal 已被正确清理");
        log.info("测试通过");
    }
}
