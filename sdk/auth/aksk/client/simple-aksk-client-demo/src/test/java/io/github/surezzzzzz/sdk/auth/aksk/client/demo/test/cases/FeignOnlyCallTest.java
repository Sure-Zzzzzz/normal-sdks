package io.github.surezzzzzz.sdk.auth.aksk.client.demo.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.client.core.manager.TokenManager;
import io.github.surezzzzzz.sdk.auth.aksk.client.demo.test.SimpleAkskClientDemoTestApplication;
import io.github.surezzzzzz.sdk.auth.aksk.client.demo.test.client.DemoFeignClient;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Feign 客户端独立调用测试
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleAkskClientDemoTestApplication.class)
class FeignOnlyCallTest {

    @Autowired
    private TokenManager tokenManager;

    @Autowired
    private DemoFeignClient.AkskDemoFeignClient akskDemoFeignClient;

    @Value("${io.github.surezzzzzz.sdk.auth.aksk.client.server-url}")
    private String serverBaseUrl;

    @BeforeEach
    void setUp() {
        tokenManager.clearToken();
    }

    @Test
    void testFeignClientGetToken() {
        log.info("========== 测试：Feign 客户端获取 Token ==========");

        String token = tokenManager.getToken();
        assertNotNull(token, "Token 不应为 null");
        assertTrue(token.startsWith("eyJ"), "Token 应该是 JWT");
        log.info("✓ Token 获取成功，长度: {}", token.length());
    }

    @Test
    void testFeignClientCallWithToken() {
        log.info("========== 测试：Feign 客户端携带 Token 调用接口 ==========");

        String response = akskDemoFeignClient.getTokenStatistics();
        assertNotNull(response, "响应不应为 null");
        log.info("✓ Feign 调用成功，响应: {}", response);
    }

    @Test
    void testFeignClientReuseToken() {
        log.info("========== 测试：Feign 客户端多次调用复用 Token ==========");

        akskDemoFeignClient.getTokenStatistics();
        String token1 = tokenManager.getToken();

        akskDemoFeignClient.getTokenStatistics();
        String token2 = tokenManager.getToken();

        assertEquals(token1, token2, "多次调用应复用同一个 Token");
        log.info("✓ Token 复用成功");
    }
}
