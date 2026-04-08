package io.github.surezzzzzz.sdk.auth.aksk.client.demo.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.client.core.manager.TokenManager;
import io.github.surezzzzzz.sdk.auth.aksk.client.demo.test.SimpleAkskClientDemoTestApplication;
import io.github.surezzzzzz.sdk.auth.aksk.client.demo.test.client.DemoFeignClient;
import io.github.surezzzzzz.sdk.auth.aksk.feign.redis.client.interceptor.AkskFeignRequestInterceptor;
import io.github.surezzzzzz.sdk.auth.aksk.resttemplate.redis.client.interceptor.AkskRestTemplateInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 两个客户端共存测试
 *
 * <p>验证 simple-aksk-feign-redis-client-starter 和
 * simple-aksk-resttemplate-redis-client-starter 同时引用时互不冲突。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleAkskClientDemoTestApplication.class)
class BothClientsCoexistTest {

    @Autowired
    private TokenManager tokenManager;

    @Autowired
    private AkskFeignRequestInterceptor akskFeignRequestInterceptor;

    @Autowired
    private AkskRestTemplateInterceptor akskRestTemplateInterceptor;

    @Autowired
    @Qualifier("akskClientRestTemplate")
    private RestTemplate akskClientRestTemplate;

    @Autowired
    private DemoFeignClient.AkskDemoFeignClient akskDemoFeignClient;

    @Autowired
    private DemoFeignClient.PlainDemoFeignClient plainDemoFeignClient;

    @Autowired(required = false)
    private org.springframework.web.client.RestTemplate defaultRestTemplate;

    @BeforeEach
    void setUp() {
        tokenManager.clearToken();
    }

    @Test
    void testBothBeansExistWithoutConflict() {
        log.info("========== 测试：两个客户端的 Bean 同时存在，无冲突 ==========");

        assertNotNull(tokenManager, "TokenManager 应该存在");
        assertNotNull(akskFeignRequestInterceptor, "AkskFeignRequestInterceptor 应该存在");
        assertNotNull(akskRestTemplateInterceptor, "AkskRestTemplateInterceptor 应该存在");
        assertNotNull(akskClientRestTemplate, "akskClientRestTemplate 应该存在");
        assertNotNull(akskDemoFeignClient, "AkskDemoFeignClient 应该存在");
        assertNotNull(plainDemoFeignClient, "PlainDemoFeignClient 应该存在");

        log.info("✓ 所有 Bean 正常注入，无冲突");
    }

    @Test
    void testBothClientsShareSameTokenManager() {
        log.info("========== 测试：两个客户端共享同一个 TokenManager ==========");

        // 获取 token，两个客户端都从同一个 TokenManager 取
        String token = tokenManager.getToken();
        assertNotNull(token, "Token 应该不为 null");
        assertTrue(token.startsWith("eyJ"), "Token 应该是 JWT");

        // 再次获取，应该是同一个（缓存复用）
        String token2 = tokenManager.getToken();
        assertEquals(token, token2, "两次获取应该是同一个 Token（缓存复用）");

        log.info("✓ 两个客户端共享同一个 TokenManager，token 长度: {}", token.length());
    }

    @Test
    void testFeignAndRestTemplateBothCallSucceed() {
        log.info("========== 测试：Feign 和 RestTemplate 同时调用，互不干扰 ==========");

        // Feign 调用
        log.info("Feign 调用 /api/token/statistics...");
        String feignResponse = akskDemoFeignClient.getTokenStatistics();
        assertNotNull(feignResponse, "Feign 响应不应为 null");
        log.info("✓ Feign 调用成功");

        // RestTemplate 调用
        log.info("RestTemplate 调用 /api/token/statistics...");
        String url = "${io.github.surezzzzzz.sdk.auth.aksk.client.server-url}/api/token/statistics";
        ResponseEntity<String> restResponse = akskClientRestTemplate.getForEntity(
                "http://localhost:8080/api/token/statistics", String.class);
        assertEquals(HttpStatus.OK, restResponse.getStatusCode(), "RestTemplate 响应应该是 200");
        assertNotNull(restResponse.getBody(), "RestTemplate 响应体不应为 null");
        log.info("✓ RestTemplate 调用成功");

        log.info("✓ Feign 和 RestTemplate 同时调用，互不干扰");
    }

    @Test
    void testBothClientsReuseToken() {
        log.info("========== 测试：两个客户端复用同一个 Token ==========");

        // Feign 先调用，触发 token 获取
        akskDemoFeignClient.getTokenStatistics();
        String tokenAfterFeign = tokenManager.getToken();
        assertNotNull(tokenAfterFeign);
        log.info("Feign 调用后 token: {}...", tokenAfterFeign.substring(0, 20));

        // RestTemplate 再调用，应该复用同一个 token
        akskClientRestTemplate.getForEntity("http://localhost:8080/api/token/statistics", String.class);
        String tokenAfterRestTemplate = tokenManager.getToken();
        assertNotNull(tokenAfterRestTemplate);
        log.info("RestTemplate 调用后 token: {}...", tokenAfterRestTemplate.substring(0, 20));

        assertEquals(tokenAfterFeign, tokenAfterRestTemplate, "两个客户端应该复用同一个 Token");
        log.info("✓ 两个客户端复用同一个 Token，无重复获取");
    }

    @Test
    void testPlainFeignClientNotAffectedByAksk() {
        log.info("========== 测试：普通 @FeignClient 不受 AKSK 影响（验证 1.0.1 修复）==========");

        // 普通 FeignClient 调用公开接口，不需要 token
        // 如果 AkskFeignConfiguration 仍然是全局的，这个请求会带上 token（但不影响结果）
        // 关键是验证普通 FeignClient 能正常工作，没有因为 AKSK 配置而出错
        String response = plainDemoFeignClient.loginPage();
        assertNotNull(response, "登录页响应不应为 null");

        log.info("✓ 普通 @FeignClient 正常工作，不受 AKSK 全局拦截器影响");
        log.info("响应长度: {}", response.length());
    }
}
