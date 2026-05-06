package io.github.surezzzzzz.sdk.auth.aksk.client.demo.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.client.core.manager.TokenManager;
import io.github.surezzzzzz.sdk.auth.aksk.client.demo.test.SimpleAkskClientDemoTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RestTemplate 客户端独立调用测试
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleAkskClientDemoTestApplication.class)
class RestTemplateOnlyCallTest {

    @Autowired
    @Qualifier("akskClientRestTemplate")
    private RestTemplate akskClientRestTemplate;

    @Autowired
    private TokenManager tokenManager;

    @Value("${io.github.surezzzzzz.sdk.auth.aksk.client.server-url}")
    private String serverBaseUrl;

    @BeforeEach
    void setUp() {
        tokenManager.clearToken();
    }

    @Test
    void testRestTemplateGetToken() {
        log.info("========== 测试：RestTemplate 客户端获取 Token ==========");

        String token = tokenManager.getToken();
        assertNotNull(token, "Token 不应为 null");
        assertTrue(token.startsWith("eyJ"), "Token 应该是 JWT");
        log.info("✓ Token 获取成功，长度: {}", token.length());
    }

    @Test
    void testRestTemplateCallWithToken() {
        log.info("========== 测试：RestTemplate 客户端携带 Token 调用接口 ==========");

        ResponseEntity<String> response = akskClientRestTemplate.getForEntity(
                serverBaseUrl + "/api/token/statistics", String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode(), "响应应该是 200");
        assertNotNull(response.getBody(), "响应体不应为 null");
        log.info("✓ RestTemplate 调用成功");
    }

    @Test
    void testRestTemplateAutoRefreshToken() {
        log.info("========== 测试：RestTemplate 客户端 Token 过期后自动刷新 ==========");

        // 第一次调用，获取并缓存 token
        akskClientRestTemplate.getForEntity(serverBaseUrl + "/api/token/statistics", String.class);
        String token1 = tokenManager.getToken();
        log.info("第一次 token: {}...", token1.substring(0, 20));

        // 模拟过期，清除缓存
        tokenManager.clearToken();

        // 第二次调用，应自动重新获取 token
        ResponseEntity<String> response = akskClientRestTemplate.getForEntity(
                serverBaseUrl + "/api/token/statistics", String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        String token2 = tokenManager.getToken();
        log.info("刷新后 token: {}...", token2.substring(0, 20));

        assertNotNull(token2, "刷新后 Token 不应为 null");
        log.info("✓ Token 自动刷新成功");
    }

    @Test
    void testRestTemplateReuseToken() {
        log.info("========== 测试：RestTemplate 客户端多次调用复用 Token ==========");

        akskClientRestTemplate.getForEntity(serverBaseUrl + "/api/token/statistics", String.class);
        String token1 = tokenManager.getToken();

        akskClientRestTemplate.getForEntity(serverBaseUrl + "/api/token/statistics", String.class);
        String token2 = tokenManager.getToken();

        assertEquals(token1, token2, "多次调用应复用同一个 Token");
        log.info("✓ Token 复用成功");
    }
}
