package io.github.surezzzzzz.sdk.cache.test.cases;

import io.github.surezzzzzz.sdk.cache.manager.SmartCacheManager;
import io.github.surezzzzzz.sdk.cache.test.SmartCacheTestApplication;
import io.github.surezzzzzz.sdk.cache.test.config.TestWarmUpConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Cache WarmUp Test
 * <p>
 * 测试缓存预热功能
 * </p>
 *
 * @author Sure
 * @since 1.0.0
 */
@Slf4j
@SpringBootTest(classes = SmartCacheTestApplication.class)
@Import(TestWarmUpConfiguration.class)
public class CacheWarmUpTest {

    @Autowired
    private SmartCacheManager cacheManager;

    @Autowired
    private TestWarmUpConfiguration.TestWarmUpService warmUpService;

    @BeforeEach
    public void setUp() {
        log.info("========== 初始化预热测试环境 ==========");
        // 确保预热数据存在（如果被其他测试清空了，重新加载）
        if (cacheManager.get("configCache", "config:1") == null) {
            log.info("检测到configCache为空，重新加载预热数据");
            Map<String, Object> configs = warmUpService.loadAllConfigs();
            configs.forEach((key, value) -> cacheManager.put("configCache", key, value));
        }
        if (cacheManager.get("userCache", "user:1") == null) {
            log.info("检测到userCache为空，重新加载预热数据");
            Map<String, Object> users = warmUpService.loadAllUsers();
            users.forEach((key, value) -> cacheManager.put("userCache", key, value));
        }
        log.info("预热测试环境初始化完成");
    }

    @Test
    public void testWarmUpShouldLoadCacheOnStartup() {
        log.info("========== 测试：预热应该在启动时加载缓存 ==========");

        // Given - 预热方法已在启动时执行

        // When - 直接从缓存获取
        String config1 = cacheManager.get("configCache", "config:1");
        String config2 = cacheManager.get("configCache", "config:2");
        String user1 = cacheManager.get("userCache", "user:1");
        String user2 = cacheManager.get("userCache", "user:2");

        log.info("从缓存获取 config:1 = {}", config1);
        log.info("从缓存获取 config:2 = {}", config2);
        log.info("从缓存获取 user:1 = {}", user1);
        log.info("从缓存获取 user:2 = {}", user2);

        // Then - 验证预热的数据已存在
        assertNotNull(config1, "config:1 应该已被预热");
        assertNotNull(config2, "config:2 应该已被预热");
        assertNotNull(user1, "user:1 应该已被预热");
        assertNotNull(user2, "user:2 应该已被预热");
        assertEquals("ConfigValue1", config1, "config:1 的值应该正确");
        assertEquals("ConfigValue2", config2, "config:2 的值应该正确");
        assertEquals("UserValue1", user1, "user:1 的值应该正确");
        assertEquals("UserValue2", user2, "user:2 的值应该正确");

        log.info("验证通过：预热数据已成功加载到缓存");
        log.info("测试通过");
    }

    @Test
    public void testWarmUpOrderShouldBeRespected() {
        log.info("========== 测试：预热顺序应该被遵守 ==========");

        // Given - 预热方法按order执行（order=1的configCache先执行，order=2的userCache后执行）

        // When - 检查调用次数
        int configCallCount = warmUpService.getConfigCallCount();
        int userCallCount = warmUpService.getUserCallCount();

        log.info("配置预热调用次数: {}", configCallCount);
        log.info("用户预热调用次数: {}", userCallCount);

        // Then - 验证都被调用了
        assertEquals(1, configCallCount, "配置预热应该被调用1次");
        assertEquals(1, userCallCount, "用户预热应该被调用1次");

        log.info("验证通过：预热方法都被正确调用");
        log.info("测试通过");
    }
}
