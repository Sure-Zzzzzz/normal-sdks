package io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.client.core.manager.TokenManager;
import io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.model.TokenWithExpiry;
import io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.test.SimpleAkskRedisTokenManagerTestApplication;
import io.github.surezzzzzz.sdk.cache.manager.SmartCacheManager;
import io.github.surezzzzzz.sdk.redis.route.template.RedisRouteTemplate;
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
// local profile 激活 application-local.yml（凭据）；SB 2.3/2.2 无 spring.config.import，必须显式带上
@ActiveProfiles({"local", "defaultCacheKey"})
class RedisTokenManagerDefaultCacheKeyTest {

    @Autowired
    private TokenManager tokenManager;
    @Autowired
    private SmartCacheManager cacheManager;
    @Autowired
    private RedisRouteTemplate redisRouteTemplate;
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
        // 走 Route 数据源（与缓存同库）删键；Boot 自动装配的 StringRedisTemplate 连默认 db，删不到缓存键
        redisRouteTemplate.execute("sure-auth-aksk-client:", template -> {
            Set<String> keys = template.keys("sure-auth-aksk-client:*");
            if (keys != null && !keys.isEmpty()) {
                template.delete(keys);
            }
            return null;
        });
    }

    @Test
    @DisplayName("无 security_context 时 cacheKey 应为 'default'")
    void testDefaultCacheKeyWhenSecurityContextIsNull() {
        log.info("========== 无 security_context 时 cacheKey 应为 'default' ==========");

        String token = tokenManager.getToken();
        assertNotNull(token, "Token 不应为 null");
        assertTrue(token.length() > 0, "Token 不应为空");

        // 直接读取，验证写入了 "default" key（类型化读取，绕过 trusted-packages 白名单）
        TokenWithExpiry fromL2 = cacheManager.get(cacheName, "default", TokenWithExpiry.class);
        assertNotNull(fromL2, "无 securityContext 时应写入 'default' key");
        assertEquals(token, fromL2.getToken(), "'default' key 存储的 token 应与 getToken() 返回值一致");

        log.info("✓ 无 security_context 时正确使用 'default' cache key");
    }

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
}
