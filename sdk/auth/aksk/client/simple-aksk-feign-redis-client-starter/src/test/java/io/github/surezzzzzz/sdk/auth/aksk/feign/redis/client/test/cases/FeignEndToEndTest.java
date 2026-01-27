package io.github.surezzzzzz.sdk.auth.aksk.feign.redis.client.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.client.core.manager.TokenManager;
import io.github.surezzzzzz.sdk.auth.aksk.feign.redis.client.annotation.AkskClientFeignClient;
import io.github.surezzzzzz.sdk.auth.aksk.feign.redis.client.configuration.AkskFeignConfiguration;
import io.github.surezzzzzz.sdk.auth.aksk.feign.redis.client.test.SimpleAkskFeignRedisClientTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Feign 端到端测试
 *
 * <p>需要启动 simple-aksk-server-starter 服务。
 *
 * <p>测试前准备：
 * <ol>
 *   <li>启动 simple-aksk-server-starter（端口：8080）</li>
 *   <li>启动 Redis（端口：6379）</li>
 *   <li>在 Server 中创建测试客户端：
 *     <ul>
 *       <li>Client ID: AKP1234567890abcdefgh</li>
 *       <li>Client Secret: SK1234567890abcdefghijklmnopqrstuvwxyz1234</li>
 *       <li>Scopes: read, write</li>
 *     </ul>
 *   </li>
 * </ol>
 *
 * <p>如果没有运行 Server，测试将被跳过（@Disabled）。
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@Slf4j
@SpringBootTest(classes = SimpleAkskFeignRedisClientTestApplication.class)
class FeignEndToEndTest {

    @Autowired(required = false)
    private TokenManager tokenManager;

    @Autowired(required = false)
    private TestFeignClient testFeignClient;

    @Autowired(required = false)
    private ExplicitConfigFeignClient explicitConfigFeignClient;

    @Value("${io.github.surezzzzzz.sdk.auth.aksk.client.server-url}")
    private String serverBaseUrl;

    /**
     * 测试用的 FeignClient（使用 @AkskClientFeignClient 注解）
     */
    @AkskClientFeignClient(name = "test-service", url = "${io.github.surezzzzzz.sdk.auth.aksk.client.server-url}")
    interface TestFeignClient {
        @GetMapping("/api/token/statistics")
        String getTokenStatistics();
    }

    /**
     * 测试用的 FeignClient（使用原始 @FeignClient 注解 + 显式配置）
     */
    @FeignClient(
            name = "explicit-config-service",
            url = "${io.github.surezzzzzz.sdk.auth.aksk.client.server-url}",
            configuration = AkskFeignConfiguration.class
    )
    interface ExplicitConfigFeignClient {
        @GetMapping("/api/token/statistics")
        String getTokenStatistics();
    }

    @BeforeEach
    void setUp() {
        // 清除 Token，确保每次测试都重新获取
        tokenManager.clearToken();
    }

    @Test
    void testTokenManagerShouldExist() {
        log.info("========== 测试：TokenManager 应该存在 ==========");
        assertNotNull(tokenManager, "TokenManager should exist");
        log.info("测试通过：TokenManager 存在");
    }

    @Test
    void testFeignClientShouldExist() {
        log.info("========== 测试：TestFeignClient 应该存在 ==========");
        assertNotNull(testFeignClient, "TestFeignClient should exist");
        log.info("测试通过：TestFeignClient 存在");
    }

    @Test
    void testGetTokenShouldSucceed() {
        log.info("========== 测试：获取 Token 应该成功 ==========");

        // When
        String token = tokenManager.getToken();
        log.info("获取到的 Token: {}", token != null ? token.substring(0, Math.min(20, token.length())) + "..." : "null");

        // Then
        assertNotNull(token, "Token should not be null");
        assertFalse(token.isEmpty(), "Token should not be empty");
        assertTrue(token.startsWith("eyJ"), "Token should be a JWT (starts with 'eyJ')");
        log.info("测试通过：Token 获取成功，长度: {}", token.length());
    }

    @Test
    void testFeignClientCallWithTokenShouldSucceed() {
        log.info("========== 测试：使用 Token 调用 FeignClient 应该成功 ==========");

        // Given
        String url = serverBaseUrl + "/api/token/statistics";
        log.info("请求 URL: {}", url);

        // When
        String response = testFeignClient.getTokenStatistics();
        log.info("响应内容: {}", response);

        // Then
        assertNotNull(response, "Response should not be null");
        log.info("测试通过：FeignClient 调用成功");
    }

    @Test
    void testExplicitConfigFeignClientShouldExist() {
        log.info("========== 测试：ExplicitConfigFeignClient 应该存在 ==========");
        assertNotNull(explicitConfigFeignClient, "ExplicitConfigFeignClient should exist");
        log.info("测试通过：ExplicitConfigFeignClient 存在");
    }

    @Test
    void testExplicitConfigFeignClientCallWithTokenShouldSucceed() {
        log.info("========== 测试：使用显式配置的 FeignClient 调用应该成功 ==========");

        // Given
        String url = serverBaseUrl + "/api/token/statistics";
        log.info("请求 URL: {}", url);

        // When
        String response = explicitConfigFeignClient.getTokenStatistics();
        log.info("响应内容: {}", response);

        // Then
        assertNotNull(response, "Response should not be null");
        log.info("测试通过：显式配置的 FeignClient 调用成功");
    }
}
