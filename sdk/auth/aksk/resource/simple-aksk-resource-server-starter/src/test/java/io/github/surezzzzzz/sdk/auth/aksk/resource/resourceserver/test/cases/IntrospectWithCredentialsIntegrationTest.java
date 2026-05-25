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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.util.StringUtils;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Introspect 带认证模式集成测试
 *
 * <p>验证 verificationMode=INTROSPECT 且携带 clientId/clientSecret 时的完整链路。
 *
 * <p>前置条件：
 * <ol>
 *   <li>aksk-server 启动（端口 8080）</li>
 *   <li>application-local.yml 中配置：
 *     <ul>
 *       <li>io.github.surezzzzzz.sdk.auth.aksk.resource.server.introspect.client-id</li>
 *       <li>io.github.surezzzzzz.sdk.auth.aksk.resource.server.introspect.client-secret</li>
 *     </ul>
 *   </li>
 * </ol>
 *
 * <p>未配置 introspect.client-id 时测试自动跳过。
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
public class IntrospectWithCredentialsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OAuth2TokenHelper tokenHelper;

    @Autowired
    private SimpleAkskResourceServerProperties properties;

    private String validToken;

    @BeforeEach
    public void setUp() {
        log.info("========== Introspect 带认证模式测试准备 ==========");

        // 未配置 introspect clientId 则跳过
        assumeTrue(StringUtils.hasText(properties.getIntrospect().getClientId()),
                "未配置 introspect.client-id，跳过带认证 introspect 测试");

        validToken = tokenHelper.getToken();
        assertNotNull(validToken, "Token 不应为 null");
        log.info("Token 获取成功，带认证 introspect 测试开始");
    }

    @Test
    void testValidTokenAccessWithCredentialsIntrospect() throws Exception {
        log.info("========== 测试：带认证 Introspect - 有效 Token 访问受保护接口 ==========");

        MvcResult result = mockMvc.perform(get("/test/basic")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        log.info("响应: {}", responseBody);
        assertNotNull(responseBody);
        log.info("✓ 带认证 Introspect 验证通过");
    }

    @Test
    void testInvalidTokenReturns401WithCredentialsIntrospect() throws Exception {
        log.info("========== 测试：带认证 Introspect - 无效 Token 返回 401 ==========");

        mockMvc.perform(get("/test/basic")
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());

        log.info("✓ 无效 Token 正确返回 401");
    }

    @Test
    void testNoTokenReturns401WithCredentialsIntrospect() throws Exception {
        log.info("========== 测试：带认证 Introspect - 无 Token 返回 401 ==========");

        mockMvc.perform(get("/test/basic"))
                .andExpect(status().isUnauthorized());

        log.info("✓ 无 Token 正确返回 401");
    }
}
