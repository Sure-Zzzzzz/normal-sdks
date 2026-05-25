package io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.configuration.SimpleAkskResourceServerProperties;
import io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.test.SimpleAkskResourceServerTestApplication;
import io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.test.helper.OAuth2TokenHelper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Introspect 无认证模式集成测试
 *
 * <p>验证 verificationMode=INTROSPECT 且 server 端 require-authentication=false 时的完整链路。
 *
 * <p>前置条件：
 * <ol>
 *   <li>aksk-server 启动（端口 8080）</li>
 *   <li>server 端配置 introspect.require-authentication=false</li>
 * </ol>
 *
 * <p>若 server 端未开启匿名 introspect，测试自动跳过。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(
        classes = SimpleAkskResourceServerTestApplication.class,
        properties = {
                "io.github.surezzzzzz.sdk.auth.aksk.resource.server.enabled=true"
        }
)
@AutoConfigureMockMvc
public class IntrospectIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OAuth2TokenHelper tokenHelper;

    @Autowired
    private SimpleAkskResourceServerProperties properties;

    private final RestTemplate restTemplate = new RestTemplate();

    private String validToken;

    @BeforeEach
    public void setUp() {
        log.info("========== Introspect 无认证模式测试准备 ==========");

        boolean anonymousIntrospectEnabled = checkAnonymousIntrospectEnabled();
        assumeTrue(anonymousIntrospectEnabled,
                "Server 端 introspect.require-authentication=true，跳过匿名 introspect 测试");

        validToken = tokenHelper.getToken();
        assertNotNull(validToken, "Token 不应为 null");
        log.info("Token 获取成功，匿名 introspect 测试开始");
    }

    @Test
    void testValidTokenAccessWithAnonymousIntrospect() throws Exception {
        log.info("========== 测试：匿名 Introspect - 有效 Token 访问受保护接口 ==========");

        MvcResult result = mockMvc.perform(get("/test/basic")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        log.info("响应: {}", responseBody);
        assertNotNull(responseBody);
        log.info("✓ 匿名 Introspect 验证通过");
    }

    @Test
    void testInvalidTokenReturns401WithAnonymousIntrospect() throws Exception {
        log.info("========== 测试：匿名 Introspect - 无效 Token 返回 401 ==========");

        mockMvc.perform(get("/test/basic")
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());

        log.info("✓ 无效 Token 正确返回 401");
    }

    @Test
    void testNoTokenReturns401WithAnonymousIntrospect() throws Exception {
        log.info("========== 测试：匿名 Introspect - 无 Token 返回 401 ==========");

        mockMvc.perform(get("/test/basic"))
                .andExpect(status().isUnauthorized());

        log.info("✓ 无 Token 正确返回 401");
    }

    private boolean checkAnonymousIntrospectEnabled() {
        try {
            String endpoint = properties.getIntrospect().getEndpoint();
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("token", "probe");

            ResponseEntity<String> response = restTemplate.postForEntity(
                    endpoint,
                    new org.springframework.http.HttpEntity<>(body, headers),
                    String.class
            );
            boolean enabled = response.getStatusCode() == HttpStatus.OK;
            log.info("匿名 introspect 检测结果: {}", enabled ? "已开启" : "未开启");
            return enabled;
        } catch (Exception e) {
            log.info("匿名 introspect 检测失败（server 可能未启动或需要认证）: {}", e.getMessage());
            return false;
        }
    }
}
