package io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.test.cases;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.test.SimpleAkskResourceServerTestApplication;
import io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.test.helper.OAuth2TokenHelper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 注解集成测试
 *
 * <p>测试权限注解功能：@RequireContext, @RequireField, @RequireFieldValue, @RequireExpression
 * <p>使用从 aksk-server 获取的真实 Token 进行测试
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@Slf4j
@SpringBootTest(classes = SimpleAkskResourceServerTestApplication.class)
@AutoConfigureMockMvc
public class AnnotationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OAuth2TokenHelper tokenHelper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String validToken;

    @BeforeEach
    public void setUp() {
        log.info("======================================");
        log.info("Setting up test - getting token from aksk-server");
        log.info("======================================");
        validToken = tokenHelper.getToken();
        assertNotNull(validToken, "Token should not be null");
        assertTrue(validToken.startsWith("eyJ"), "Token should be JWT format");
        log.info("Token obtained successfully");
    }

    // ==================== @RequireContext 测试 ====================

    /**
     * 测试 @RequireContext - 有上下文时通过
     */
    @Test
    @DisplayName("测试 @RequireContext - 有上下文时通过")
    public void testRequireContextWithContext() throws Exception {
        log.info("======================================");
        log.info("测试 @RequireContext - 有上下文时通过");
        log.info("======================================");

        MvcResult result = mockMvc.perform(get("/test/require-context")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        JsonNode jsonResponse = objectMapper.readTree(responseBody);

        assertEquals("Context exists", jsonResponse.get("message").asText());
        log.info("@RequireContext passed with valid token");
    }

    /**
     * 测试 @RequireContext - 无上下文时失败
     */
    @Test
    @DisplayName("测试 @RequireContext - 无上下文时失败")
    public void testRequireContextWithoutContext() throws Exception {
        log.info("======================================");
        log.info("测试 @RequireContext - 无上下文时失败");
        log.info("======================================");

        mockMvc.perform(get("/test/require-context"))
                .andExpect(status().isUnauthorized()); // 无 token 会先被 Spring Security 拦截返回 401

        log.info("@RequireContext correctly rejected request without token");
    }

    // ==================== @RequireField 测试 ====================

    /**
     * 测试 @RequireField - 检查 userId 字段
     * <p>
     * client_credentials 令牌不包含 userId，@RequireField 应拒绝访问返回 403
     */
    @Test
    @DisplayName("测试 @RequireField - 检查 userId 字段")
    public void testRequireFieldWithUserId() throws Exception {
        log.info("======================================");
        log.info("测试 @RequireField - 检查 userId 字段");
        log.info("======================================");

        MvcResult result = mockMvc.perform(get("/test/require-field")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isForbidden())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        JsonNode jsonResponse = objectMapper.readTree(responseBody);

        assertEquals("Forbidden", jsonResponse.get("error").asText());
        assertTrue(jsonResponse.get("message").asText().contains("userId"));
        log.info("@RequireField correctly rejected - userId field missing in token");
    }

    // ==================== @RequireFieldValue 测试 ====================

    /**
     * 测试 @RequireFieldValue - 检查 clientType 字段值
     * <p>
     * client_credentials 令牌的 clientType 不等于 "service"，@RequireFieldValue 应拒绝访问返回 403
     */
    @Test
    @DisplayName("测试 @RequireFieldValue - 检查 clientType 字段值")
    public void testRequireFieldValueWithClientType() throws Exception {
        log.info("======================================");
        log.info("测试 @RequireFieldValue - 检查 clientType 字段值");
        log.info("======================================");

        MvcResult result = mockMvc.perform(get("/test/require-field-value")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isForbidden())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        JsonNode jsonResponse = objectMapper.readTree(responseBody);

        assertEquals("Forbidden", jsonResponse.get("error").asText());
        log.info("@RequireFieldValue correctly rejected - clientType mismatch or missing");
    }

    // ==================== @RequireExpression 测试 ====================

    /**
     * 测试 @RequireExpression - SpEL 表达式校验
     * <p>
     * 表达式：#context['userId'] != null && #context['userId'].length() > 0
     * client_credentials 令牌不包含 userId，表达式求值为 false，应返回 403
     */
    @Test
    @DisplayName("测试 @RequireExpression - SpEL 表达式校验")
    public void testRequireExpressionWithUserId() throws Exception {
        log.info("======================================");
        log.info("测试 @RequireExpression - SpEL 表达式校验");
        log.info("======================================");

        MvcResult result = mockMvc.perform(get("/test/require-expression")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isForbidden())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        JsonNode jsonResponse = objectMapper.readTree(responseBody);

        assertEquals("Forbidden", jsonResponse.get("error").asText());
        log.info("@RequireExpression correctly rejected - expression evaluated to false");
    }

    // ==================== 实际业务场景测试 ====================

    /**
     * 测试管理员接口 - 检查 scope 是否包含 admin
     * <p>
     * 表达式：#context['scope'] != null && #context['scope'].contains('admin')
     * client_credentials 令牌的 scope 不包含 admin，应返回 403
     */
    @Test
    @DisplayName("测试管理员接口 - 检查 scope 是否包含 admin")
    public void testAdminDashboardWithScope() throws Exception {
        log.info("======================================");
        log.info("测试管理员接口 - 检查 scope 是否包含 admin");
        log.info("======================================");

        MvcResult result = mockMvc.perform(get("/test/admin/dashboard")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isForbidden())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        JsonNode jsonResponse = objectMapper.readTree(responseBody);

        assertEquals("Forbidden", jsonResponse.get("error").asText());
        log.info("Admin dashboard access denied - scope does not contain 'admin'");
    }

    /**
     * 测试多租户接口 - 检查 tenantId 是否存在
     * <p>
     * 表达式：#context['tenantId'] != null && #context['tenantId'].length() > 0
     * client_credentials 令牌不包含 tenantId，应返回 403
     */
    @Test
    @DisplayName("测试多租户接口 - 检查 tenantId 是否存在")
    public void testTenantDataWithTenantId() throws Exception {
        log.info("======================================");
        log.info("测试多租户接口 - 检查 tenantId 是否存在");
        log.info("======================================");

        MvcResult result = mockMvc.perform(get("/test/tenant/data")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isForbidden())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        JsonNode jsonResponse = objectMapper.readTree(responseBody);

        assertEquals("Forbidden", jsonResponse.get("error").asText());
        log.info("Tenant data access denied - tenantId missing");
    }

    // ==================== 无效 Token 测试 ====================

    /**
     * 测试使用无效 Token 访问需要注解的接口
     */
    @Test
    @DisplayName("测试使用无效 Token 访问需要注解的接口")
    public void testAnnotationEndpointWithInvalidToken() throws Exception {
        log.info("======================================");
        log.info("测试使用无效 Token 访问需要注解的接口");
        log.info("======================================");

        mockMvc.perform(get("/test/require-context")
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());

        log.info("Correctly returned 401 Unauthorized with invalid token");
    }

    /**
     * 测试不带 Token 访问需要注解的接口
     */
    @Test
    @DisplayName("测试不带 Token 访问需要注解的接口")
    public void testAnnotationEndpointWithoutToken() throws Exception {
        log.info("======================================");
        log.info("测试不带 Token 访问需要注解的接口");
        log.info("======================================");

        mockMvc.perform(get("/test/require-context"))
                .andExpect(status().isUnauthorized());

        log.info("Correctly returned 401 Unauthorized without token");
    }
}
