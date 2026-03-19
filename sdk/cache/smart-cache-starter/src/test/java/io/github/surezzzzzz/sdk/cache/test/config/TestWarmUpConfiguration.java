package io.github.surezzzzzz.sdk.cache.test.config;

import io.github.surezzzzzz.sdk.cache.annotation.SmartCacheWarmUp;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Test WarmUp Configuration
 *
 * @author Sure
 * @since 1.0.0
 */
@Slf4j
@TestConfiguration
public class TestWarmUpConfiguration {

    @Bean
    public TestWarmUpService testWarmUpService() {
        return new TestWarmUpService();
    }

    /**
     * 测试预热服务
     */
    @Slf4j
    public static class TestWarmUpService {

        private final AtomicInteger configCallCount = new AtomicInteger(0);
        private final AtomicInteger userCallCount = new AtomicInteger(0);

        @SmartCacheWarmUp(cacheName = "configCache", order = 1)
        public Map<String, Object> loadAllConfigs() {
            int count = configCallCount.incrementAndGet();
            log.info("执行配置预热，调用次数: {}", count);

            Map<String, Object> configs = new HashMap<>();
            configs.put("config:1", "ConfigValue1");
            configs.put("config:2", "ConfigValue2");
            configs.put("config:3", "ConfigValue3");
            return configs;
        }

        @SmartCacheWarmUp(cacheName = "userCache", order = 2)
        public Map<String, Object> loadAllUsers() {
            int count = userCallCount.incrementAndGet();
            log.info("执行用户预热，调用次数: {}", count);

            Map<String, Object> users = new HashMap<>();
            users.put("user:1", "UserValue1");
            users.put("user:2", "UserValue2");
            return users;
        }

        public int getConfigCallCount() {
            return configCallCount.get();
        }

        public int getUserCallCount() {
            return userCallCount.get();
        }

        public void resetCounts() {
            configCallCount.set(0);
            userCallCount.set(0);
        }
    }
}
