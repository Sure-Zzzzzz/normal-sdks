package io.github.surezzzzzz.sdk.auth.aksk.server.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.server.controller.response.ClientInfoResponse;
import io.github.surezzzzzz.sdk.auth.aksk.server.repository.OAuth2RegisteredClientEntityRepository;
import io.github.surezzzzzz.sdk.auth.aksk.server.service.ClientManagementService;
import io.github.surezzzzzz.sdk.auth.aksk.server.test.SimpleAkskServerTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.TestPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Admin禁用测试
 * <p>
 * 测试在admin.enabled=false的情况下，Admin相关功能是否正确禁用
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(
        classes = SimpleAkskServerTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@TestPropertySource(properties = {
        "io.github.surezzzzzz.sdk.auth.aksk.server.admin.enabled=false"
})
class AdminDisabledTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ClientManagementService clientManagementService;

    @Autowired
    private OAuth2RegisteredClientEntityRepository clientRepository;

    /**
     * 每个测试方法执行后清理数据
     */
    @AfterEach
    void cleanupData() {
        log.info("清理测试数据...");
        clientRepository.deleteAll();
        log.info("测试数据清理完成");
    }

    @Test
    void testAdminPageNotAccessible() {
        log.info("测试admin.enabled=false时，Admin页面不可访问");

        // 尝试访问Admin页面
        String adminUrl = "http://localhost:" + port + "/admin";

        ResponseEntity<String> response = restTemplate.getForEntity(adminUrl, String.class);

        log.info("Admin页面响应 - Status: {}", response.getStatusCode());

        // 验证返回404、401或403（取决于Spring Security配置的@Order）
        // 当AdminController被@ConditionalOnProperty禁用后，Security配置仍在，会返回403
        assertTrue(
                response.getStatusCode() == HttpStatus.NOT_FOUND ||
                        response.getStatusCode() == HttpStatus.UNAUTHORIZED ||
                        response.getStatusCode() == HttpStatus.FORBIDDEN,
                "Admin页面应该不可访问，期望404/401/403，实际: " + response.getStatusCode()
        );

        log.info("Admin页面不可访问测试通过");
    }

    @Test
    void testLoginPageNotAccessible() {
        log.info("测试admin.enabled=false时，Login页面不可访问");

        // 尝试访问Login页面
        String loginUrl = "http://localhost:" + port + "/admin/login";

        ResponseEntity<String> response = restTemplate.getForEntity(loginUrl, String.class);

        log.info("Login页面响应 - Status: {}", response.getStatusCode());

        // 验证返回404、401或403（取决于Spring Security配置的@Order）
        // 当AdminController被@ConditionalOnProperty禁用后，Security配置仍在，会返回403
        assertTrue(
                response.getStatusCode() == HttpStatus.NOT_FOUND ||
                        response.getStatusCode() == HttpStatus.UNAUTHORIZED ||
                        response.getStatusCode() == HttpStatus.FORBIDDEN,
                "Login页面应该不可访问，期望404/401/403，实际: " + response.getStatusCode()
        );

        log.info("Login页面不可访问测试通过");
    }

    @Test
    void testOAuth2FlowStillWorks() {
        log.info("测试admin.enabled=false时，OAuth2流程仍然正常工作");

        // Step 1: 创建平台级AKSK
        ClientInfoResponse clientInfo = clientManagementService.createPlatformClient("Admin Disabled Test Client");

        log.info("创建AKSK成功 - ClientId: {}, ClientSecret: {}",
                clientInfo.getClientId(), clientInfo.getClientSecret());

        assertNotNull(clientInfo.getClientId());
        assertNotNull(clientInfo.getClientSecret());
        assertTrue(clientInfo.getClientId().startsWith("AKP"));
        assertTrue(clientInfo.getClientSecret().startsWith("SK"));

        // Step 2: 使用AKSK换取Token
        String tokenUrl = "http://localhost:" + port + "/oauth2/token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth(clientInfo.getClientId(), clientInfo.getClientSecret());

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "client_credentials");
        body.add("scope", "read write");

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

        log.info("请求Token - URL: {}, ClientId: {}", tokenUrl, clientInfo.getClientId());

        ResponseEntity<Map> response = restTemplate.postForEntity(tokenUrl, request, Map.class);

        log.info("Token响应 - Status: {}, Body: {}", response.getStatusCode(), response.getBody());

        // Step 3: 验证Token响应
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());

        Map<String, Object> tokenResponse = response.getBody();
        assertNotNull(tokenResponse.get("access_token"));
        assertNotNull(tokenResponse.get("token_type"));
        assertNotNull(tokenResponse.get("expires_in"));

        String accessToken = (String) tokenResponse.get("access_token");
        log.info("获取到Access Token: {}", accessToken.substring(0, Math.min(50, accessToken.length())) + "...");

        log.info("admin.enabled=false时OAuth2流程测试通过");
    }
}
