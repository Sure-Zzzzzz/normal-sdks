package io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.client.core.configuration.SimpleAkskClientCoreProperties;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.manager.TokenManager;
import io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.configuration.SimpleAkskRedisTokenManagerProperties;
import io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.test.SimpleAkskRedisTokenManagerTestApplication;
import io.github.surezzzzzz.sdk.cache.manager.SmartCacheManager;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RedisTokenManager 空 securityContext 测试
 *
 * <p>独立 Spring Context，不加载测试用 {@code @Primary} SecurityContextProvider。
 * 使用 AutoConfiguration 默认的 {@code DefaultSecurityContextProvider}（返回 null），
 * 验证：无 securityContext 时 cacheKey 为 "default"。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = {
        SimpleAkskRedisTokenManagerTestApplication.class,
        RedisTokenManagerDefaultCacheKeyTest.TestConfig.class
})
@ActiveProfiles("defaultCacheKey")
class RedisTokenManagerDefaultCacheKeyTest {

    /**
     * 显式注册 DefaultSecurityContextProvider（返回 null），
     * 覆盖测试 Application 中的 @Primary Bean，
     * 保证本测试类使用 null securityContext 验证 "default" cacheKey 场景。
     */
    @Configuration
    @Profile("defaultCacheKey")
    static class TestConfig {
        @Bean
        public io.github.surezzzzzz.sdk.auth.aksk.client.core.provider.SecurityContextProvider securityContextProvider() {
            return new io.github.surezzzzzz.sdk.auth.aksk.client.core.provider.DefaultSecurityContextProvider();
        }
    }

    @Autowired
    private TokenManager tokenManager;

    @Autowired
    private SmartCacheManager cacheManager;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Value("${io.github.surezzzzzz.sdk.auth.aksk.client.redis.token.cache-name:aksk-client-token}")
    private String cacheName;

    @BeforeEach
    void setUp() {
        cacheManager.clear(cacheName);
        cleanupTestKeys();
    }

    @AfterEach
    void tearDown() {
        cacheManager.clear(cacheName);
        cleanupTestKeys();
    }

    private void cleanupTestKeys() {
        Set<String> keys = stringRedisTemplate.keys("sure-auth-aksk-client:*");
        if (keys != null && !keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
    }

    @Test
    @DisplayName("无 security_context 时 cacheKey 应为 'default'")
    void testDefaultCacheKeyWhenSecurityContextIsNull() {
        log.info("========== 无 security_context 时 cacheKey 应为 'default' ==========");

        String token = tokenManager.getToken();
        assertNotNull(token, "Token 不应为 null");
        assertTrue(token.length() > 0, "Token 不应为空");

        // 直接从 L2 读取，验证写入了 "default" key
        Object fromL2 = cacheManager.get(cacheName, "default");
        assertNotNull(fromL2, "无 securityContext 时应写入 'default' key");

        log.info("✓ 无 security_context 时正确使用 'default' cache key");
    }
}
