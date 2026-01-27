package io.github.surezzzzzz.sdk.auth.aksk.feign.redis.client.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.client.core.manager.TokenManager;
import io.github.surezzzzzz.sdk.auth.aksk.feign.redis.client.interceptor.AkskFeignRequestInterceptor;
import io.github.surezzzzzz.sdk.auth.aksk.feign.redis.client.test.SimpleAkskFeignRedisClientTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Feign Integration Test
 *
 * <p>测试 Spring Boot 自动配置是否正常工作
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@Slf4j
@SpringBootTest(classes = SimpleAkskFeignRedisClientTestApplication.class)
class FeignIntegrationTest {

    @Autowired(required = false)
    private TokenManager tokenManager;

    @Autowired(required = false)
    private AkskFeignRequestInterceptor akskFeignRequestInterceptor;

    @Test
    void testTokenManagerBeanShouldExist() {
        log.info("========== 测试：TokenManager Bean 应该存在 ==========");
        assertNotNull(tokenManager, "TokenManager Bean should exist");
        log.info("测试通过：TokenManager Bean 存在");
    }

    @Test
    void testAkskFeignRequestInterceptorBeanShouldExist() {
        log.info("========== 测试：AkskFeignRequestInterceptor Bean 应该存在 ==========");
        assertNotNull(akskFeignRequestInterceptor, "AkskFeignRequestInterceptor Bean should exist");
        log.info("测试通过：AkskFeignRequestInterceptor Bean 存在");
    }

    @Test
    void testInterceptorCanAccessTokenManager() {
        log.info("========== 测试：拦截器应该能够访问 TokenManager ==========");
        assertNotNull(tokenManager, "TokenManager should be available");
        assertNotNull(akskFeignRequestInterceptor, "AkskFeignRequestInterceptor should be available");
        log.info("TokenManager 和 AkskFeignRequestInterceptor 都可用");

        // 拦截器应该能够访问 TokenManager
        String token = tokenManager.getToken();
        log.info("获取到的 Token: {}", token != null ? token.substring(0, Math.min(20, token.length())) + "..." : "null");
        // Token 可能为 null（如果 Redis 未配置或凭证无效），但不应该抛出异常
        log.info("测试通过：拦截器可以访问 TokenManager");
    }
}
